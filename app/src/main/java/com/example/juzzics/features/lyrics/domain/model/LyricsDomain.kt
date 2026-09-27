package com.example.juzzics.features.lyrics.domain.model

/**
 * @param lyrics plain text
 * @param synced LRC text ("[01:23.45] line") when time-synced lyrics are known, see [parseLrc]
 * @param match the song these lyrics were found for online (not saved, only for name suggestions)
 */
data class LyricsDomain(
    val lyrics: String,
    val synced: String? = null,
    val match: LyricsMatch? = null,
)

/**
 * The song online lyrics belong to.
 * [confident]: same length as our song and a similar name, so its name can be suggested.
 */
data class LyricsMatch(val title: String, val artist: String, val confident: Boolean)
