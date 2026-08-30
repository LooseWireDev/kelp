package com.loosewire.tide.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import retrofit2.http.GET
import retrofit2.http.Query

class CurrentSearchApiTest {
    @Test
    fun searchUsesCurrentQueryEndpointInsteadOfLegacyPathId() {
        val method = CurrentSearchApi::class.java.declaredMethods.single {
            it.name == "searchResultsGet"
        }

        assertEquals("searchResults", assertNotNull(method.getAnnotation(GET::class.java)).value)
        assertEquals(
            listOf("filter[query]", "include", "page[cursor]"),
            method.parameterAnnotations.dropLast(1).map { annotations ->
                annotations.filterIsInstance<Query>().single().value
            },
        )
    }

    @Test
    fun tracksUseRawCurrentEndpointForTolerantDecoding() {
        val method = CurrentTracksApi::class.java.declaredMethods.single {
            it.name == "tracksGet"
        }

        assertEquals("tracks", assertNotNull(method.getAnnotation(GET::class.java)).value)
        assertEquals(
            listOf("include", "filter[id]"),
            method.parameterAnnotations.dropLast(1).map { annotations ->
                annotations.filterIsInstance<Query>().single().value
            },
        )
    }
}
