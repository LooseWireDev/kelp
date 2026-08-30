package com.loosewire.kelp.server

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Compatibility surface for the current TIDAL search contract.
 *
 * TIDAL Android SDK 0.3.53 was generated from API schema 1.10.86, where search
 * used `/searchResults/{id}` and returned a single resource. The current API
 * uses `/searchResults?filter[query]=...` and returns a resource array. Keep
 * this adapter narrow so the generated client continues to own every endpoint
 * whose contract has not changed.
 */
internal interface CurrentSearchApi {
    @GET("searchResults")
    suspend fun searchResultsGet(
        @Query("filter[query]") query: String,
        @Query("include") include: List<String>,
        @Query("page[cursor]") pageCursor: String? = null,
    ): Response<String>
}

/**
 * Raw response surface for track resources.
 *
 * API schema 1.10.86 marks the musical-analysis fields `key` and `keyScale`
 * as required, while catalog responses legitimately omit them. Returning the
 * JSON body as a string keeps those unused fields from making every song list
 * fail before Kelp can map the fields it actually displays.
 */
internal interface CurrentTracksApi {
    @GET("tracks")
    suspend fun tracksGet(
        @Query("include") include: List<String>,
        @Query("filter[id]") filterId: List<String>,
    ): Response<String>
}
