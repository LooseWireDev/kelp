package com.loosewire.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: TidePreferences,
    private val tideClient: TideClient = BinderTideClient,
) : LightViewModel<Unit>() {
    val playback = preferences.playback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TidePlaybackPreferences(),
    )

    private val _signedOut = MutableStateFlow(false)
    val signedOut = _signedOut.asStateFlow()

    fun signOut() {
        viewModelScope.launch {
            when (val result = tideClient.logout()) {
                is TideClientResult.Success -> _signedOut.value = true
                is TideClientResult.Failure -> Unit
            }
        }
    }

    fun cycleWifiQuality() {
        viewModelScope.launch {
            preferences.setWifiQuality(playback.value.wifiQuality.next())
        }
    }

    fun cycleMobileQuality() {
        viewModelScope.launch {
            preferences.setMobileQuality(playback.value.mobileQuality.next())
        }
    }

    fun cycleDownloadQuality() {
        viewModelScope.launch {
            preferences.setDownloadQuality(playback.value.downloadQuality.next())
        }
    }

    fun toggleNormalizeVolume() {
        viewModelScope.launch {
            preferences.setNormalizeVolume(!playback.value.normalizeVolume)
        }
    }

    fun toggleExplicitContent() {
        viewModelScope.launch {
            preferences.setAllowExplicitContent(!playback.value.allowExplicitContent)
        }
    }

    fun toggleContinuousPlayback() {
        viewModelScope.launch {
            preferences.setContinuousPlayback(!playback.value.continuousPlayback)
        }
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel(): SettingsViewModel = SettingsViewModel(
        DataStoreTidePreferences(lightContext.dataStore),
    )

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val playback by viewModel.playback.collectAsState()
        val signedOut by viewModel.signedOut.collectAsState()

        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Settings"),
                )
                LightScrollView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    SectionLabel("PLAYBACK")
                    SettingRow(
                        label = "Wi-Fi quality",
                        value = "${playback.wifiQuality.label} · ${playback.wifiQuality.detail}",
                        onClick = viewModel::cycleWifiQuality,
                    )
                    SettingRow(
                        label = "Mobile quality",
                        value = "${playback.mobileQuality.label} · ${playback.mobileQuality.detail}",
                        onClick = viewModel::cycleMobileQuality,
                    )
                    SettingRow(
                        label = "Download quality",
                        value = "${playback.downloadQuality.label} · ${playback.downloadQuality.detail}",
                        onClick = viewModel::cycleDownloadQuality,
                    )
                    SettingRow(
                        label = "Normalize volume",
                        value = playback.normalizeVolume.onOffLabel(),
                        onClick = viewModel::toggleNormalizeVolume,
                    )
                    SettingRow(
                        label = "Explicit content",
                        value = if (playback.allowExplicitContent) "Allowed" else "Hidden",
                        onClick = viewModel::toggleExplicitContent,
                    )

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    SectionLabel("BEHAVIOR")
                    SettingRow(
                        label = "Continuous playback",
                        value = if (playback.continuousPlayback) {
                            "On · Keep playing similar songs"
                        } else {
                            "Off · Stop at the end of the queue"
                        },
                        onClick = viewModel::toggleContinuousPlayback,
                    )
                    ReadOnlySettingRow(
                        label = "Background playback",
                        value = "Continue when Tide closes",
                    )
                    ReadOnlySettingRow(
                        label = "Theme",
                        value = "Follow LightOS",
                    )

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    SectionLabel("ACCOUNT")
                    if (signedOut) {
                        ReadOnlySettingRow(label = "TIDAL", value = "Signed out")
                    } else {
                        SettingRow(
                            label = "TIDAL",
                            value = "Connected · tap to sign out",
                            onClick = viewModel::signOut,
                        )
                    }
                    ReadOnlySettingRow(label = "Tide", value = "Version 0.1.0")
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = Modifier.padding(
                top = 1f.gridUnitsAsDp(),
                bottom = 0.25f.gridUnitsAsDp(),
            ),
        )
    }

    @Composable
    private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onClick)
                .padding(
                    end = 1f.gridUnitsAsDp(),
                    top = 0.6f.gridUnitsAsDp(),
                    bottom = 0.6f.gridUnitsAsDp(),
                ),
        ) {
            LightText(text = label, variant = LightTextVariant.Copy)
            LightText(
                text = value,
                variant = LightTextVariant.Fine,
                lighten = true,
            )
        }
    }

    @Composable
    private fun ReadOnlySettingRow(label: String, value: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    end = 1f.gridUnitsAsDp(),
                    top = 0.6f.gridUnitsAsDp(),
                    bottom = 0.6f.gridUnitsAsDp(),
                ),
        ) {
            LightText(text = label, variant = LightTextVariant.Copy)
            LightText(
                text = value,
                variant = LightTextVariant.Fine,
                lighten = true,
            )
        }
    }

    private fun Boolean.onOffLabel(): String = if (this) "On" else "Off"
}
