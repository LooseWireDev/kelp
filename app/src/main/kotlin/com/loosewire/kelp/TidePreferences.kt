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

enum class TideAudioQuality(
    val label: String,
    val detail: String,
) {
    Low("Low", "Data saver"),
    High("High", "320 kbps"),
    Lossless("Lossless", "CD quality"),
    HiResLossless("Max", "Hi-res lossless"),
    ;

    fun next(): TideAudioQuality = entries[(ordinal + 1) % entries.size]
}

data class TidePlaybackPreferences(
    val wifiQuality: TideAudioQuality = TideAudioQuality.Lossless,
    val mobileQuality: TideAudioQuality = TideAudioQuality.High,
    val downloadQuality: TideAudioQuality = TideAudioQuality.Lossless,
    val normalizeVolume: Boolean = true,
    val allowExplicitContent: Boolean = true,
    val continuousPlayback: Boolean = true,
)

interface TidePreferences {
    val playback: Flow<TidePlaybackPreferences>

    suspend fun setWifiQuality(quality: TideAudioQuality)
    suspend fun setMobileQuality(quality: TideAudioQuality)
    suspend fun setDownloadQuality(quality: TideAudioQuality)
    suspend fun setNormalizeVolume(enabled: Boolean)
    suspend fun setAllowExplicitContent(enabled: Boolean)
    suspend fun setContinuousPlayback(enabled: Boolean)
}

class DataStoreTidePreferences(
    private val dataStore: DataStore<Preferences>,
) : TidePreferences {
    override val playback: Flow<TidePlaybackPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            TidePlaybackPreferences(
                wifiQuality = preferences[WifiQualityKey].asQualityOr(TideAudioQuality.Lossless),
                mobileQuality = preferences[MobileQualityKey].asQualityOr(TideAudioQuality.High),
                downloadQuality = preferences[DownloadQualityKey].asQualityOr(TideAudioQuality.Lossless),
                normalizeVolume = preferences[NormalizeVolumeKey] ?: true,
                allowExplicitContent = preferences[AllowExplicitContentKey] ?: true,
                continuousPlayback = preferences[ContinuousPlaybackKey] ?: true,
            )
        }

    override suspend fun setWifiQuality(quality: TideAudioQuality) {
        dataStore.edit { it[WifiQualityKey] = quality.name }
    }

    override suspend fun setMobileQuality(quality: TideAudioQuality) {
        dataStore.edit { it[MobileQualityKey] = quality.name }
    }

    override suspend fun setDownloadQuality(quality: TideAudioQuality) {
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

    private fun String?.asQualityOr(fallback: TideAudioQuality): TideAudioQuality =
        TideAudioQuality.entries.firstOrNull { it.name == this } ?: fallback

    private companion object {
        val WifiQualityKey = stringPreferencesKey("playback_wifi_quality")
        val MobileQualityKey = stringPreferencesKey("playback_mobile_quality")
        val DownloadQualityKey = stringPreferencesKey("download_quality")
        val NormalizeVolumeKey = booleanPreferencesKey("playback_normalize_volume")
        val AllowExplicitContentKey = booleanPreferencesKey("playback_allow_explicit")
        val ContinuousPlaybackKey = booleanPreferencesKey("playback_continuous")
    }
}
