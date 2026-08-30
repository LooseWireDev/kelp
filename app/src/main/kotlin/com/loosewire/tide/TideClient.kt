package com.loosewire.tide

import com.loosewire.tide.protocol.AlbumDetail
import com.loosewire.tide.protocol.AlbumRequest
import com.loosewire.tide.protocol.AuthSnapshot
import com.loosewire.tide.protocol.ArtistDetail
import com.loosewire.tide.protocol.ArtistRequest
import com.loosewire.tide.protocol.ArtistReleaseSection
import com.loosewire.tide.protocol.ArtistReleasesRequest
import com.loosewire.tide.protocol.ArtistSummary
import com.loosewire.tide.protocol.ArtistTracksRequest
import com.loosewire.tide.protocol.CollectionRequest
import com.loosewire.tide.protocol.HomeFeed
import com.loosewire.tide.protocol.Page
import com.loosewire.tide.protocol.PlaylistDetail
import com.loosewire.tide.protocol.PlaylistRequest
import com.loosewire.tide.protocol.PlaylistSummary
import com.loosewire.tide.protocol.PlaybackSnapshot
import com.loosewire.tide.protocol.PlayerCommand
import com.loosewire.tide.protocol.PlayerCommandRequest
import com.loosewire.tide.protocol.StartPlaybackRequest
import com.loosewire.tide.protocol.ReleaseSummary
import com.loosewire.tide.protocol.SearchRequest
import com.loosewire.tide.protocol.SearchPageRequest
import com.loosewire.tide.protocol.SearchResults
import com.loosewire.tide.protocol.SearchSection
import com.loosewire.tide.protocol.ServerActivity
import com.loosewire.tide.protocol.TrackSummary
import com.loosewire.tide.protocol.TideError
import com.loosewire.tide.protocol.TideErrorCategory
import com.loosewire.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import kotlinx.coroutines.CancellationException

interface TideClient {
    suspend fun authSnapshot(): TideClientResult<AuthSnapshot>
    suspend fun loginActivity(): TideClientResult<ServerActivity>
    suspend fun logout(): TideClientResult<AuthSnapshot> =
        error("Logout is not implemented by this TideClient")
    suspend fun collection(cursor: String? = null): TideClientResult<Page<ReleaseSummary>>
    suspend fun artists(cursor: String? = null): TideClientResult<Page<ArtistSummary>> =
        error("Artists are not implemented by this TideClient")
    suspend fun tracks(cursor: String? = null): TideClientResult<Page<TrackSummary>> =
        error("Tracks are not implemented by this TideClient")
    suspend fun home(): TideClientResult<HomeFeed> = error("Home is not implemented by this TideClient")
    suspend fun search(query: String): TideClientResult<SearchResults> =
        error("Search is not implemented by this TideClient")
    suspend fun searchPage(
        query: String,
        section: SearchSection,
        cursor: String? = null,
    ): TideClientResult<SearchResults> = error("Search pages are not implemented by this TideClient")
    suspend fun artistDetail(artist: ArtistSummary): TideClientResult<ArtistDetail> =
        error("Artist details are not implemented by this TideClient")
    suspend fun artistReleases(
        artist: ArtistSummary,
        section: ArtistReleaseSection,
        cursor: String? = null,
    ): TideClientResult<Page<ReleaseSummary>> =
        error("Artist releases are not implemented by this TideClient")
    suspend fun artistTracks(
        artist: ArtistSummary,
        cursor: String? = null,
    ): TideClientResult<Page<TrackSummary>> =
        error("Artist tracks are not implemented by this TideClient")
    suspend fun albumDetail(album: ReleaseSummary): TideClientResult<AlbumDetail> =
        error("Album details are not implemented by this TideClient")
    suspend fun playlistDetail(playlist: PlaylistSummary): TideClientResult<PlaylistDetail> =
        error("Playlist details are not implemented by this TideClient")
    suspend fun playback(): TideClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this TideClient")
    suspend fun startPlayback(request: StartPlaybackRequest): TideClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this TideClient")
    suspend fun controlPlayback(command: PlayerCommand): TideClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this TideClient")
    suspend fun seekPlayback(positionMs: Long): TideClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this TideClient")
}

sealed interface TideClientResult<out T> {
    data class Success<T>(val data: T) : TideClientResult<T>
    data class Failure(val error: TideError) : TideClientResult<Nothing>
}

object BinderTideClient : TideClient {
    override suspend fun authSnapshot(): TideClientResult<AuthSnapshot> =
        call(TideRemoteMethod.GetAuthSnapshot, Unit)

