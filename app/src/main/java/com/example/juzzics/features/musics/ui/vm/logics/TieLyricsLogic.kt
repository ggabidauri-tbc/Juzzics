package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS

/** saves the found lyrics to the current song (the song's lyrics update from the database) */
fun MusicVM.tieLyrics() {
    val song = CLICKED_MUSIC() ?: return
    val lyrics = LYRICS()?.takeIf { it.lyrics.isNotBlank() } ?: return
    launch(emitLoadingAction = false, emitErrorMsgAction = true) { saveLyricsUseCase(song.id, lyrics) }
    arrowClick()
}
