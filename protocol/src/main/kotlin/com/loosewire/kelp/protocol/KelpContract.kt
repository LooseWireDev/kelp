package com.loosewire.kelp.protocol

import com.thelightphone.sdk.shared.LightRemoteMethod
import kotlinx.serialization.serializer

sealed interface KelpRemoteMethod<TRequest, TResponse> : LightRemoteMethod<TRequest, TResponse> {
    /** Fetch and acknowledge auth changes resulting from the server activity. */
    object GetAuthSnapshot : KelpRemoteMethod<Unit, AuthSnapshot> {
        override val id = "com.loosewire.kelp.auth.snapshot.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<AuthSnapshot>()
    }

    /** Locate the unrestricted OAuth activity managed by the merged server. */
    object GetLoginActivity : KelpRemoteMethod<Unit, ServerActivity> {
        override val id = "com.loosewire.kelp.auth.login-activity.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<ServerActivity>()
    }

    /** Clear all TIDAL sessions (catalog and playback) and report the new auth state. */
    object Logout : KelpRemoteMethod<Unit, AuthSnapshot> {
        override val id = "com.loosewire.kelp.auth.logout.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<AuthSnapshot>()
    }

    /** Fetch a page of the authenticated user's collection. */
    object GetCollection : KelpRemoteMethod<CollectionRequest, Page<ReleaseSummary>> {
        override val id = "com.loosewire.kelp.catalog.collection.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<ReleaseSummary>>()
    }

    /** Fetch a page of artists saved by the authenticated user. */
    object GetArtists : KelpRemoteMethod<CollectionRequest, Page<ArtistSummary>> {
        override val id = "com.loosewire.kelp.catalog.artists.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<ArtistSummary>>()
    }

    /** Fetch a page of tracks saved by the authenticated user. */
    object GetTracks : KelpRemoteMethod<CollectionRequest, Page<TrackSummary>> {
        override val id = "com.loosewire.kelp.catalog.tracks.v1"
        override val requestSerializer = serializer<CollectionRequest>()
        override val responseSerializer = serializer<Page<TrackSummary>>()
    }

    /** Fetch the personalized mixes and saved playlists displayed on Home. */
    object GetHome : KelpRemoteMethod<Unit, HomeFeed> {
        override val id = "com.loosewire.kelp.catalog.home.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<HomeFeed>()
    }

    /** Search TIDAL and return results grouped for the Light UI. */
    object Search : KelpRemoteMethod<SearchRequest, SearchResults> {
        override val id = "com.loosewire.kelp.catalog.search.v1"
        override val requestSerializer = serializer<SearchRequest>()
        override val responseSerializer = serializer<SearchResults>()
    }

    /** Fetch one paginated section of a catalog search. */
    object SearchPage : KelpRemoteMethod<SearchPageRequest, SearchResults> {
        override val id = "com.loosewire.kelp.catalog.search-page.v1"
        override val requestSerializer = serializer<SearchPageRequest>()
        override val responseSerializer = serializer<SearchResults>()
    }

    /** Fetch the album, EP/single, and track sections for an artist. */
    object GetArtistDetail : KelpRemoteMethod<ArtistRequest, ArtistDetail> {
        override val id = "com.loosewire.kelp.catalog.artist-detail.v1"
        override val requestSerializer = serializer<ArtistRequest>()
        override val responseSerializer = serializer<ArtistDetail>()
    }

    /** Fetch one paginated release section for an artist. */
    object GetArtistReleases : KelpRemoteMethod<ArtistReleasesRequest, Page<ReleaseSummary>> {
        override val id = "com.loosewire.kelp.catalog.artist-releases.v1"
        override val requestSerializer = serializer<ArtistReleasesRequest>()
        override val responseSerializer = serializer<Page<ReleaseSummary>>()
    }

    /** Fetch a page of an artist's songs. */
    object GetArtistTracks : KelpRemoteMethod<ArtistTracksRequest, Page<TrackSummary>> {
        override val id = "com.loosewire.kelp.catalog.artist-tracks.v1"
        override val requestSerializer = serializer<ArtistTracksRequest>()
        override val responseSerializer = serializer<Page<TrackSummary>>()
    }

    /** Fetch the ordered songs in an album, EP, or single. */
    object GetAlbumDetail : KelpRemoteMethod<AlbumRequest, AlbumDetail> {
        override val id = "com.loosewire.kelp.catalog.album-detail.v1"
        override val requestSerializer = serializer<AlbumRequest>()
        override val responseSerializer = serializer<AlbumDetail>()
    }

    /** Fetch the ordered songs in a playlist or personalized mix. */
    object GetPlaylistDetail : KelpRemoteMethod<PlaylistRequest, PlaylistDetail> {
        override val id = "com.loosewire.kelp.catalog.playlist-detail.v1"
        override val requestSerializer = serializer<PlaylistRequest>()
        override val responseSerializer = serializer<PlaylistDetail>()
    }


    object GetPlayback : KelpRemoteMethod<Unit, PlaybackSnapshot> {
        override val id = "com.loosewire.kelp.player.snapshot.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }

    object StartPlayback : KelpRemoteMethod<StartPlaybackRequest, PlaybackSnapshot> {
        override val id = "com.loosewire.kelp.player.start.v1"
        override val requestSerializer = serializer<StartPlaybackRequest>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }

    object ControlPlayback : KelpRemoteMethod<PlayerCommandRequest, PlaybackSnapshot> {
        override val id = "com.loosewire.kelp.player.control.v1"
        override val requestSerializer = serializer<PlayerCommandRequest>()
        override val responseSerializer = serializer<PlaybackSnapshot>()
    }
}

val kelpRemoteMethods: Map<String, KelpRemoteMethod<*, *>> = listOf(
    KelpRemoteMethod.GetAuthSnapshot,
    KelpRemoteMethod.GetLoginActivity,
    KelpRemoteMethod.Logout,
    KelpRemoteMethod.GetCollection,
    KelpRemoteMethod.GetArtists,
    KelpRemoteMethod.GetTracks,
    KelpRemoteMethod.GetHome,
    KelpRemoteMethod.Search,
    KelpRemoteMethod.SearchPage,
    KelpRemoteMethod.GetArtistDetail,
    KelpRemoteMethod.GetArtistReleases,
    KelpRemoteMethod.GetArtistTracks,
    KelpRemoteMethod.GetAlbumDetail,
    KelpRemoteMethod.GetPlaylistDetail,
    KelpRemoteMethod.GetPlayback,
    KelpRemoteMethod.StartPlayback,
    KelpRemoteMethod.ControlPlayback,
).associateBy { it.id }
