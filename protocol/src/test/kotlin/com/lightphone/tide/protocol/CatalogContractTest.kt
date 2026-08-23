package com.lightphone.tide.protocol

import com.thelightphone.sdk.shared.lightJson
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
