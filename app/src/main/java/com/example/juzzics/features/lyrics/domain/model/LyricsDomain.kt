package com.example.juzzics.features.lyrics.domain.model

/**
 * @param lyrics plain text
 * @param synced LRC text ("[01:23.45] line") when time-synced lyrics are known, see [parseLrc]
 */
data class LyricsDomain(
    val lyrics: String,
    val synced: String? = null,
)
