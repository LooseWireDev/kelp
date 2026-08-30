package com.loosewire.kelp

import com.loosewire.kelp.protocol.AlbumDetail
import com.loosewire.kelp.protocol.AlbumRequest
import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.ArtistDetail
import com.loosewire.kelp.protocol.ArtistRequest
import com.loosewire.kelp.protocol.ArtistReleaseSection
import com.loosewire.kelp.protocol.ArtistReleasesRequest
import com.loosewire.kelp.protocol.ArtistSummary
import com.loosewire.kelp.protocol.ArtistTracksRequest
import com.loosewire.kelp.protocol.CollectionRequest
import com.loosewire.kelp.protocol.HomeFeed
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.PlaylistDetail
import com.loosewire.kelp.protocol.PlaylistRequest
import com.loosewire.kelp.protocol.PlaylistSummary
import com.loosewire.kelp.protocol.PlaybackSnapshot
import com.loosewire.kelp.protocol.PlayerCommand
import com.loosewire.kelp.protocol.PlayerCommandRequest
import com.loosewire.kelp.protocol.StartPlaybackRequest
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.SearchRequest
import com.loosewire.kelp.protocol.SearchPageRequest
import com.loosewire.kelp.protocol.SearchResults
import com.loosewire.kelp.protocol.SearchSection
import com.loosewire.kelp.protocol.ServerActivity
import com.loosewire.kelp.protocol.TrackSummary
import com.loosewire.kelp.protocol.KelpError
import com.loosewire.kelp.protocol.KelpErrorCategory
import com.loosewire.kelp.protocol.KelpRemoteMethod
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import kotlinx.coroutines.CancellationException

interface KelpClient {
    suspend fun authSnapshot(): KelpClientResult<AuthSnapshot>
    suspend fun loginActivity(): KelpClientResult<ServerActivity>
    suspend fun logout(): KelpClientResult<AuthSnapshot> =
        error("Logout is not implemented by this KelpClient")
    suspend fun collection(cursor: String? = null): KelpClientResult<Page<ReleaseSummary>>
    suspend fun artists(cursor: String? = null): KelpClientResult<Page<ArtistSummary>> =
        error("Artists are not implemented by this KelpClient")
    suspend fun tracks(cursor: String? = null): KelpClientResult<Page<TrackSummary>> =
        error("Tracks are not implemented by this KelpClient")
    suspend fun home(): KelpClientResult<HomeFeed> = error("Home is not implemented by this KelpClient")
    suspend fun search(query: String): KelpClientResult<SearchResults> =
        error("Search is not implemented by this KelpClient")
    suspend fun searchPage(
        query: String,
        section: SearchSection,
        cursor: String? = null,
    ): KelpClientResult<SearchResults> = error("Search pages are not implemented by this KelpClient")
    suspend fun artistDetail(artist: ArtistSummary): KelpClientResult<ArtistDetail> =
        error("Artist details are not implemented by this KelpClient")
    suspend fun artistReleases(
        artist: ArtistSummary,
        section: ArtistReleaseSection,
        cursor: String? = null,
    ): KelpClientResult<Page<ReleaseSummary>> =
        error("Artist releases are not implemented by this KelpClient")
    suspend fun artistTracks(
        artist: ArtistSummary,
        cursor: String? = null,
    ): KelpClientResult<Page<TrackSummary>> =
        error("Artist tracks are not implemented by this KelpClient")
    suspend fun albumDetail(album: ReleaseSummary): KelpClientResult<AlbumDetail> =
        error("Album details are not implemented by this KelpClient")
    suspend fun playlistDetail(playlist: PlaylistSummary): KelpClientResult<PlaylistDetail> =
        error("Playlist details are not implemented by this KelpClient")
    suspend fun playback(): KelpClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this KelpClient")
    suspend fun startPlayback(request: StartPlaybackRequest): KelpClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this KelpClient")
    suspend fun controlPlayback(command: PlayerCommand): KelpClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this KelpClient")
    suspend fun seekPlayback(positionMs: Long): KelpClientResult<PlaybackSnapshot> =
        error("Playback is not implemented by this KelpClient")
}

sealed interface KelpClientResult<out T> {
    data class Success<T>(val data: T) : KelpClientResult<T>
    data class Failure(val error: KelpError) : KelpClientResult<Nothing>
}

object BinderTideClient : KelpClient {
    override suspend fun authSnapshot(): KelpClientResult<AuthSnapshot> =
        call(KelpRemoteMethod.GetAuthSnapshot, Unit)

    override suspend fun loginActivity(): KelpClientResult<ServerActivity> =
        call(KelpRemoteMethod.GetLoginActivity, Unit)

