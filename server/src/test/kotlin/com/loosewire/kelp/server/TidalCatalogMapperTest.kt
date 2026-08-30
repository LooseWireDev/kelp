package com.loosewire.kelp.server

import com.loosewire.kelp.protocol.ReleaseType
import com.loosewire.kelp.protocol.SearchSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TidalCatalogMapperTest {
    @Test
    fun cursorExtractionTreatsBlankAndMissingValuesAsNoNextPage() {
        assertEquals(
            "opaque token",
            TidalCatalogMapper.extractCursor("https://openapi.tidal.com/albums?page%5Bcursor%5D=opaque%20token"),
        )
        assertNull(TidalCatalogMapper.extractCursor("https://openapi.tidal.com/albums?page%5Bcursor%5D="))
        assertNull(TidalCatalogMapper.extractCursor("https://openapi.tidal.com/albums?page%5Blimit%5D=20"))
    }

    @Test
    fun missingAlbumFieldsUseSafeFallbacks() {
        val release = TidalCatalogMapper.mapRelease(
            id = "album-id",
            title = null,
            artistName = null,
            typeName = null,
            itemCount = null,
        )

        assertEquals("Untitled", release.title)
        assertEquals("Unknown Artist", release.artistName)
        assertEquals(ReleaseType.Album, release.type)
        assertEquals(0, release.itemCount)
    }

    @Test
    fun releaseTypesMapToProtocolValues() {
        assertEquals(ReleaseType.Album, TidalCatalogMapper.mapReleaseType("ALBUM"))
        assertEquals(ReleaseType.Ep, TidalCatalogMapper.mapReleaseType("EP"))
        assertEquals(ReleaseType.Single, TidalCatalogMapper.mapReleaseType("SINGLE"))
        assertEquals(ReleaseType.Album, TidalCatalogMapper.mapReleaseType(null))
    }

    @Test
    fun trackResponsesDoNotRequireMusicalAnalysisFields() {
        val body = """
            {
              "data": [{
                "id": "track-1",
                "type": "tracks",
                "attributes": {
                  "title": "A Song",
                  "duration": "PT3M5.5S",
                  "explicit": true
                },
                "relationships": {
                  "artists": {"data": [{"id": "artist-1", "type": "artists"}]},
                  "albums": {"data": [{"id": "album-1", "type": "albums"}]}
                }
              }],
              "included": [
                {"id": "artist-1", "type": "artists", "attributes": {"name": "An Artist"}},
                {"id": "album-1", "type": "albums", "attributes": {"title": "An Album"}}
              ]
            }
        """.trimIndent()

        val track = TidalCatalogMapper.mapTrackResourcesJson(listOf("track-1"), body).single()

        assertEquals("A Song", track.title)
        assertEquals("An Artist", track.artistName)
        assertEquals("An Album", track.albumTitle)
        assertEquals(185_500L, track.durationMs)
        assertEquals(true, track.explicit)
    }

    @Test
    fun searchMapsTrackResponsesWithoutKeyOrKeyScale() {
        val body = """
            {
              "data": [],
              "included": [{
                "id": "track-1",
                "type": "tracks",
                "attributes": {"title": "A Song", "duration": "PT1M", "explicit": false},
                "relationships": {}
              }]
            }
        """.trimIndent()

        val track = TidalCatalogMapper.mapSearchJson(body).tracks.single()

        assertEquals("track-1", track.id)
        assertEquals("A Song", track.title)
    }

    @Test
    fun searchSectionUsesItsRelationshipCursor() {
        val body = """
            {
              "data": [{
                "id": "search-1",
                "type": "searchResults",
                "relationships": {
                  "artists": {
                    "links": {
                      "next": "https://openapi.tidal.com/v2/searchResults/search-1/relationships/artists?page%5Bcursor%5D=next-artists"
                    }
                  }
                }
              }],
              "included": [{
                "id": "artist-1",
                "type": "artists",
                "attributes": {"name": "An Artist"}
              }]
            }
        """.trimIndent()

        val results = TidalCatalogMapper.mapSearchJson(body, SearchSection.Artists)

        assertEquals(listOf("An Artist"), results.artists.map { it.name })
        assertEquals("next-artists", results.nextCursor)
    }
}
