package com.example.juzzics.common.songs

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A better name for a song, shown instead of its file's title / artist tags. */
data class SongName(val title: String, val artist: String)

/** A name found online for a song whose own name looks messy (e.g. a YouTube title). */
data class NameSuggestion(
    val songId: Long,
    val currentTitle: String,
    val currentArtist: String,
    val suggested: SongName,
)

/**
 * Small per-song settings, kept apart from the song files (which aren't changed):
 * - lyrics timing offset (when synced lyrics run a bit early / late)
 * - fixed names, and names suggested by lyrics lookups
 *
 * Stored as JSON in SharedPreferences: a few small maps, no database migration needed.
 */
class SongSettings(context: Context) {
    private val prefs = context.getSharedPreferences("song_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _lyricsOffsets = MutableStateFlow(load<Map<Long, Long>>(KEY_OFFSETS) ?: emptyMap())
    /** song id to milliseconds: positive shows lyrics later, negative earlier */
    val lyricsOffsets: StateFlow<Map<Long, Long>> = _lyricsOffsets.asStateFlow()

    private val _names = MutableStateFlow(load<Map<Long, SongName>>(KEY_NAMES) ?: emptyMap())
    /** song id to the name shown instead of its tags */
    val names: StateFlow<Map<Long, SongName>> = _names.asStateFlow()

    private val _suggestions = MutableStateFlow(load<Map<Long, NameSuggestion>>(KEY_SUGGESTIONS) ?: emptyMap())
    /** song id to a suggested better name, waiting for the user to accept or dismiss it */
    val suggestions: StateFlow<Map<Long, NameSuggestion>> = _suggestions.asStateFlow()

    fun setLyricsOffset(songId: Long, offsetMs: Long) {
        _lyricsOffsets.update { if (offsetMs == 0L) it - songId else it + (songId to offsetMs) }
        save(KEY_OFFSETS, _lyricsOffsets.value)
    }

    fun rename(songId: Long, name: SongName) {
        _names.update { it + (songId to name) }
        save(KEY_NAMES, _names.value)
        dismissSuggestion(songId)
    }

    /** back to the name from the file's tags */
    fun resetName(songId: Long) {
        _names.update { it - songId }
        save(KEY_NAMES, _names.value)
    }

    fun suggest(suggestion: NameSuggestion) {
        // already renamed, or dismissed before: don't ask again
        if (suggestion.songId in _names.value || suggestion.songId in dismissed) return
        _suggestions.update { it + (suggestion.songId to suggestion) }
        save(KEY_SUGGESTIONS, _suggestions.value)
    }

    fun dismissSuggestion(songId: Long) {
        if (songId !in _suggestions.value) return
        _suggestions.update { it - songId }
        save(KEY_SUGGESTIONS, _suggestions.value)
        dismissed += songId
        save(KEY_DISMISSED, dismissed)
    }

    /** songs whose suggestion was dismissed (or accepted) */
    private val dismissed: MutableSet<Long> = (load<Set<Long>>(KEY_DISMISSED) ?: emptySet()).toMutableSet()

    private inline fun <reified T> load(key: String): T? = runCatching {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) }
    }.getOrNull()

    private fun save(key: String, value: Any) = prefs.edit { putString(key, gson.toJson(value)) }

    private companion object {
        const val KEY_OFFSETS = "lyrics_offsets"
        const val KEY_NAMES = "names"
        const val KEY_SUGGESTIONS = "name_suggestions"
        const val KEY_DISMISSED = "dismissed_suggestions"
    }
}
