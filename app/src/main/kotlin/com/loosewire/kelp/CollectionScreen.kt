package com.loosewire.kelp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.ReleaseType
import com.loosewire.kelp.protocol.TideError
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CollectionViewModel(
    private val tideClient: TideClient = BinderTideClient,
) : LightViewModel<Unit>() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(
            val page: Page<ReleaseSummary>,
            val loadingMore: Boolean,
            val loadMoreError: TideError? = null,
        ) : UiState
        data class Error(val error: TideError) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()
    private var hasRequestedFirstPage = false
    private var requestGeneration = 0L
    private var requestJob: Job? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        loadIfNeeded()
    }

    fun loadIfNeeded() {
        if (!hasRequestedFirstPage) loadFirstPage()
    }

    fun loadFirstPage() {
        hasRequestedFirstPage = true
        val generation = ++requestGeneration
        requestJob?.cancel()
        _state.value = UiState.Loading
        requestJob = viewModelScope.launch {
            when (val result = tideClient.collection()) {
                is TideClientResult.Success -> if (generation == requestGeneration) {
                    _state.value = UiState.Loaded(
                        result.data.withDistinctItems(),
                        loadingMore = false,
                    )
                }
                is TideClientResult.Failure -> if (generation == requestGeneration) {
                    _state.value = UiState.Error(result.error)
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? UiState.Loaded ?: return
        if (current.loadingMore || !current.page.hasMore) return

        val generation = ++requestGeneration
        requestJob?.cancel()
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        requestJob = viewModelScope.launch {
            when (val result = tideClient.collection(current.page.nextCursor)) {
                is TideClientResult.Success -> if (generation == requestGeneration) {
                    _state.value = UiState.Loaded(
                        page = Page(
                            items = (current.page.items + result.data.items).distinctBy { it.id },
                            nextCursor = result.data.nextCursor,
                        ),
                        loadingMore = false,
                    )
                }
                is TideClientResult.Failure -> if (generation == requestGeneration) {
                    _state.value = current.copy(
                        loadingMore = false,
                        loadMoreError = result.error,
                    )
                }
            }
        }
    }

    private fun Page<ReleaseSummary>.withDistinctItems(): Page<ReleaseSummary> =
        copy(items = items.distinctBy { it.id })
}

class CollectionScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, CollectionViewModel>(sealedActivity) {

    override val viewModelClass: Class<CollectionViewModel>
        get() = CollectionViewModel::class.java

    override fun createViewModel(): CollectionViewModel = CollectionViewModel()

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
                    center = LightTopBarCenter.Text("My Collection"),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (val s = state) {
                        is CollectionViewModel.UiState.Loading -> LoadingState()
                        is CollectionViewModel.UiState.Error -> ErrorState(
                            message = s.error.message,
                            onRetry = { viewModel.loadFirstPage() },
                        )
                        is CollectionViewModel.UiState.Loaded -> ReleaseList(s)
                    }
                }
            }
        }
    }

    @Composable
    private fun LoadingState() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "Loading…",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    }

    @Composable
    private fun ErrorState(message: String, onRetry: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "$message\n\nTap to retry.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
            )
        }
    }

    @Composable
    private fun ReleaseList(s: CollectionViewModel.UiState.Loaded) {
        if (s.page.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Your collection is empty.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
            return
        }

        LightLazyScrollView(
            modifier = Modifier.fillMaxSize(),
            uniformItemHeightGridUnits = 5f,
        ) {
            items(s.page.items, key = { it.id }) { release ->
                ReleaseRow(release)
            }
            if (s.page.hasMore) {
                item(key = "load-more") {
                    LoadMoreRow(
                        loading = s.loadingMore,
                        failed = s.loadMoreError != null,
                        onClick = { viewModel.loadMore() },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReleaseRow(release: ReleaseSummary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    navigateTo(screenFactory = { AlbumScreen(it, release) })
                }
                .padding(
                    start = 0.5f.gridUnitsAsDp(),
                    end = 0.5f.gridUnitsAsDp(),
                    top = 14.dp,
                    bottom = 14.dp,
                ),
        ) {
            LightText(
                text = release.title,
                variant = LightTextVariant.Heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
            LightText(
                text = "${release.artistName} · ${release.typeLabel()} · ${release.itemCount} tracks",
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun LoadMoreRow(loading: Boolean, failed: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5f.gridUnitsAsDp())
                .clickable(enabled = !loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = when {
                    loading -> "Loading…"
                    failed -> "Could not load more — retry"
                    else -> "Load more"
                },
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    }

    private fun ReleaseSummary.typeLabel(): String = when (type) {
        ReleaseType.Album -> "Album"
        ReleaseType.Ep -> "EP"
        ReleaseType.Single -> "Single"
    }
}
