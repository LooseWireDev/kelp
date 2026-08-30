package com.loosewire.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.loosewire.tide.protocol.ArtistReleaseSection
import com.loosewire.tide.protocol.ArtistSummary
import com.loosewire.tide.protocol.ReleaseSummary
import com.loosewire.tide.protocol.StartPlaybackRequest
import com.loosewire.tide.protocol.TideError
import com.loosewire.tide.protocol.TrackSummary
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
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

private enum class ArtistSection(
    val title: String,
    val releaseSection: ArtistReleaseSection? = null,
) {
    Albums("Albums", ArtistReleaseSection.Albums),
    EpsAndSingles("EPs & Singles", ArtistReleaseSection.EpsAndSingles),
    Songs("Songs"),
}

class ArtistViewModel : LightViewModel<Unit>()

class ArtistScreen(
    sealedActivity: SealedLightActivity,
    private val artist: ArtistSummary,
) : LightScreen<Unit, ArtistViewModel>(sealedActivity) {
    override val viewModelClass: Class<ArtistViewModel>
        get() = ArtistViewModel::class.java

    override fun createViewModel(): ArtistViewModel = ArtistViewModel()

    @Composable
    override fun Content() {
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
                    center = LightTopBarCenter.Text(artist.name),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    ArtistSection.entries.forEach { section -> SectionRow(section) }
                }
            }
        }
    }

    @Composable
    private fun SectionRow(section: ArtistSection) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.5f.gridUnitsAsDp())
                .lightClickable {
                    navigateTo(screenFactory = { ArtistSectionScreen(it, artist, section) })
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(text = section.title, variant = LightTextVariant.Subheading)
            LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                size = 1.5f,
                contentDescription = "Open ${section.title}",
            )
        }
    }
}

private class ArtistSectionViewModel(
    private val artist: ArtistSummary,
    private val section: ArtistSection,
    private val tideClient: TideClient = BinderTideClient,
    preferences: TidePreferences? = null,
) : LightViewModel<Unit>() {
    data class UiState(
        val releases: List<ReleaseSummary> = emptyList(),
        val tracks: List<TrackSummary> = emptyList(),
        val nextCursor: String? = null,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val loaded: Boolean = false,
        val error: TideError? = null,
        val loadMoreError: TideError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private val playbackPreferences = preferences?.playback?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TidePlaybackPreferences(),
    )

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!_state.value.loaded) loadFirstPage()
    }

    fun loadFirstPage() {
        requestJob?.cancel()
        _state.value = UiState()
        requestJob = viewModelScope.launch { loadPage(cursor = null, append = false) }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        requestJob?.cancel()
        requestJob = viewModelScope.launch { loadPage(cursor = cursor, append = true) }
    }

    private suspend fun loadPage(cursor: String?, append: Boolean) {
        val before = _state.value
        val releaseSection = section.releaseSection
        if (releaseSection != null) {
            when (val result = tideClient.artistReleases(artist, releaseSection, cursor)) {
                is TideClientResult.Success -> _state.value = UiState(
                    releases = ((if (append) before.releases else emptyList()) + result.data.items)
                        .distinctBy(ReleaseSummary::id),
                    nextCursor = result.data.nextCursor,
                    loading = false,
                    loaded = true,
                )
                is TideClientResult.Failure -> pageFailure(before, result.error, append)
            }
        } else {
            when (val result = tideClient.artistTracks(artist, cursor)) {
                is TideClientResult.Success -> _state.value = UiState(
                    tracks = ((if (append) before.tracks else emptyList()) + result.data.items)
                        .distinctBy(TrackSummary::id),
                    nextCursor = result.data.nextCursor,
                    loading = false,
                    loaded = true,
                )
                is TideClientResult.Failure -> pageFailure(before, result.error, append)
            }
        }
    }

    private fun pageFailure(before: UiState, error: TideError, append: Boolean) {
        _state.value = if (append) {
            before.copy(loadingMore = false, loadMoreError = error)
        } else {
            UiState(loading = false, loaded = true, error = error)
        }
    }

    fun play(index: Int, onStarted: () -> Unit) {
        val tracks = _state.value.tracks
        if (index !in tracks.indices) return
        viewModelScope.launch {
            val result = tideClient.startPlayback(
                StartPlaybackRequest(
                    tracks = tracks,
                    startIndex = index,
                    sourceName = artist.name,
                    continuousPlayback = playbackPreferences?.value?.continuousPlayback ?: true,
                ),
            )
            if (result is TideClientResult.Success) onStarted()
        }
    }
}

private class ArtistSectionScreen(
    sealedActivity: SealedLightActivity,
    private val artist: ArtistSummary,
    private val section: ArtistSection,
) : LightScreen<Unit, ArtistSectionViewModel>(sealedActivity) {
    override val viewModelClass: Class<ArtistSectionViewModel>
        get() = ArtistSectionViewModel::class.java

    override fun createViewModel(): ArtistSectionViewModel = ArtistSectionViewModel(
        artist = artist,
        section = section,
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
                    center = LightTopBarCenter.Text(section.title),
                )
                ArtistSectionContent(state)
            }
        }
    }

    @Composable
    private fun ArtistSectionContent(state: ArtistSectionViewModel.UiState) {
        when {
            state.loading -> Message("Loading…")
            state.error != null -> Message("${state.error.message}\n\nTap to retry.", viewModel::loadFirstPage)
            state.releases.isEmpty() && state.tracks.isEmpty() && state.nextCursor == null ->
                Message("No ${section.title.lowercase()} available.")
            else -> LightLazyScrollView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 1f.gridUnitsAsDp()),
                uniformItemHeightGridUnits = 5f,
            ) {
                items(state.releases, key = { "release-${it.id}" }) { release ->
                    ReleaseRow(release) {
                        navigateTo(screenFactory = { AlbumScreen(it, release) })
                    }
                }
                items(state.tracks, key = { "track-${it.id}" }) { track ->
                    TrackRow(track) {
                        viewModel.play(state.tracks.indexOfFirst { it.id == track.id }) {
                            navigateTo(::QueueScreen)
                        }
                    }
                }
                if (state.nextCursor != null) {
                    item(key = "load-more") { LoadMoreRow(state) }
                }
            }
        }
    }

    @Composable
    private fun LoadMoreRow(state: ArtistSectionViewModel.UiState) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5f.gridUnitsAsDp())
                .lightClickable(onClick = viewModel::loadMore),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = when {
                    state.loadingMore -> "Loading…"
                    state.loadMoreError != null -> "Could not load more — retry"
                    else -> "Load more"
                },
                variant = LightTextVariant.Copy,
            )
        }
    }

    @Composable
    private fun Message(message: String, onClick: (() -> Unit)? = null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { if (onClick == null) it else it.lightClickable(onClick = onClick) }
                .padding(horizontal = 2f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
    }
}
