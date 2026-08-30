package com.loosewire.kelp

import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.ReleaseType
import com.loosewire.kelp.protocol.ServerActivity
import com.loosewire.kelp.protocol.KelpError
import com.loosewire.kelp.protocol.KelpErrorCategory
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
        pendingMore.complete(KelpClientResult.Success(page(release("stale"))))
        pendingRefresh.complete(KelpClientResult.Success(page(release("fresh"))))
        runCurrent()

        assertEquals(listOf("fresh"), viewModel.loaded().page.items.map { it.id })
    }

    @Test
    fun loadMoreFailureKeepsExistingItemsAndCanRetry() = runTest(dispatcher) {
        val error = KelpError(KelpErrorCategory.Network, "Could not reach TIDAL.")
        val client = FakeTideClient().apply {
            enqueueSuccess(page(release("1"), nextCursor = "next"))
            enqueue(KelpClientResult.Failure(error))
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

private class FakeTideClient : KelpClient {
    private val collectionResults = ArrayDeque<suspend () -> KelpClientResult<Page<ReleaseSummary>>>()
    var collectionCalls = 0
        private set

    fun enqueue(result: KelpClientResult<Page<ReleaseSummary>>) {
        collectionResults.addLast { result }
    }

    fun enqueueSuccess(page: Page<ReleaseSummary>) {
        enqueue(KelpClientResult.Success(page))
    }

    fun enqueuePending(): CompletableDeferred<KelpClientResult<Page<ReleaseSummary>>> {
        val result = CompletableDeferred<KelpClientResult<Page<ReleaseSummary>>>()
        collectionResults.addLast { withContext(NonCancellable) { result.await() } }
        return result
    }

    override suspend fun collection(cursor: String?): KelpClientResult<Page<ReleaseSummary>> {
        collectionCalls += 1
        return assertNotNull(collectionResults.removeFirstOrNull()).invoke()
    }

    override suspend fun authSnapshot(): KelpClientResult<AuthSnapshot> = error("Not used")

    override suspend fun loginActivity(): KelpClientResult<ServerActivity> = error("Not used")
}
