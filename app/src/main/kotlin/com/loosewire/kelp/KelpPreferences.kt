package com.loosewire.kelp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class KelpAudioQuality(
    val label: String,
    val detail: String,
) {
    Low("Low", "Data saver"),
    High("High", "320 kbps"),
    Lossless("Lossless", "CD quality"),
    HiResLossless("Max", "Hi-res lossless"),
    ;

    fun next(): KelpAudioQuality = entries[(ordinal + 1) % entries.size]
}

data class KelpPlaybackPreferences(
    val wifiQuality: KelpAudioQuality = KelpAudioQuality.Lossless,
    val mobileQuality: KelpAudioQuality = KelpAudioQuality.High,
    val downloadQuality: KelpAudioQuality = KelpAudioQuality.Lossless,
    val normalizeVolume: Boolean = true,
    val allowExplicitContent: Boolean = true,
    val continuousPlayback: Boolean = true,
)

interface KelpPreferences {
    val playback: Flow<KelpPlaybackPreferences>

    suspend fun setWifiQuality(quality: KelpAudioQuality)
    suspend fun setMobileQuality(quality: KelpAudioQuality)
    suspend fun setDownloadQuality(quality: KelpAudioQuality)
    suspend fun setNormalizeVolume(enabled: Boolean)
    suspend fun setAllowExplicitContent(enabled: Boolean)
    suspend fun setContinuousPlayback(enabled: Boolean)
}

class DataStoreTidePreferences(
    private val dataStore: DataStore<Preferences>,
) : KelpPreferences {
    override val playback: Flow<KelpPlaybackPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            KelpPlaybackPreferences(
                wifiQuality = preferences[WifiQualityKey].asQualityOr(KelpAudioQuality.Lossless),
                mobileQuality = preferences[MobileQualityKey].asQualityOr(KelpAudioQuality.High),
                downloadQuality = preferences[DownloadQualityKey].asQualityOr(KelpAudioQuality.Lossless),
                normalizeVolume = preferences[NormalizeVolumeKey] ?: true,
                allowExplicitContent = preferences[AllowExplicitContentKey] ?: true,
                continuousPlayback = preferences[ContinuousPlaybackKey] ?: true,
            )
        }

    override suspend fun setWifiQuality(quality: KelpAudioQuality) {
        dataStore.edit { it[WifiQualityKey] = quality.name }
    }

    override suspend fun setMobileQuality(quality: KelpAudioQuality) {
        dataStore.edit { it[MobileQualityKey] = quality.name }
    }

    override suspend fun setDownloadQuality(quality: KelpAudioQuality) {
        dataStore.edit { it[DownloadQualityKey] = quality.name }
    }

    override suspend fun setNormalizeVolume(enabled: Boolean) {
        dataStore.edit { it[NormalizeVolumeKey] = enabled }
    }

    override suspend fun setAllowExplicitContent(enabled: Boolean) {
        dataStore.edit { it[AllowExplicitContentKey] = enabled }
    }

    override suspend fun setContinuousPlayback(enabled: Boolean) {
        dataStore.edit { it[ContinuousPlaybackKey] = enabled }
    }

    private fun String?.asQualityOr(fallback: KelpAudioQuality): KelpAudioQuality =
        KelpAudioQuality.entries.firstOrNull { it.name == this } ?: fallback

    private companion object {
        val WifiQualityKey = stringPreferencesKey("playback_wifi_quality")
        val MobileQualityKey = stringPreferencesKey("playback_mobile_quality")
        val DownloadQualityKey = stringPreferencesKey("download_quality")
        val NormalizeVolumeKey = booleanPreferencesKey("playback_normalize_volume")
        val AllowExplicitContentKey = booleanPreferencesKey("playback_allow_explicit")
        val ContinuousPlaybackKey = booleanPreferencesKey("playback_continuous")
    }
}
