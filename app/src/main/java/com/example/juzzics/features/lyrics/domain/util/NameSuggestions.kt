package com.example.juzzics.features.lyrics.domain.util

import com.example.juzzics.common.songs.NameSuggestion
import com.example.juzzics.common.songs.SongName
import com.example.juzzics.features.lyrics.domain.model.LyricsMatch

/**
 * A better name for a song, from the song its lyrics were found for, or null when the lookup
 * wasn't sure enough or the song's name is already the same.
 */
fun suggestName(songId: Long, currentTitle: String, currentArtist: String, match: LyricsMatch?): NameSuggestion? {
    if (match == null || !match.confident || match.title.isBlank()) return null
    val sameTitle = simple(match.title) == simple(currentTitle)
    val sameArtist = match.artist.isBlank() || simple(match.artist) == simple(currentArtist)
    if (sameTitle && sameArtist) return null
    return NameSuggestion(
        songId = songId,
        currentTitle = currentTitle,
        currentArtist = currentArtist,
        suggested = SongName(title = match.title.trim(), artist = match.artist.trim()),
    )
}

/** lower case letters and digits only, so "Song (feat. X)" and "song feat x" compare equal */
private fun simple(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }
