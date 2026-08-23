package com.lightphone.tide.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class ReleaseType {
    Album,
    Ep,
    Single,
}

@Serializable
data class ReleaseSummary(
    val id: String,
    val title: String,
    val artistName: String,
    val type: ReleaseType,
    val itemCount: Int,
)

@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    val hasMore: Boolean
        get() = nextCursor != null
}
