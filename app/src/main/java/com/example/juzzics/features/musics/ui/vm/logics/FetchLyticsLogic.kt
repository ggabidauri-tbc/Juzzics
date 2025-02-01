package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.ARTIST
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.LYRICS
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.TITLE
import com.example.juzzics.features.musics.ui.vm.MusicVM

fun MusicVM.fetchLyrics() {
    LYRICS(LyricsDomain(""))
    launch(emitErrorMsgAction = true) { call(fetchLyricsUseCase(!ARTIST, !TITLE), LYRICS) }
}