    override suspend fun loginActivity(): TideClientResult<ServerActivity> =
        call(TideRemoteMethod.GetLoginActivity, Unit)

    override suspend fun logout(): TideClientResult<AuthSnapshot> =
        call(TideRemoteMethod.Logout, Unit)

    override suspend fun collection(cursor: String?): TideClientResult<Page<ReleaseSummary>> =
        call(TideRemoteMethod.GetCollection, CollectionRequest(cursor))

    override suspend fun artists(cursor: String?): TideClientResult<Page<ArtistSummary>> =
        call(TideRemoteMethod.GetArtists, CollectionRequest(cursor))

    override suspend fun tracks(cursor: String?): TideClientResult<Page<TrackSummary>> =
        call(TideRemoteMethod.GetTracks, CollectionRequest(cursor))

    override suspend fun home(): TideClientResult<HomeFeed> =
        call(TideRemoteMethod.GetHome, Unit)

    override suspend fun search(query: String): TideClientResult<SearchResults> =
        call(TideRemoteMethod.Search, SearchRequest(query))

    override suspend fun searchPage(
        query: String,
        section: SearchSection,
        cursor: String?,
    ): TideClientResult<SearchResults> = call(
        TideRemoteMethod.SearchPage,
        SearchPageRequest(query, section, cursor),
    )

    override suspend fun artistDetail(artist: ArtistSummary): TideClientResult<ArtistDetail> =
        call(TideRemoteMethod.GetArtistDetail, ArtistRequest(artist))

    override suspend fun artistReleases(
        artist: ArtistSummary,
        section: ArtistReleaseSection,
        cursor: String?,
    ): TideClientResult<Page<ReleaseSummary>> = call(
        TideRemoteMethod.GetArtistReleases,
        ArtistReleasesRequest(artist, section, cursor),
    )

    override suspend fun artistTracks(
        artist: ArtistSummary,
        cursor: String?,
    ): TideClientResult<Page<TrackSummary>> = call(
        TideRemoteMethod.GetArtistTracks,
        ArtistTracksRequest(artist, cursor),
    )

    override suspend fun albumDetail(album: ReleaseSummary): TideClientResult<AlbumDetail> =
        call(TideRemoteMethod.GetAlbumDetail, AlbumRequest(album))

    override suspend fun playlistDetail(playlist: PlaylistSummary): TideClientResult<PlaylistDetail> =
        call(TideRemoteMethod.GetPlaylistDetail, PlaylistRequest(playlist))

    override suspend fun playback(): TideClientResult<PlaybackSnapshot> =
        call(TideRemoteMethod.GetPlayback, Unit)

    override suspend fun startPlayback(request: StartPlaybackRequest): TideClientResult<PlaybackSnapshot> =
        call(TideRemoteMethod.StartPlayback, request)

    override suspend fun controlPlayback(command: PlayerCommand): TideClientResult<PlaybackSnapshot> =
        call(TideRemoteMethod.ControlPlayback, PlayerCommandRequest(command))

    override suspend fun seekPlayback(positionMs: Long): TideClientResult<PlaybackSnapshot> =
        call(
            TideRemoteMethod.ControlPlayback,
            PlayerCommandRequest(PlayerCommand.Seek, positionMs),
        )

    private suspend fun <TRequest, TResponse> call(
        method: LightRemoteMethod<TRequest, TResponse>,
        request: TRequest,
    ): TideClientResult<TResponse> = try {
        when (val result = callRemoteServiceMethod(method, request)) {
            is LightResult.Success -> TideClientResult.Success(result.data)
            is LightResult.Error -> TideClientResult.Failure(result.toTideError())
        }

    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        TideClientResult.Failure(
            TideError(
                TideErrorCategory.Protocol,
                "Tide received an invalid response from its server.",
            ),
        )
    }

    private fun LightResult.Error.toTideError(): TideError =
        TideError.decodeOrNull(extra) ?: when (code) {
            LightResult.ErrorCode.NoPermission,
            LightResult.ErrorCode.InvalidToken,
            -> TideError(TideErrorCategory.Authentication, "Please sign in to TIDAL again.")

            LightResult.ErrorCode.InvalidParameters ->
                TideError(TideErrorCategory.Protocol, "Tide could not understand the server request.")

            LightResult.ErrorCode.Removed ->
                TideError(TideErrorCategory.Unavailable, "This Tide feature is no longer available.")

            LightResult.ErrorCode.Unknown ->
                TideError(TideErrorCategory.Unavailable, "Could not connect to the Tide server.")
        }
}
