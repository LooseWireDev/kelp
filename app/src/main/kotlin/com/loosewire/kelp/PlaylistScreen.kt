package com.loosewire.kelp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.loosewire.kelp.protocol.PlaylistDetail
import com.loosewire.kelp.protocol.PlaylistSummary
import com.loosewire.kelp.protocol.StartPlaybackRequest
import com.loosewire.kelp.protocol.TrackSummary
import com.loosewire.kelp.protocol.TideError
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val playlist: PlaylistSummary,
    private val tideClient: TideClient = BinderTideClient,
    preferences: TidePreferences? = null,
) : LightViewModel<Unit>() {
    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val detail: PlaylistDetail) : UiState
        data class Error(val error: TideError) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()
    private var loaded = false
    private var loadJob: Job? = null
    private val playbackPreferences = preferences?.playback?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TidePlaybackPreferences(),
    )

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!loaded) load()
    }

    fun load() {
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            when (val result = tideClient.playlistDetail(playlist)) {
                is TideClientResult.Success -> {
                    loaded = true
                    _state.value = UiState.Loaded(result.data)
                }
                is TideClientResult.Failure -> _state.value = UiState.Error(result.error)
            }
        }
    }

    fun play(tracks: List<TrackSummary>, index: Int, onStarted: () -> Unit) {
        viewModelScope.launch {
            val result = tideClient.startPlayback(
                StartPlaybackRequest(
                    tracks = tracks,
                    startIndex = index,
                    sourceName = playlist.name,
                    continuousPlayback = playbackPreferences?.value?.continuousPlayback ?: true,
                ),
            )
            if (result is TideClientResult.Success) onStarted()
        }
    }
}

class PlaylistScreen(
    sealedActivity: SealedLightActivity,
    private val playlist: PlaylistSummary,
) : LightScreen<Unit, PlaylistViewModel>(sealedActivity) {
    override val viewModelClass: Class<PlaylistViewModel>
        get() = PlaylistViewModel::class.java

    override fun createViewModel(): PlaylistViewModel = PlaylistViewModel(
        playlist = playlist,
        preferences = DataStoreTidePreferences(lightContext.dataStore),
    )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val colors by LightThemeController.colors.collectAsState()

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
                    center = LightTopBarCenter.Text(playlist.name),
                )
                when (val current = state) {
                    PlaylistViewModel.UiState.Loading -> Message("Loading…")
                    is PlaylistViewModel.UiState.Error -> ErrorMessage(current.error.message)
                    is PlaylistViewModel.UiState.Loaded -> PlaylistContent(current.detail)
                }
            }
        }
    }

    @Composable
    private fun PlaylistContent(detail: PlaylistDetail) {
        LightScrollView(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            detail.playlist.description?.let {
                LightText(
                    text = it,
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier.padding(
                        top = 0.5f.gridUnitsAsDp(),
                        end = 1f.gridUnitsAsDp(),
                    ),
                )
            }
            CatalogSectionLabel("Songs")
            if (detail.tracks.isEmpty()) {
                LightText(text = "No songs.", variant = LightTextVariant.Fine, lighten = true)
            } else {
                detail.tracks.forEachIndexed { index, track ->
                    TrackRow(track) {
                        viewModel.play(detail.tracks, index) { navigateTo(::QueueScreen) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
    }

    @Composable
    private fun ErrorMessage(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .lightClickable(onClick = viewModel::load)
                .padding(horizontal = 2f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "$message\n\nTap to retry.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    }
}
