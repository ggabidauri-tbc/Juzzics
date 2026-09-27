package com.example.juzzics.features.lyrics.domain.model

/** One search result to pick from. */
data class LyricsCandidate(
    val title: String,
    val artist: String,
    /** length of that recording, if known */
    val durationMs: Long?,
    val lyrics: LyricsDomain,
    /** true when its length matches the song's (within a few seconds): very likely the same recording */
    val sameLength: Boolean,
) {
    val isSynced: Boolean get() = lyrics.synced != null
}
