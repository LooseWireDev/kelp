package com.loosewire.tide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TidePreferencesTest {
    @Test
    fun qualityCyclesThroughTidalLevels() {
        assertEquals(TideAudioQuality.High, TideAudioQuality.Low.next())
        assertEquals(TideAudioQuality.Lossless, TideAudioQuality.High.next())
        assertEquals(TideAudioQuality.HiResLossless, TideAudioQuality.Lossless.next())
        assertEquals(TideAudioQuality.Low, TideAudioQuality.HiResLossless.next())
    }

    @Test
    fun defaultsFavorQualityWithoutSpendingMobileData() {
        val preferences = TidePlaybackPreferences()

        assertEquals(TideAudioQuality.Lossless, preferences.wifiQuality)
        assertEquals(TideAudioQuality.High, preferences.mobileQuality)
        assertEquals(TideAudioQuality.Lossless, preferences.downloadQuality)
        assertTrue(preferences.normalizeVolume)
        assertTrue(preferences.allowExplicitContent)
        assertTrue(preferences.continuousPlayback)
    }
}
