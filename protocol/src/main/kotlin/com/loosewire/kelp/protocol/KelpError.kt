package com.loosewire.kelp.protocol

import com.thelightphone.sdk.shared.lightJson
import kotlinx.serialization.Serializable

@Serializable
enum class KelpErrorCategory {
    Authentication,
    Network,
    Timeout,
    Protocol,
    Unavailable,
}

@Serializable
data class KelpError(
    val category: KelpErrorCategory,
    val message: String,
) {
    fun encode(): String = lightJson.encodeToString(serializer(), this)

    companion object {
        fun decodeOrNull(value: String?): KelpError? = value?.let {
            runCatching { lightJson.decodeFromString(serializer(), it) }.getOrNull()
        }
    }
}
