package com.loosewire.kelp.protocol

import com.thelightphone.sdk.shared.lightJson
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CatalogContractTest {
    @Test
    fun releaseSummaryPreservesTidalNativeTypes() {
        val expectedCatalog = ReleaseSummary(
            id = "123",
            title = "For Those That Wish To Exist",
            artistName = "Between the Buried and Me",
            type = ReleaseType.Ep,
            itemCount = 3,
        )

        val encoded = lightJson.encodeToString(ReleaseSummary.serializer(), expectedCatalog)

        assertEquals(expectedCatalog, lightJson.decodeFromString(ReleaseSummary.serializer(), encoded))
    }

    @Test
    fun cursorIsOpaqueAndPresenceControlsMoreLoading() {
        val pageSerializer = serializer<Page<ReleaseSummary>>()
        val page = Page(
            items = listOf(
                ReleaseSummary(
                    id = "1",
                    title = "Automata II",
                    artistName = "Between the Buried and Me",
                    type = ReleaseType.Album,
                    itemCount = 9,
                ),
            ),
            nextCursor = "server-opaque",
        )

        assertEquals(page, lightJson.decodeFromString(pageSerializer, lightJson.encodeToString(pageSerializer, page)))
        assertFalse(page.copy(nextCursor = null).hasMore)
    }

    @Test
    fun collectionRequestRoundTripsWithNullCursor() {
        val request = CollectionRequest(cursor = null)
        val encoded = lightJson.encodeToString(CollectionRequest.serializer(), request)
        val decoded = lightJson.decodeFromString(CollectionRequest.serializer(), encoded)
        assertEquals(request, decoded)
        assertNull(decoded.cursor)
    }

    @Test
    fun collectionRequestRoundTripsWithCursor() {
        val request = CollectionRequest(cursor = "opaque-page-token")
        val encoded = lightJson.encodeToString(CollectionRequest.serializer(), request)
        val decoded = lightJson.decodeFromString(CollectionRequest.serializer(), encoded)
        assertEquals(request, decoded)
        assertEquals("opaque-page-token", decoded.cursor)
    }

    @Test
    fun kelpErrorRoundTripsThroughBinderExtra() {
        val expected = KelpError(
            category = KelpErrorCategory.Timeout,
            message = "Loading your collection timed out. Please retry.",
        )

        assertEquals(expected, KelpError.decodeOrNull(expected.encode()))
        assertNull(KelpError.decodeOrNull("not a Kelp error"))
    }

    @Test
    fun homeAndPlaylistContractsRoundTrip() {
        val playlist = PlaylistSummary("playlist", "Road Trip", "Long drives", 12)
        val feed = HomeFeed(mixes = listOf(playlist.copy(id = "mix")), playlists = listOf(playlist))
        val detail = PlaylistDetail(
            playlist = playlist,
            tracks = listOf(TrackSummary("track", "Song", "Artist", 120_000, false, "Album")),
        )

        assertEquals(
            feed,
            lightJson.decodeFromString(HomeFeed.serializer(), lightJson.encodeToString(HomeFeed.serializer(), feed)),
        )
        assertEquals(
            detail,
            lightJson.decodeFromString(
                PlaylistDetail.serializer(),
                lightJson.encodeToString(PlaylistDetail.serializer(), detail),
            ),
        )

        val albumDetail = AlbumDetail(
            album = ReleaseSummary("album", "Album", "Artist", ReleaseType.Album, 1),
            tracks = detail.tracks,
        )
        assertEquals(
            albumDetail,
            lightJson.decodeFromString(
                AlbumDetail.serializer(),
                lightJson.encodeToString(AlbumDetail.serializer(), albumDetail),
            ),
        )
    }
}
