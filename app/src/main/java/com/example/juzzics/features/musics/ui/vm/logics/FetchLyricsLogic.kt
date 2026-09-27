package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.ARTIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS_CANDIDATES
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.TITLE

/**
 * Manual search: shows the results to pick from (best first). With a single result, or one
 * whose length matches the song, that one is picked right away.
 */
fun MusicVM.fetchLyrics() {
    LYRICS(null)
    LYRICS_CANDIDATES(emptyList())
    val durationMs = CLICKED_MUSIC()?.duration ?: 0
    launch(emitErrorMsgAction = true) {
        val candidates = searchLyricsUseCase(!ARTIST, !TITLE, durationMs).getOrThrow()
        LYRICS_CANDIDATES(candidates)
        val obvious = candidates.singleOrNull() ?: candidates.firstOrNull()?.takeIf { it.sameLength }
        if (obvious != null) LYRICS(obvious.lyrics)
    }
}
