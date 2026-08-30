package com.loosewire.kelp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KelpPreferencesTest {
    @Test
    fun qualityCyclesThroughTidalLevels() {
        assertEquals(KelpAudioQuality.High, KelpAudioQuality.Low.next())
        assertEquals(KelpAudioQuality.Lossless, KelpAudioQuality.High.next())
        assertEquals(KelpAudioQuality.HiResLossless, KelpAudioQuality.Lossless.next())
        assertEquals(KelpAudioQuality.Low, KelpAudioQuality.HiResLossless.next())
    }

    @Test
    fun defaultsFavorQualityWithoutSpendingMobileData() {
        val preferences = KelpPlaybackPreferences()

        assertEquals(KelpAudioQuality.Lossless, preferences.wifiQuality)
        assertEquals(KelpAudioQuality.High, preferences.mobileQuality)
        assertEquals(KelpAudioQuality.Lossless, preferences.downloadQuality)
        assertTrue(preferences.normalizeVolume)
        assertTrue(preferences.allowExplicitContent)
        assertTrue(preferences.continuousPlayback)
    }
}
