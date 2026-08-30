package com.loosewire.tide.protocol

import com.thelightphone.sdk.shared.LightRemoteMethod
import kotlinx.serialization.serializer

sealed interface TideRemoteMethod<TRequest, TResponse> : LightRemoteMethod<TRequest, TResponse> {
    /** Fetch and acknowledge auth changes resulting from the server activity. */
    object GetAuthSnapshot : TideRemoteMethod<Unit, AuthSnapshot> {
        override val id = "com.loosewire.tide.auth.snapshot.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<AuthSnapshot>()
    }

    /** Locate the unrestricted OAuth activity managed by the merged server. */
    object GetLoginActivity : TideRemoteMethod<Unit, ServerActivity> {
        override val id = "com.loosewire.tide.auth.login-activity.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<ServerActivity>()
    }

    /** Clear all TIDAL sessions (catalog and playback) and report the new auth state. */
    object Logout : TideRemoteMethod<Unit, AuthSnapshot> {
        override val id = "com.loosewire.tide.auth.logout.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<AuthSnapshot>()
    }

    /** Fetch a page of the authenticated user's collection. */
    object GetCollection : TideRemoteMethod<CollectionRequest, Page<ReleaseSummary>> {
        override val id = "com.loosewire.tide.catalog.collection.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<ReleaseSummary>>()
    }

    /** Fetch a page of artists saved by the authenticated user. */
    object GetArtists : TideRemoteMethod<CollectionRequest, Page<ArtistSummary>> {
        override val id = "com.loosewire.tide.catalog.artists.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<ArtistSummary>>()
    }

    /** Fetch a page of tracks saved by the authenticated user. */
    object GetTracks : TideRemoteMethod<CollectionRequest, Page<TrackSummary>> {
        override val id = "com.loosewire.tide.catalog.tracks.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<TrackSummary>>()
    }

    /** Fetch the personalized mixes and saved playlists displayed on Home. */
    object GetHome : TideRemoteMethod<Unit, HomeFeed> {
        override val id = "com.loosewire.tide.catalog.home.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<HomeFeed>()
    }

    /** Search TIDAL and return results grouped for the Light UI. */
    object Search : TideRemoteMethod<SearchRequest, SearchResults> {
        override val id = "com.loosewire.tide.catalog.search.v1"
        override val requestSerializer = serializer<SearchRequest>()
        override val responseSerializer = serializer<SearchResults>()
    }

    /** Fetch one paginated section of a catalog search. */
    object SearchPage : TideRemoteMethod<SearchPageRequest, SearchResults> {
        override val id = "com.loosewire.tide.catalog.search-page.v1"
        override val requestSerializer = serializer<SearchPageRequest>()
        override val responseSerializer = serializer<SearchResults>()
    }

    /** Fetch the album, EP/single, and track sections for an artist. */
    object GetArtistDetail : TideRemoteMethod<ArtistRequest, ArtistDetail> {
        override val id = "com.loosewire.tide.catalog.artist-detail.v1"
        override val requestSerializer = serializer<ArtistRequest>()
        override val responseSerializer = serializer<ArtistDetail>()
    }

    /** Fetch one paginated release section for an artist. */
    object GetArtistReleases : TideRemoteMethod<ArtistReleasesRequest, Page<ReleaseSummary>> {
        override val id = "com.loosewire.tide.catalog.artist-releases.v1"
        override val requestSerializer = serializer<ArtistReleasesRequest>()
        override val responseSerializer = serializer<Page<ReleaseSummary>>()
    }

    /** Fetch a page of an artist's songs. */
    object GetArtistTracks : TideRemoteMethod<ArtistTracksRequest, Page<TrackSummary>> {
        override val id = "com.loosewire.tide.catalog.artist-tracks.v1"
        override val requestSerializer = serializer<ArtistTracksRequest>()
        override val responseSerializer = serializer<Page<TrackSummary>>()
    }

    /** Fetch the ordered songs in an album, EP, or single. */
    object GetAlbumDetail : TideRemoteMethod<AlbumRequest, AlbumDetail> {
        override val id = "com.loosewire.tide.catalog.album-detail.v1"
        override val requestSerializer = serializer<AlbumRequest>()
        override val responseSerializer = serializer<AlbumDetail>()
    }

    /** Fetch the ordered songs in a playlist or personalized mix. */
    object GetPlaylistDetail : TideRemoteMethod<PlaylistRequest, PlaylistDetail> {
        override val id = "com.loosewire.tide.catalog.playlist-detail.v1"
        override val requestSerializer = serializer<PlaylistRequest>()
        override val responseSerializer = serializer<PlaylistDetail>()
    }


    object GetPlayback : TideRemoteMethod<Unit, PlaybackSnapshot> {
        override val id = "com.loosewire.tide.player.snapshot.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }

    object StartPlayback : TideRemoteMethod<StartPlaybackRequest, PlaybackSnapshot> {
        override val id = "com.loosewire.tide.player.start.v1"
        override val requestSerializer = serializer<StartPlaybackRequest>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }

    object ControlPlayback : TideRemoteMethod<PlayerCommandRequest, PlaybackSnapshot> {
        override val id = "com.loosewire.tide.player.control.v1"
        override val requestSerializer = serializer<PlayerCommandRequest>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }
}

val tideRemoteMethods: Map<String, TideRemoteMethod<*, *>> = listOf(
    TideRemoteMethod.GetAuthSnapshot,
    TideRemoteMethod.GetLoginActivity,
    TideRemoteMethod.Logout,
    TideRemoteMethod.GetCollection,
    TideRemoteMethod.GetArtists,
    TideRemoteMethod.GetTracks,
    TideRemoteMethod.GetHome,
    TideRemoteMethod.Search,
    TideRemoteMethod.SearchPage,
    TideRemoteMethod.GetArtistDetail,
    TideRemoteMethod.GetArtistReleases,
    TideRemoteMethod.GetArtistTracks,
    TideRemoteMethod.GetAlbumDetail,
    TideRemoteMethod.GetPlaylistDetail,
    TideRemoteMethod.GetPlayback,
    TideRemoteMethod.StartPlayback,
    TideRemoteMethod.ControlPlayback,
).associateBy { it.id }
