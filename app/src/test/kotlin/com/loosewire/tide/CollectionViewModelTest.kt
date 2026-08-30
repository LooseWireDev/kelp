package com.loosewire.tide

import com.loosewire.tide.protocol.AuthSnapshot
import com.loosewire.tide.protocol.Page
import com.loosewire.tide.protocol.ReleaseSummary
import com.loosewire.tide.protocol.ReleaseType
import com.loosewire.tide.protocol.ServerActivity
import com.loosewire.tide.protocol.TideError
import com.loosewire.tide.protocol.TideErrorCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeatedLoadIfNeededPreservesLoadedCollection() = runTest(dispatcher) {
        val client = FakeTideClient().apply { enqueueSuccess(page(release("1"))) }
        val viewModel = CollectionViewModel(client)

        viewModel.loadIfNeeded()
        runCurrent()
        viewModel.loadIfNeeded()
        runCurrent()

        assertEquals(1, client.collectionCalls)
        assertEquals(listOf("1"), viewModel.loaded().page.items.map { it.id })
    }

    @Test
    fun supersededLoadMoreCannotOverwriteFreshFirstPage() = runTest(dispatcher) {
        val client = FakeTideClient()
        client.enqueueSuccess(page(release("old"), nextCursor = "next"))
        val pendingMore = client.enqueuePending()
        val pendingRefresh = client.enqueuePending()
        val viewModel = CollectionViewModel(client)
        viewModel.loadFirstPage()
        runCurrent()

        viewModel.loadMore()
        runCurrent()
        viewModel.loadFirstPage()
        runCurrent()
        pendingMore.complete(TideClientResult.Success(page(release("stale"))))
        pendingRefresh.complete(TideClientResult.Success(page(release("fresh"))))
        runCurrent()

        assertEquals(listOf("fresh"), viewModel.loaded().page.items.map { it.id })
    }

    @Test
    fun loadMoreFailureKeepsExistingItemsAndCanRetry() = runTest(dispatcher) {
        val error = TideError(TideErrorCategory.Network, "Could not reach TIDAL.")
        val client = FakeTideClient().apply {
            enqueueSuccess(page(release("1"), nextCursor = "next"))
            enqueue(TideClientResult.Failure(error))
        }
        val viewModel = CollectionViewModel(client)
        viewModel.loadFirstPage()
        runCurrent()

        viewModel.loadMore()
        runCurrent()

        val loaded = viewModel.loaded()
        assertEquals(listOf("1"), loaded.page.items.map { it.id })
        assertEquals(error, loaded.loadMoreError)
        assertFalse(loaded.loadingMore)
    }

    @Test
    fun duplicateItemsAreRemovedAcrossAndWithinPages() = runTest(dispatcher) {
        val client = FakeTideClient().apply {
            enqueueSuccess(page(release("1"), release("1"), nextCursor = "next"))
            enqueueSuccess(page(release("1"), release("2")))
        }
        val viewModel = CollectionViewModel(client)
        viewModel.loadFirstPage()
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertEquals(listOf("1", "2"), viewModel.loaded().page.items.map { it.id })
    }

    private fun CollectionViewModel.loaded(): CollectionViewModel.UiState.Loaded =
        assertIs(state.value)

    private fun page(
        vararg releases: ReleaseSummary,
        nextCursor: String? = null,
    ) = Page(releases.toList(), nextCursor)

    private fun release(id: String) = ReleaseSummary(
        id = id,
        title = "Release $id",
        artistName = "Artist",
        type = ReleaseType.Album,
        itemCount = 1,
    )
}

private class FakeTideClient : TideClient {
    private val collectionResults = ArrayDeque<suspend () -> TideClientResult<Page<ReleaseSummary>>>()
    var collectionCalls = 0
        private set

    fun enqueue(result: TideClientResult<Page<ReleaseSummary>>) {
        collectionResults.addLast { result }
    }

    fun enqueueSuccess(page: Page<ReleaseSummary>) {
        enqueue(TideClientResult.Success(page))
    }

    fun enqueuePending(): CompletableDeferred<TideClientResult<Page<ReleaseSummary>>> {
        val result = CompletableDeferred<TideClientResult<Page<ReleaseSummary>>>()
        collectionResults.addLast { withContext(NonCancellable) { result.await() } }
        return result
    }

    override suspend fun collection(cursor: String?): TideClientResult<Page<ReleaseSummary>> {
        collectionCalls += 1
        return assertNotNull(collectionResults.removeFirstOrNull()).invoke()
    }

    override suspend fun authSnapshot(): TideClientResult<AuthSnapshot> = error("Not used")

    override suspend fun loginActivity(): TideClientResult<ServerActivity> = error("Not used")
}
