package com.loosewire.tide.server

import android.content.Context
import com.loosewire.tide.protocol.TrackSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object RecentTracksStore {
    private const val PreferencesName = "tide_recent_tracks"
    private const val TracksKey = "tracks"
    private const val MaximumTracks = 30
    private val serializer = ListSerializer(TrackSummary.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var context: Context? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            this.context = context.applicationContext
        }
    }

    fun record(track: TrackSummary) = synchronized(lock) {
        val appContext = context ?: return@synchronized
        val updated = (listOf(track) + readLocked(appContext))
            .distinctBy(TrackSummary::id)
            .take(MaximumTracks)
        appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(TracksKey, json.encodeToString(serializer, updated))
            .apply()
    }

    fun tracks(): List<TrackSummary> = synchronized(lock) {
        context?.let(::readLocked).orEmpty()
    }

    private fun readLocked(context: Context): List<TrackSummary> {
        val encoded = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(TracksKey, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, encoded) }.getOrDefault(emptyList())
    }
}
