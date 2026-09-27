package com.example.juzzics.features.player

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Remembers the queue, current song and position, so after the app was closed the player
 * comes back where you left off (paused).
 */
class PlaybackMemory(context: Context) {

    data class Saved(
        val queue: List<MusicFileDomain>,
        val index: Int,
        val positionMs: Long,
        val source: String?,
    )

    private data class SavedSong(
        val id: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long,
        val icon: String,
        val dateAdded: Long,
    )

    private val prefs = context.getSharedPreferences("playback_memory", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveQueue(queue: List<MusicFileDomain>, source: String?) {
        val songs = queue.map { SavedSong(it.id, it.title, it.artist, it.data, it.duration, it.icon.toString(), it.dateAdded) }
        prefs.edit {
            putString(KEY_QUEUE, gson.toJson(songs))
            putString(KEY_SOURCE, source)
        }
    }

    fun savePosition(index: Int, positionMs: Long) {
        prefs.edit {
            putInt(KEY_INDEX, index)
            putLong(KEY_POSITION, positionMs)
        }
    }

    fun load(): Saved? = runCatching {
        val json = prefs.getString(KEY_QUEUE, null) ?: return null
        val type = object : TypeToken<List<SavedSong>>() {}.type
        val songs: List<SavedSong> = gson.fromJson(json, type) ?: return null
        if (songs.isEmpty()) return null
        Saved(
            queue = songs.map {
                MusicFileDomain(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    data = it.album,
                    duration = it.duration,
                    icon = it.icon.toUri(),
                    dateAdded = it.dateAdded,
                )
            },
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(songs.indices),
            positionMs = prefs.getLong(KEY_POSITION, 0).coerceAtLeast(0),
            source = prefs.getString(KEY_SOURCE, null),
        )
    }.getOrNull()

    private companion object {
        const val KEY_QUEUE = "queue"
        const val KEY_SOURCE = "source"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
    }
}
