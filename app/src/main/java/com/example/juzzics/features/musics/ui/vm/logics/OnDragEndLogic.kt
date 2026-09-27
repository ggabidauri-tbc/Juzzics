package com.example.juzzics.features.musics.ui.vm.logics

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import kotlinx.collections.immutable.toImmutableList

/** your new order is shown right away and saved for next time */
fun MusicVM.onDragEnd(list: SnapshotStateList<MusicFileUi>) {
    MUSIC_LIST(list.toImmutableList())
    val songIds = list.map { it.id }
    launch(emitLoadingAction = false) { saveSongOrderUseCase(songIds) }
}