package com.loosewire.kelp

import com.loosewire.kelp.protocol.PlaybackSnapshot
import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.Page
import com.loosewire.kelp.protocol.ReleaseSummary
import com.loosewire.kelp.protocol.ServerActivity
import com.loosewire.kelp.protocol.TrackSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
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
    fun seekUsesActualPlaybackDurationInsteadOfCatalogDuration() = runTest(dispatcher) {
        val client = PlayerFakeTideClient(
            PlaybackSnapshot(
                current = TrackSummary(
                    id = "track",
                    title = "Track",
                    artistName = "Artist",
                    durationMs = 240_000L,
                    explicit = false,
                ),
                durationMs = 30_000L,
            ),
        )
        val viewModel = PlayerViewModel(client)
        viewModel.refresh()
        runCurrent()

        viewModel.seekTo(0.5f)
        advanceTimeBy(121L)
        runCurrent()

        assertEquals(15_000L, client.lastSeekPositionMs)
    }
}

private class PlayerFakeTideClient(
    private val playback: PlaybackSnapshot,
) : TideClient {
    var lastSeekPositionMs: Long? = null
        private set

    override suspend fun playback(): TideClientResult<PlaybackSnapshot> =
        TideClientResult.Success(playback)

    override suspend fun authSnapshot(): TideClientResult<AuthSnapshot> = error("Not used")

    override suspend fun loginActivity(): TideClientResult<ServerActivity> = error("Not used")

    override suspend fun collection(cursor: String?): TideClientResult<Page<ReleaseSummary>> =
        error("Not used")

    override suspend fun seekPlayback(positionMs: Long): TideClientResult<PlaybackSnapshot> {
        lastSeekPositionMs = positionMs
        return TideClientResult.Success(playback.copy(positionMs = positionMs))
    }
}
