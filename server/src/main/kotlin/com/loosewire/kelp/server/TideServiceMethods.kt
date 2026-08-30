package com.loosewire.kelp.server

import android.content.ComponentName
import android.content.Context
import com.loosewire.kelp.protocol.AlbumRequest
import com.loosewire.kelp.protocol.ArtistRequest
import com.loosewire.kelp.protocol.ArtistReleasesRequest
import com.loosewire.kelp.protocol.ArtistTracksRequest
import com.loosewire.kelp.protocol.CollectionRequest
import com.loosewire.kelp.protocol.PlaylistRequest
import com.loosewire.kelp.protocol.PlayerCommandRequest
import com.loosewire.kelp.protocol.StartPlaybackRequest
import com.loosewire.kelp.protocol.SearchRequest
import com.loosewire.kelp.protocol.SearchPageRequest
import com.loosewire.kelp.protocol.ServerActivity
import com.loosewire.kelp.protocol.TideError
import com.loosewire.kelp.protocol.TideErrorCategory
import com.loosewire.kelp.protocol.TideRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException

object TideServiceMethods {
    private val logger = Logger.getLogger(TideServiceMethods::class.java.name)
    private var loginActivityComponent = "com.loosewire.kelp/.server.LoginActivity"
    private lateinit var applicationContext: Context
    private val catalogLock = Any()
    @Volatile
    private var catalog: Catalog? = null
    @Volatile
    private var playerController: TidePlayerController? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        RecentTracksStore.initialize(applicationContext)
        loginActivityComponent = ComponentName(
            context.applicationContext,
            LoginActivity::class.java,
        ).flattenToString()
    }

    fun initializeCatalog(catalog: Catalog?) {
        synchronized(catalogLock) {
            this.catalog = catalog
        }
    }

    fun dispatch(methodId: String, payload: String?): LightResult<String> = try {
        when (methodId) {
            TideRemoteMethod.GetAuthSnapshot.id -> LightResult.Success(
                TideRemoteMethod.GetAuthSnapshot.encodeResponse(
                    TideRuntime.currentAuthSnapshot(),
                ),
            )

            TideRemoteMethod.GetLoginActivity.id -> LightResult.Success(
                TideRemoteMethod.GetLoginActivity.encodeResponse(
                    ServerActivity(loginActivityComponent),
                ),
            )

            TideRemoteMethod.Logout.id -> {
                runBlocking { TideRuntime.logout() }
                synchronized(catalogLock) {
                    playerController?.release()
                    playerController = null
                    catalog = null
                }
                LightResult.Success(
                    TideRemoteMethod.Logout.encodeResponse(TideRuntime.currentAuthSnapshot()),
                )
            }

            TideRemoteMethod.GetCollection.id -> {
                val request = TideRemoteMethod.GetCollection.decodeRequest(payload ?: "{}")
                handleCatalog(
                    operation = { it.collection(request.cursor) },
                    encode = TideRemoteMethod.GetCollection::encodeResponse,
                )
            }

            TideRemoteMethod.GetArtists.id -> {
                val request = TideRemoteMethod.GetArtists.decodeRequest(payload ?: "{}")
                handleCatalog(
                    operation = { it.artists(request.cursor) },
                    encode = TideRemoteMethod.GetArtists::encodeResponse,
                )
            }

            TideRemoteMethod.GetTracks.id -> {
                val request = TideRemoteMethod.GetTracks.decodeRequest(payload ?: "{}")
                handleCatalog(
                    operation = { it.tracks(request.cursor) },
                    encode = TideRemoteMethod.GetTracks::encodeResponse,
                )
            }

            TideRemoteMethod.GetHome.id -> handleCatalog(
                operation = Catalog::home,
                encode = TideRemoteMethod.GetHome::encodeResponse,
            )

            TideRemoteMethod.Search.id -> {
                val request = TideRemoteMethod.Search.decodeRequest(payload ?: "{}")
                if (request.query.isBlank()) {
                    failure(
                        TideErrorCategory.Protocol,
                        "Enter something to search for.",
                        LightResult.ErrorCode.InvalidParameters,
                    )
                } else {
                    handleSearch(request)
                }
            }

            TideRemoteMethod.SearchPage.id -> {
                val request = TideRemoteMethod.SearchPage.decodeRequest(payload ?: "{}")
                if (request.query.isBlank()) {
                    failure(
                        TideErrorCategory.Protocol,
                        "Enter something to search for.",
                        LightResult.ErrorCode.InvalidParameters,
                    )
                } else {
                    handleSearchPage(request)
                }
            }

            TideRemoteMethod.GetArtistDetail.id -> {
                val request = TideRemoteMethod.GetArtistDetail.decodeRequest(payload ?: "{}")
                handleArtistDetail(request)
            }

            TideRemoteMethod.GetArtistReleases.id -> {
                val request = TideRemoteMethod.GetArtistReleases.decodeRequest(payload ?: "{}")
                handleArtistReleases(request)
            }

            TideRemoteMethod.GetArtistTracks.id -> {
                val request = TideRemoteMethod.GetArtistTracks.decodeRequest(payload ?: "{}")
                handleArtistTracks(request)
            }

            TideRemoteMethod.GetAlbumDetail.id -> {
                val request = TideRemoteMethod.GetAlbumDetail.decodeRequest(payload ?: "{}")
                handleAlbumDetail(request)
            }

            TideRemoteMethod.GetPlaylistDetail.id -> {
                val request = TideRemoteMethod.GetPlaylistDetail.decodeRequest(payload ?: "{}")
                handlePlaylistDetail(request)
            }

            TideRemoteMethod.GetPlayback.id -> withPlayer { controller ->
                LightResult.Success(
                    TideRemoteMethod.GetPlayback.encodeResponse(controller.snapshot()),
                )
            }

            TideRemoteMethod.StartPlayback.id -> {
                val request = TideRemoteMethod.StartPlayback.decodeRequest(payload ?: "{}")
                handleStartPlayback(request)
            }

            TideRemoteMethod.ControlPlayback.id -> {
                val request = TideRemoteMethod.ControlPlayback.decodeRequest(payload ?: "{}")
                handlePlayerCommand(request)
            }

            else -> failure(
                TideErrorCategory.Protocol,
                "Kelp does not support this server request.",
                LightResult.ErrorCode.InvalidParameters,
            )
        }
    } catch (error: SerializationException) {
        logger.warning(
            "Invalid Kelp RPC payload for method $methodId: ${error.message ?: error.javaClass.simpleName}",
        )
        failure(
            TideErrorCategory.Protocol,
            "Kelp received an invalid request.",
            LightResult.ErrorCode.InvalidParameters,
        )
    } catch (error: Exception) {
        logger.severe("Kelp RPC failed for method $methodId: ${error.javaClass.simpleName}")
        failure(TideErrorCategory.Unavailable, "The Kelp server could not complete the request.")
    }

    private fun handleSearch(request: SearchRequest): LightResult<String> = handleCatalog(
        operation = { it.search(request.query.trim()) },
        encode = TideRemoteMethod.Search::encodeResponse,
    )

    private fun handleSearchPage(request: SearchPageRequest): LightResult<String> = handleCatalog(
        operation = { it.searchPage(request.query.trim(), request.section, request.cursor) },
        encode = TideRemoteMethod.SearchPage::encodeResponse,
    )

    private fun handleArtistDetail(request: ArtistRequest): LightResult<String> = handleCatalog(
        operation = { it.artistDetail(request.artist) },
        encode = TideRemoteMethod.GetArtistDetail::encodeResponse,
    )

    private fun handleArtistReleases(request: ArtistReleasesRequest): LightResult<String> = handleCatalog(
        operation = { it.artistReleases(request.artist, request.section, request.cursor) },
        encode = TideRemoteMethod.GetArtistReleases::encodeResponse,
    )

    private fun handleArtistTracks(request: ArtistTracksRequest): LightResult<String> = handleCatalog(
        operation = { it.artistTracks(request.artist, request.cursor) },
        encode = TideRemoteMethod.GetArtistTracks::encodeResponse,
    )

    private fun handleAlbumDetail(request: AlbumRequest): LightResult<String> = handleCatalog(
        operation = { it.albumDetail(request.album) },
        encode = TideRemoteMethod.GetAlbumDetail::encodeResponse,
    )

    private fun handlePlaylistDetail(request: PlaylistRequest): LightResult<String> = handleCatalog(
        operation = { it.playlistDetail(request.playlist) },
        encode = TideRemoteMethod.GetPlaylistDetail::encodeResponse,
    )

    private fun handleStartPlayback(request: StartPlaybackRequest): LightResult<String> =
        withPlayer { controller ->
            runCatching { controller.start(request) }.fold(
                onSuccess = { LightResult.Success(TideRemoteMethod.StartPlayback.encodeResponse(it)) },
                onFailure = {
                    failure(
                        TideErrorCategory.Protocol,
                        "Choose a valid song to start playback.",
                        LightResult.ErrorCode.InvalidParameters,
                    )
                },
            )
        }

    private fun handlePlayerCommand(request: PlayerCommandRequest): LightResult<String> =
        withPlayer { controller ->
            val snapshot = runBlocking {
                withTimeout(collectionTimeoutMillis) {
                    controller.control(request.command, request.positionMs)
                }
            }
            LightResult.Success(TideRemoteMethod.ControlPlayback.encodeResponse(snapshot))
        }

    private fun withPlayer(
        block: (TidePlayerController) -> LightResult<String>,
    ): LightResult<String> {
        val controller = currentPlayer() ?: return failure(
            TideErrorCategory.Unavailable,
            "The TIDAL player is not available yet.",
        )
        return try {
            block(controller)
        } catch (_: TimeoutCancellationException) {
            failure(TideErrorCategory.Timeout, "The player took too long to respond. Please retry.")
        } catch (error: TidalCatalogException) {
            failure(error.category, error.safeMessage)
        } catch (error: Exception) {
            logger.warning("TIDAL player request failed: ${error.javaClass.simpleName}")
            failure(TideErrorCategory.Unavailable, "The TIDAL player could not complete that action.")
        }
    }

    private fun <T> handleCatalog(
        operation: suspend (Catalog) -> T,
        encode: (T) -> String,
    ): LightResult<String> {
        val cat = currentCatalog() ?: return failure(
            TideErrorCategory.Unavailable,
            "The TIDAL catalog is not available yet.",
        )
        return try {
            val result = runBlocking {
                withTimeout(collectionTimeoutMillis) {
                    operation(cat)
                }
            }
            LightResult.Success(encode(result))
        } catch (_: TimeoutCancellationException) {
            logger.log(Level.WARNING, "TIDAL catalog request timed out")
            failure(TideErrorCategory.Timeout, "Loading from TIDAL timed out. Please retry.")
        } catch (error: TidalCatalogException) {
            logger.warning(
                "TIDAL catalog request failed: category=${error.category}, status=${error.statusCode ?: "none"}",
            )
            val lightCode = if (error.category == TideErrorCategory.Authentication) {
                LightResult.ErrorCode.NoPermission
            } else {
                LightResult.ErrorCode.Unknown
            }
            failure(error.category, error.safeMessage, lightCode)
        } catch (error: SerializationException) {
            logger.warning(
                "TIDAL catalog response could not be decoded: ${error.message ?: error.javaClass.simpleName}",
            )
            failure(
                TideErrorCategory.Protocol,
                "TIDAL returned an incompatible catalog response. Please retry.",
            )
        } catch (_: IOException) {
            logger.log(Level.WARNING, "TIDAL catalog network request failed")
            failure(TideErrorCategory.Network, "Could not reach TIDAL. Check your connection and retry.")
        }
    }

    private fun currentCatalog(): Catalog? {
        catalog?.let { return it }
        val provider = TideRuntime.tidalAuth()?.credentialsProvider ?: return null
        return synchronized(catalogLock) {
            catalog ?: TidalCatalog(provider).also { catalog = it }
        }
    }

    private fun currentPlayer(): TidePlayerController? {
        playerController?.let { return it }
        if (!::applicationContext.isInitialized) return null
        val streamingAuth = TideRuntime.streamingAuth() ?: return null
        return synchronized(catalogLock) {
            playerController ?: TidePlayerController(applicationContext, streamingAuth) { track ->
                currentCatalog()?.similarTracks(track).orEmpty()
            }.also { playerController = it }
        }
    }

    private fun failure(
        category: TideErrorCategory,
        message: String,
        code: LightResult.ErrorCode = LightResult.ErrorCode.Unknown,
    ): LightResult.Error = LightResult.Error(code, TideError(category, message).encode())

    internal var collectionTimeoutMillis = 10_000L
        private set

    internal fun setCollectionTimeoutForTests(timeoutMillis: Long) {
        collectionTimeoutMillis = timeoutMillis
    }
}
