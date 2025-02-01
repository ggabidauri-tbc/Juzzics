package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC

fun MusicVM.tieLyrics() {
    CLICKED_MUSIC<MusicFileUi>()?.run {
        CLICKED_MUSIC(copy(lyrics = MusicVM.LYRICS<LyricsDomain>()?.lyrics.orEmpty()))
        arrowClick()
    }
}