    override suspend fun logout(): KelpClientResult<AuthSnapshot> =
        call(KelpRemoteMethod.Logout, Unit)

    override suspend fun collection(cursor: String?): KelpClientResult<Page<ReleaseSummary>> =
        call(KelpRemoteMethod.GetCollection, CollectionRequest(cursor))

    override suspend fun artists(cursor: String?): KelpClientResult<Page<ArtistSummary>> =
        call(KelpRemoteMethod.GetArtists, CollectionRequest(cursor))

    override suspend fun tracks(cursor: String?): KelpClientResult<Page<TrackSummary>> =
        call(KelpRemoteMethod.GetTracks, CollectionRequest(cursor))

    override suspend fun home(): KelpClientResult<HomeFeed> =
        call(KelpRemoteMethod.GetHome, Unit)

    override suspend fun search(query: String): KelpClientResult<SearchResults> =
        call(KelpRemoteMethod.Search, SearchRequest(query))

    override suspend fun searchPage(
        query: String,
        section: SearchSection,
        cursor: String?,
    ): KelpClientResult<SearchResults> = call(
        KelpRemoteMethod.SearchPage,
        SearchPageRequest(query, section, cursor),
    )

    override suspend fun artistDetail(artist: ArtistSummary): KelpClientResult<ArtistDetail> =
        call(KelpRemoteMethod.GetArtistDetail, ArtistRequest(artist))

    override suspend fun artistReleases(
        artist: ArtistSummary,
        section: ArtistReleaseSection,
        cursor: String?,
    ): KelpClientResult<Page<ReleaseSummary>> = call(
        KelpRemoteMethod.GetArtistReleases,
        ArtistReleasesRequest(artist, section, cursor),
    )

    override suspend fun artistTracks(
        artist: ArtistSummary,
        cursor: String?,
    ): KelpClientResult<Page<TrackSummary>> = call(
        KelpRemoteMethod.GetArtistTracks,
        ArtistTracksRequest(artist, cursor),
    )

    override suspend fun albumDetail(album: ReleaseSummary): KelpClientResult<AlbumDetail> =
        call(KelpRemoteMethod.GetAlbumDetail, AlbumRequest(album))

    override suspend fun playlistDetail(playlist: PlaylistSummary): KelpClientResult<PlaylistDetail> =
        call(KelpRemoteMethod.GetPlaylistDetail, PlaylistRequest(playlist))

    override suspend fun playback(): KelpClientResult<PlaybackSnapshot> =
        call(KelpRemoteMethod.GetPlayback, Unit)

    override suspend fun startPlayback(request: StartPlaybackRequest): KelpClientResult<PlaybackSnapshot> =
        call(KelpRemoteMethod.StartPlayback, request)

    override suspend fun controlPlayback(command: PlayerCommand): KelpClientResult<PlaybackSnapshot> =
        call(KelpRemoteMethod.ControlPlayback, PlayerCommandRequest(command))

    override suspend fun seekPlayback(positionMs: Long): KelpClientResult<PlaybackSnapshot> =
        call(
            KelpRemoteMethod.ControlPlayback,
            PlayerCommandRequest(PlayerCommand.Seek, positionMs),
        )

    private suspend fun <TRequest, TResponse> call(
        method: LightRemoteMethod<TRequest, TResponse>,
        request: TRequest,
    ): KelpClientResult<TResponse> = try {
        when (val result = callRemoteServiceMethod(method, request)) {
            is LightResult.Success -> KelpClientResult.Success(result.data)
            is LightResult.Error -> KelpClientResult.Failure(result.toTideError())
        }

    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        KelpClientResult.Failure(
            KelpError(
                KelpErrorCategory.Protocol,
                "Kelp received an invalid response from its server.",
            ),
        )
    }

    private fun LightResult.Error.toTideError(): KelpError =
        KelpError.decodeOrNull(extra) ?: when (code) {
            LightResult.ErrorCode.NoPermission,
            LightResult.ErrorCode.InvalidToken,
            -> KelpError(KelpErrorCategory.Authentication, "Please sign in to TIDAL again.")

            LightResult.ErrorCode.InvalidParameters ->
                KelpError(KelpErrorCategory.Protocol, "Kelp could not understand the server request.")

            LightResult.ErrorCode.Removed ->
                KelpError(KelpErrorCategory.Unavailable, "This Kelp feature is no longer available.")

            LightResult.ErrorCode.Unknown ->
                KelpError(KelpErrorCategory.Unavailable, "Could not connect to the Kelp server.")
        }
}
