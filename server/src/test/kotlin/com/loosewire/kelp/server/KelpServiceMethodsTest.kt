package com.loosewire.kelp.server

import com.loosewire.kelp.protocol.AlbumDetail
import com.loosewire.kelp.protocol.AlbumRequest
import com.loosewire.kelp.protocol.AuthState
import com.loosewire.kelp.protocol.ArtistDetail
import com.loosewire.kelp.protocol.ArtistRequest
import com.loosewire.kelp.protocol.ArtistSummary
import com.loosewire.kelp.protocol.CollectionRequest
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.HomeFeed
import com.loosewire.kelp.protocol.PlaylistDetail
import com.loosewire.kelp.protocol.PlaylistRequest
import com.loosewire.kelp.protocol.PlaylistSummary
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.SearchRequest
import com.loosewire.kelp.protocol.SearchResults
import com.loosewire.kelp.protocol.TrackSummary
import com.loosewire.kelp.protocol.KelpError
import com.loosewire.kelp.protocol.KelpErrorCategory
import com.loosewire.kelp.protocol.KelpRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import java.io.IOException
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KelpServiceMethodsTest {
    @AfterTest
    fun resetCatalog() {
        KelpServiceMethods.initializeCatalog(null)
        KelpServiceMethods.setCollectionTimeoutForTests(10_000L)
    }

    @Test
    fun authSnapshotUsesProtocolContract() {
        val result = KelpServiceMethods.dispatch(
            KelpRemoteMethod.GetAuthSnapshot.id,
            KelpRemoteMethod.GetAuthSnapshot.encodeRequest(Unit),
        )
        val response = KelpRemoteMethod.GetAuthSnapshot.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals(AuthState.MissingConfiguration, response.state)
        assertEquals(null, response.errorMessage)
    }

    @Test
    fun loginActivityComponentIsToolOwned() {
        val result = KelpServiceMethods.dispatch(
            KelpRemoteMethod.GetLoginActivity.id,
            KelpRemoteMethod.GetLoginActivity.encodeRequest(Unit),
        )
        val response = KelpRemoteMethod.GetLoginActivity.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals("com.loosewire.kelp/.server.LoginActivity", response.componentName)
    }

    @Test
    fun unknownMethodDoesNotDispatchToRuntime() {
        val result = KelpServiceMethods.dispatch("com.loosewire.kelp.unknown.v1", "{}")

        val failure = assertIs<LightResult.Error>(result)
        assertEquals(KelpErrorCategory.Protocol, KelpError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionWithoutCatalogReturnsError() {
        val result = KelpServiceMethods.dispatch(
            KelpRemoteMethod.GetCollection.id,
            KelpRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
        )

        val failure = assertIs<LightResult.Error>(result)
        assertEquals(KelpErrorCategory.Unavailable, KelpError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionTimeoutReturnsStructuredError() {
        KelpServiceMethods.initializeCatalog(object : Catalog {
            override suspend fun collection(cursor: String?): Page<ReleaseSummary> {
                delay(100)
                return Page(emptyList(), null)
            }
        })
        KelpServiceMethods.setCollectionTimeoutForTests(1L)

        val failure = assertIs<LightResult.Error>(dispatchCollection())

        assertEquals(KelpErrorCategory.Timeout, KelpError.decodeOrNull(failure.extra)?.category)
    }

    @Test
    fun collectionNetworkFailureReturnsStructuredError() {
        KelpServiceMethods.initializeCatalog(object : Catalog {
            override suspend fun collection(cursor: String?): Page<ReleaseSummary> {
                throw IOException("sensitive upstream detail")
            }
        })

        val failure = assertIs<LightResult.Error>(dispatchCollection())
        val error = KelpError.decodeOrNull(failure.extra)

        assertEquals(KelpErrorCategory.Network, error?.category)
        assertTrue(error?.message?.contains("sensitive") == false)
    }

    @Test
    fun everyCatalogMethodAcceptsItsProtocolEncodedRequest() {
        val artist = ArtistSummary("artist", "Artist")
        val playlist = PlaylistSummary("playlist", "Playlist")
        KelpServiceMethods.initializeCatalog(object : Catalog {
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
            KelpRemoteMethod.GetCollection.id to
                KelpRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
            KelpRemoteMethod.GetArtists.id to
                KelpRemoteMethod.GetArtists.encodeRequest(CollectionRequest()),
            KelpRemoteMethod.GetTracks.id to
                KelpRemoteMethod.GetTracks.encodeRequest(CollectionRequest()),
            KelpRemoteMethod.GetHome.id to KelpRemoteMethod.GetHome.encodeRequest(Unit),
            KelpRemoteMethod.Search.id to
                KelpRemoteMethod.Search.encodeRequest(SearchRequest("query")),
            KelpRemoteMethod.GetArtistDetail.id to
                KelpRemoteMethod.GetArtistDetail.encodeRequest(ArtistRequest(artist)),
            KelpRemoteMethod.GetAlbumDetail.id to
                KelpRemoteMethod.GetAlbumDetail.encodeRequest(
                    AlbumRequest(
                        ReleaseSummary("album", "Album", "Artist", com.loosewire.kelp.protocol.ReleaseType.Album, 1),
                    ),
                ),
            KelpRemoteMethod.GetPlaylistDetail.id to
                KelpRemoteMethod.GetPlaylistDetail.encodeRequest(PlaylistRequest(playlist)),
        )

        requests.forEach { (method, payload) ->
            assertIs<LightResult.Success<String>>(KelpServiceMethods.dispatch(method, payload))
        }
    }

    @Test
    fun malformedCatalogPayloadReturnsProtocolError() {
        val failure = assertIs<LightResult.Error>(
            KelpServiceMethods.dispatch(KelpRemoteMethod.Search.id, "{not-json}"),
        )

        assertEquals(KelpErrorCategory.Protocol, KelpError.decodeOrNull(failure.extra)?.category)
    }

    private fun dispatchCollection(): LightResult<String> = KelpServiceMethods.dispatch(
        KelpRemoteMethod.GetCollection.id,
        KelpRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
    )
}
