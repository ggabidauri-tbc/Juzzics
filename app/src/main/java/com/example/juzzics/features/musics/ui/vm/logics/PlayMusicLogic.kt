package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_GROUP
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_TAB
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CURRENT_ID
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SEARCH_QUERY
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SORT

/**
 * Tapping the current song toggles play/pause; tapping another one plays the list as it's
 * shown (search + sort applied) starting from that song.
 */
fun MusicVM.playMusic(music: MusicFileUi) {
    if (music.id == CURRENT_ID()) {
        player.togglePlayPause()
        return
    }
    val visible = MUSIC_LIST().visibleSongs(!SEARCH_QUERY, SORT(), BROWSE_TAB(), BROWSE_GROUP())
    val index = visible.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
    lastTappedSongId = music.id
    player.playQueue(visible.map { it.toDomain() }, index)
}
