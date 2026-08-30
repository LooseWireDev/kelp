package com.loosewire.kelp

import com.loosewire.kelp.protocol.ArtistDetail
import com.loosewire.kelp.protocol.ArtistSummary
import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.AuthState
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.HomeFeed
import com.loosewire.kelp.protocol.PlaylistSummary
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.ReleaseType
import com.loosewire.kelp.protocol.SearchResults
import com.loosewire.kelp.protocol.ServerActivity
import com.loosewire.kelp.protocol.TrackSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun authenticatedRefreshLoadsHomeMixesAndPlaylistsByDefault() = runTest(dispatcher) {
        val feed = HomeFeed(
            mixes = listOf(PlaylistSummary("mix", "My Mix 1")),
            playlists = listOf(PlaylistSummary("playlist", "Favorites")),
        )
        val client = HomeFakeTideClient(
            auth = AuthSnapshot(AuthState.Authenticated),
            homeFeed = feed,
        )
        val viewModel = HomeViewModel(client)

        viewModel.refreshAuth()
        runCurrent()

        assertEquals(TideTab.Home, viewModel.state.value.selectedTab)
        assertEquals(feed, viewModel.state.value.home.items.single())
        assertFalse(viewModel.state.value.home.loading)
        assertEquals(1, client.homeCalls)
    }

    @Test
    fun selectingAlbumsLoadsAlbumTabOnce() = runTest(dispatcher) {
        val client = HomeFakeTideClient(
            auth = AuthSnapshot(AuthState.Authenticated),
            savedAlbums = listOf(release("album")),
        )
        val viewModel = HomeViewModel(client)
        viewModel.refreshAuth()
        runCurrent()

        viewModel.selectTab(TideTab.Albums)
        runCurrent()
        viewModel.selectTab(TideTab.Home)
        viewModel.selectTab(TideTab.Albums)
        runCurrent()

        assertEquals(1, client.albumCalls)
        assertEquals(listOf("album"), viewModel.state.value.albums.items.map { it.id })
    }

    @Test
    fun searchQueryIsTrimmedBeforeShowingResultSections() = runTest(dispatcher) {
        val viewModel = SearchViewModel()

        viewModel.setQuery("  Jon Batiste  ")

        assertEquals("Jon Batiste", viewModel.query.value)
    }

    @Test
    fun unauthenticatedRefreshDoesNotLoadCatalog() = runTest(dispatcher) {
        val client = HomeFakeTideClient(auth = AuthSnapshot(AuthState.Unauthenticated))
        val viewModel = HomeViewModel(client)

        viewModel.refreshAuth()
        runCurrent()

        assertEquals(0, client.artistCalls)
        assertEquals(0, client.albumCalls)
    }

    private fun release(id: String) = ReleaseSummary(
        id = id,
        title = "Release $id",
        artistName = "Artist",
        type = ReleaseType.Album,
        itemCount = 1,
    )
}

private class HomeFakeTideClient(
    private val auth: AuthSnapshot,
    private val savedArtists: List<ArtistSummary> = emptyList(),
    private val savedAlbums: List<ReleaseSummary> = emptyList(),
    private val savedTracks: List<TrackSummary> = emptyList(),
    private val homeFeed: HomeFeed = HomeFeed(emptyList(), emptyList()),
    private val searchResult: SearchResults = SearchResults(emptyList(), emptyList(), emptyList()),
) : TideClient {
    var artistCalls = 0
        private set
    var albumCalls = 0
        private set
    var searchCalls = 0
        private set
    var homeCalls = 0
        private set

    override suspend fun authSnapshot(): TideClientResult<AuthSnapshot> =
        TideClientResult.Success(auth)

    override suspend fun loginActivity(): TideClientResult<ServerActivity> =
        TideClientResult.Success(ServerActivity("com.loosewire.kelp/.server.LoginActivity"))

    override suspend fun collection(cursor: String?): TideClientResult<Page<ReleaseSummary>> {
        albumCalls += 1
        return TideClientResult.Success(Page(savedAlbums, nextCursor = null))
    }

    override suspend fun artists(cursor: String?): TideClientResult<Page<ArtistSummary>> {
        artistCalls += 1
        return TideClientResult.Success(Page(savedArtists, nextCursor = null))
    }

    override suspend fun tracks(cursor: String?): TideClientResult<Page<TrackSummary>> =
        TideClientResult.Success(Page(savedTracks, nextCursor = null))

    override suspend fun home(): TideClientResult<HomeFeed> {
        homeCalls += 1
        return TideClientResult.Success(homeFeed)
    }

    override suspend fun search(query: String): TideClientResult<SearchResults> {
        searchCalls += 1
        return TideClientResult.Success(searchResult)
    }

    override suspend fun artistDetail(artist: ArtistSummary): TideClientResult<ArtistDetail> =
        TideClientResult.Success(ArtistDetail(artist, emptyList(), emptyList()))
}
