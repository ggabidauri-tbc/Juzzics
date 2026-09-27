package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.ARTIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.TITLE

fun MusicVM.fetchLyrics() {
    LYRICS(LyricsDomain(""))
    launch(emitErrorMsgAction = true) { call(fetchLyricsUseCase(!ARTIST, !TITLE), LYRICS) }
}
