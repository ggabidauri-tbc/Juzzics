package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCROLL_POSITION
import kotlinx.collections.immutable.ImmutableList

fun MusicVM.playNextOrPrevLogic(next: Boolean) {
    val list = MUSIC_LIST<ImmutableList<MusicFileUi>>()
    val curMusic = list?.find { it.id == CLICKED_MUSIC<MusicFileUi>()?.id }
    val curIndex = list?.indexOf(curMusic)
    curIndex?.let {
        if (next) {
            if (it + 1 > list.size - 1) 0 else it + 1
        } else {
            if (it - 1 < 0) list.size - 1 else it - 1
        }
    }?.also {
        onAction(MusicVM.PlayMusicAction(list[it], false))
        val pos = if (next) {
            if (it == 0) 0 else it - 1
        } else {
            when (it) {
                0 -> 0
                list.size -> list.size - 1
                else -> it - 1
            }
        }
        SCROLL_POSITION(pos)
        MusicVM.ScrollToPositionUiEvent(pos).emit()
    }
}