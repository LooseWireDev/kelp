package com.loosewire.tide.server

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.loosewire.tide.protocol.PlaybackSnapshot
import com.loosewire.tide.protocol.PlayerCommand
import com.loosewire.tide.protocol.RepeatMode
import com.loosewire.tide.protocol.StartPlaybackRequest
import com.loosewire.tide.protocol.TrackSummary
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Queue and playback state for Tide. Streams resolve via [TidalStreamResolver]
 * (first-party playbackinfo) and play through a Media3 [ExoPlayer] — Tide owns
 * the player because the official TIDAL Player module only plays 30-second
 * previews for unapproved developer apps (phono runs the same recipe).
 *
 * Threading: binder RPCs arrive on arbitrary threads. Queue/queue-index state
 * lives under [stateLock]; ExoPlayer calls are marshaled to the main thread
 * via [onPlayer], whose blocking-post must never run while [stateLock] is
 * held (the main-thread block briefly takes it itself). Playback liveness
 * (isPlaying/position/duration) lives in @Volatile fields maintained by the
 * player listener and a main-thread ticker.
 */
@UnstableApi
@SuppressLint("UnsafeOptInUsageError")
internal class TidePlayerController(
    context: Context,
    streamingAuth: TidalStreamingAuth,
    private val continuousLoader: suspend (TrackSummary) -> List<TrackSummary>,
) {
    private val appContext = context.applicationContext
    private val resolver = TidalStreamResolver(streamingAuth)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logger = Logger.getLogger(TidePlayerController::class.java.name)

    @Volatile
    private var player: ExoPlayer? = null

    private val stateLock = Any()
    private var queue = emptyList<TrackSummary>()
    private var currentIndex = -1
    private var sourceName: String? = null
    private var shuffle = false
    private var repeatMode = RepeatMode.Off
    private var continuousPlayback = true
    private var loadGeneration = 0
    private var rescuedGeneration = 0

    @Volatile
    private var isPlaying = false

    @Volatile
    private var positionMs = 0L

    @Volatile
    private var durationMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                // REPEAT_MODE_ONE loops inside ExoPlayer, so ENDED means "queue step".
                Player.STATE_ENDED -> scope.launch { advance() }
                Player.STATE_IDLE -> isPlaying = false
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            val detail = (error.message ?: error.javaClass.simpleName).take(200)
            logger.warning("TIDAL playback error ${error.errorCodeName}: $detail")
            val step = synchronized(stateLock) {
                queue.getOrNull(currentIndex)?.let { it to loadGeneration }
            } ?: return
            if (rescuedGeneration == step.second) {
                isPlaying = false
                return
            }
            // Signed CDN URLs expire; a stale resolve is the common failure,
            // so re-resolve the current track once before giving up.
            rescuedGeneration = step.second
            beginLoad(step.first, step.second, play = true)
        }
    }

    /** Keeps snapshot positions moving between player callbacks. */
    private val positionTicker = object : Runnable {
        override fun run() {
            try {
                player?.let { p ->
                    if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING) {
                        positionMs = p.currentPosition.coerceAtLeast(0L)
                        p.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
                    }
                }
            } finally {
                mainHandler.postDelayed(this, POSITION_POLL_MS)
            }
        }
    }

    // --- public API (binder threads) ------------------------------------------

    fun snapshot(): PlaybackSnapshot = synchronized(stateLock) { snapshotLocked() }

    fun start(request: StartPlaybackRequest): PlaybackSnapshot {
        require(request.tracks.isNotEmpty()) { "Playback needs at least one song" }
        require(request.startIndex in request.tracks.indices) { "Invalid starting song" }
        val step = synchronized(stateLock) {
            queue = request.tracks.distinctBy { it.id }
            val requestedId = request.tracks[request.startIndex].id
            currentIndex = queue.indexOfFirst { it.id == requestedId }.coerceAtLeast(0)
            sourceName = request.sourceName
            continuousPlayback = request.continuousPlayback
            isPlaying = true
            val generation = ++loadGeneration
            queue[currentIndex] to generation
        }
        beginLoad(step.first, step.second, play = true)
        return snapshot()
    }

    suspend fun control(command: PlayerCommand, positionMs: Long? = null): PlaybackSnapshot {
        when (command) {
            PlayerCommand.TogglePlayPause -> togglePlayPause()
            PlayerCommand.Previous -> previous()
            PlayerCommand.Next -> advance()
            PlayerCommand.ToggleShuffle -> toggleShuffle()
            PlayerCommand.CycleRepeat -> cycleRepeat()
            PlayerCommand.Seek -> seek(positionMs)
        }
        return snapshot()
    }

    /** Stop playback and release the ExoPlayer instance (sign out / full reset). */
    fun release() {
        synchronized(stateLock) {
            loadGeneration += 1
            queue = emptyList()
            currentIndex = -1
            sourceName = null
            isPlaying = false
            positionMs = 0L
            durationMs = 0L
        }
        mainHandler.post {
            mainHandler.removeCallbacks(positionTicker)
            player?.release()
            player = null
        }
    }

    // --- commands ----------------------------------------------------------------

    private fun togglePlayPause() {
        onPlayer { p ->
            if (p.isPlaying) p.pause() else if (p.playbackState != Player.STATE_IDLE) p.play()
        }
    }

    private fun previous() {
        val restartPositionMs = onPlayer { it.currentPosition }
        val step = synchronized(stateLock) {
            if (queue.isEmpty()) return@synchronized null
            if (restartPositionMs > PreviousRestartThresholdMs || currentIndex <= 0) {
                null
            } else {
                currentIndex -= 1
                val generation = ++loadGeneration
                queue[currentIndex] to generation
            }
        }
        if (step == null) {
            seek(0L)
        } else {
            beginLoad(step.first, step.second, play = true)
        }
    }

    private suspend fun advance() {
        val needsMore = synchronized(stateLock) {
            queue.isNotEmpty() && currentIndex >= queue.lastIndex &&
                repeatMode == RepeatMode.Off && continuousPlayback
        }
        if (needsMore) appendContinuousTracks()

        val step = synchronized(stateLock) {
            if (queue.isEmpty()) return@synchronized null
            when {
                currentIndex < queue.lastIndex -> currentIndex += 1
                repeatMode == RepeatMode.All -> currentIndex = 0
                repeatMode == RepeatMode.One -> Unit
                else -> {
                    isPlaying = false
                    return@synchronized null
                }
            }
            val generation = ++loadGeneration
            queue[currentIndex] to generation
        }
        if (step == null) {
            onPlayer { it.pause() }
            return
        }
        isPlaying = true
        beginLoad(step.first, step.second, play = true)
    }

    private suspend fun appendContinuousTracks() {
        val seed = synchronized(stateLock) { queue.getOrNull(currentIndex) } ?: return
        val additions = continuousLoader(seed)
        synchronized(stateLock) {
            val existing = queue.asSequence().map(TrackSummary::id).toHashSet()
            queue = queue + additions.filter { existing.add(it.id) }
        }
    }

    private fun seek(positionMs: Long?) {
        val targetMs = positionMs?.coerceAtLeast(0L) ?: return
        val duration = durationMs
        if (duration <= 0L) return
        val clamped = targetMs.coerceAtMost(duration)
        onPlayer { p ->
            if (p.playbackState != Player.STATE_IDLE) p.seekTo(clamped)
        }
        this.positionMs = clamped
    }

    private fun toggleShuffle() = synchronized(stateLock) {
        val current = queue.getOrNull(currentIndex) ?: return@synchronized
        shuffle = !shuffle
        if (shuffle) {
            queue = listOf(current) + queue.filterNot { it.id == current.id }.shuffled()
            currentIndex = 0
        }
    }

    private fun cycleRepeat() {
        val next = synchronized(stateLock) {
            repeatMode = when (repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            }
            repeatMode
        }
        onPlayer { p ->
            p.repeatMode = if (next == RepeatMode.One) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    // --- loading ------------------------------------------------------------------

    /**
     * Resolve [track] on IO, then install it on the player on the main thread.
     * A stale [generation] (user already moved on) short-circuits both phases.
     */
    private fun beginLoad(track: TrackSummary, generation: Int, play: Boolean) {
        RecentTracksStore.record(track)
        scope.launch {
            val mediaItem = try {
                resolver.resolveMediaItem(appContext, track)
            } catch (error: Exception) {
                logger.warning(
                    "TIDAL stream resolve failed for ${track.id}: ${error.javaClass.simpleName}: ${error.message?.take(200)}",
                )
                synchronized(stateLock) {
                    if (generation == loadGeneration) isPlaying = false
                }
                return@launch
            }
            if (synchronized(stateLock) { generation != loadGeneration }) return@launch
            onPlayer { p ->
                if (synchronized(stateLock) { generation != loadGeneration }) return@onPlayer
                positionMs = 0L
                durationMs = track.durationMs
                p.setMediaItem(mediaItem)
                p.prepare()
                if (play) p.play() else p.pause()
            }
        }
    }

    // --- ExoPlayer main-thread bridge ----------------------------------------------

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 90_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                )
                .build(),
        )
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also {
            it.addListener(playerListener)
            mainHandler.postDelayed(positionTicker, POSITION_POLL_MS)
        }

    /** Run [block] on the main thread against the (lazily built) player. */
    private fun <T> onPlayer(block: (ExoPlayer) -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block(player ?: buildPlayer().also { player = it })
        }
        val future = CompletableFuture<Result<T>>()
        mainHandler.post {
            future.complete(runCatching { block(player ?: buildPlayer().also { player = it }) })
        }
        return future.get(ON_PLAYER_TIMEOUT_MS, TimeUnit.MILLISECONDS).getOrThrow()
    }

    // --- snapshot --------------------------------------------------------------------

    private fun snapshotLocked(): PlaybackSnapshot = PlaybackSnapshot(
        current = queue.getOrNull(currentIndex),
        sourceName = sourceName,
        queue = queue,
        currentIndex = currentIndex,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        shuffle = shuffle,
        repeatMode = repeatMode,
    )

    private companion object {
        const val ON_PLAYER_TIMEOUT_MS = 5_000L
        const val POSITION_POLL_MS = 500L
        const val PreviousRestartThresholdMs = 5_000L
    }
}
