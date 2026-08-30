package com.loosewire.kelp.protocol

import com.thelightphone.sdk.shared.lightJson
import kotlinx.serialization.Serializable

@Serializable
enum class TideErrorCategory {
    Authentication,
    Network,
    Timeout,
    Protocol,
    Unavailable,
}

@Serializable
data class TideError(
    val category: TideErrorCategory,
    val message: String,
) {
    fun encode(): String = lightJson.encodeToString(serializer(), this)

    companion object {
        fun decodeOrNull(value: String?): TideError? = value?.let {
            runCatching { lightJson.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
