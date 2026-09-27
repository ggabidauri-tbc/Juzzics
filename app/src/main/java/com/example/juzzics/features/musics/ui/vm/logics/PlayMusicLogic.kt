package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_GROUP
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_TAB
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SEARCH_QUERY
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SORT
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND

/**
 * Tapping the current song toggles play/pause; tapping another one plays the list as it's
 * shown (search + sort applied) starting from that song.
 */
fun MusicVM.playMusic(music: MusicFileUi, updateScene: Boolean) {
    if (music.id == CLICKED_MUSIC()?.id) {
        player.togglePlayPause()
    } else {
        val visible = MUSIC_LIST().visibleSongs(!SEARCH_QUERY, SORT(), BROWSE_TAB(), BROWSE_GROUP())
        val index = visible.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
        lastTappedSongId = music.id
        player.playQueue(visible.map { it.toDomain() }, index)
    }
    if (updateScene) SCENE_NAME(SECOND)
}
