package com.example.juzzics.features.player

import com.example.juzzics.features.musics.domain.model.MusicFileDomain

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val currentSong: MusicFileDomain? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** the play queue, in its original order */
    val queue: List<MusicFileDomain> = emptyList(),
    val currentIndex: Int = -1,
    /** increases with every [PlayerController.playQueue], so screens can react to "something new started" */
    val playRequest: Int = 0,
    /** where the queue came from, e.g. a playlist's name; null = the song list */
    val queueSource: String? = null,
)

data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
