package com.loosewire.tide.server

import com.loosewire.tide.protocol.AlbumDetail
import com.loosewire.tide.protocol.AlbumRequest
import com.loosewire.tide.protocol.AuthState
import com.loosewire.tide.protocol.ArtistDetail
import com.loosewire.tide.protocol.ArtistRequest
import com.loosewire.tide.protocol.ArtistSummary
import com.loosewire.tide.protocol.CollectionRequest
import com.loosewire.tide.protocol.Page
import com.loosewire.tide.protocol.HomeFeed
import com.loosewire.tide.protocol.PlaylistDetail
import com.loosewire.tide.protocol.PlaylistRequest
import com.loosewire.tide.protocol.PlaylistSummary
import com.loosewire.tide.protocol.ReleaseSummary
import com.loosewire.tide.protocol.SearchRequest
import com.loosewire.tide.protocol.SearchResults
import com.loosewire.tide.protocol.TrackSummary
import com.loosewire.tide.protocol.TideError
import com.loosewire.tide.protocol.TideErrorCategory
import com.loosewire.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import java.io.IOException
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TideServiceMethodsTest {
    @AfterTest
    fun resetCatalog() {
        TideServiceMethods.initializeCatalog(null)
        TideServiceMethods.setCollectionTimeoutForTests(10_000L)
    }

    @Test
    fun authSnapshotUsesProtocolContract() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetAuthSnapshot.id,
            TideRemoteMethod.GetAuthSnapshot.encodeRequest(Unit),
        )
        val response = TideRemoteMethod.GetAuthSnapshot.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals(AuthState.MissingConfiguration, response.state)
        assertEquals(null, response.errorMessage)
    }

    @Test
    fun loginActivityComponentIsToolOwned() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetLoginActivity.id,
            TideRemoteMethod.GetLoginActivity.encodeRequest(Unit),
        )
        val response = TideRemoteMethod.GetLoginActivity.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals("com.loosewire.tide/.server.LoginActivity", response.componentName)
    }

    @Test
    fun unknownMethodDoesNotDispatchToRuntime() {
        val result = TideServiceMethods.dispatch("com.loosewire.tide.unknown.v1", "{}")

        val failure = assertIs<LightResult.Error>(result)
        assertEquals(TideErrorCategory.Protocol, TideError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionWithoutCatalogReturnsError() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetCollection.id,
            TideRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
        )

        val failure = assertIs<LightResult.Error>(result)
        assertEquals(TideErrorCategory.Unavailable, TideError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionTimeoutReturnsStructuredError() {
        TideServiceMethods.initializeCatalog(object : Catalog {
            override suspend fun collection(cursor: String?): Page<ReleaseSummary> {
                delay(100)
                return Page(emptyList(), null)
            }
        })
        TideServiceMethods.setCollectionTimeoutForTests(1L)

        val failure = assertIs<LightResult.Error>(dispatchCollection())

        assertEquals(TideErrorCategory.Timeout, TideError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionNetworkFailureReturnsStructuredError() {
        TideServiceMethods.initializeCatalog(object : Catalog {
            override suspend fun collection(cursor: String?): Page<ReleaseSummary> {
                throw IOException("sensitive upstream detail")
            }
        })

        val failure = assertIs<LightResult.Error>(dispatchCollection())
        val error = TideError.decodeOrNull(failure.extra)

        assertEquals(TideErrorCategory.Network, error?.category)
        assertTrue(error?.message?.contains("sensitive") == false)
    }

    @Test
    fun everyCatalogMethodAcceptsItsProtocolEncodedRequest() {
        val artist = ArtistSummary("artist", "Artist")
        val playlist = PlaylistSummary("playlist", "Playlist")
        TideServiceMethods.initializeCatalog(object : Catalog {
            override suspend fun collection(cursor: String?) = Page<ReleaseSummary>(emptyList(), null)
            override suspend fun artists(cursor: String?) = Page<ArtistSummary>(emptyList(), null)
            override suspend fun tracks(cursor: String?) = Page<TrackSummary>(emptyList(), null)
            override suspend fun home() = HomeFeed(emptyList(), emptyList())
            override suspend fun search(query: String) = SearchResults(emptyList(), emptyList(), emptyList())
            override suspend fun artistDetail(artist: ArtistSummary) =
                ArtistDetail(artist, emptyList(), emptyList())
            override suspend fun albumDetail(album: ReleaseSummary) =
                AlbumDetail(album, emptyList())
            override suspend fun playlistDetail(playlist: PlaylistSummary) =
                PlaylistDetail(playlist, emptyList())
        })

        val requests = listOf(
            TideRemoteMethod.GetCollection.id to
                TideRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
            TideRemoteMethod.GetArtists.id to
                TideRemoteMethod.GetArtists.encodeRequest(CollectionRequest()),
            TideRemoteMethod.GetTracks.id to
                TideRemoteMethod.GetTracks.encodeRequest(CollectionRequest()),
            TideRemoteMethod.GetHome.id to TideRemoteMethod.GetHome.encodeRequest(Unit),
            TideRemoteMethod.Search.id to
                TideRemoteMethod.Search.encodeRequest(SearchRequest("query")),
            TideRemoteMethod.GetArtistDetail.id to
                TideRemoteMethod.GetArtistDetail.encodeRequest(ArtistRequest(artist)),
            TideRemoteMethod.GetAlbumDetail.id to
                TideRemoteMethod.GetAlbumDetail.encodeRequest(
                    AlbumRequest(
                        ReleaseSummary("album", "Album", "Artist", com.loosewire.tide.protocol.ReleaseType.Album, 1),
                    ),
                ),
            TideRemoteMethod.GetPlaylistDetail.id to
                TideRemoteMethod.GetPlaylistDetail.encodeRequest(PlaylistRequest(playlist)),
        )

        requests.forEach { (method, payload) ->
            assertIs<LightResult.Success<String>>(TideServiceMethods.dispatch(method, payload))
        }
    }

    @Test
    fun malformedCatalogPayloadReturnsProtocolError() {
        val failure = assertIs<LightResult.Error>(
            TideServiceMethods.dispatch(TideRemoteMethod.Search.id, "{not-json}"),
        )

        assertEquals(TideErrorCategory.Protocol, TideError.decodeOrNull(failure.extra)?.category)
    }

    private fun dispatchCollection(): LightResult<String> = TideServiceMethods.dispatch(
        TideRemoteMethod.GetCollection.id,
        TideRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
    )
}
