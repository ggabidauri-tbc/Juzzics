package com.example.juzzics.features.musics.ui.vm.logics

import androidx.lifecycle.viewModelScope
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_GROUP
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_TAB
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CURRENT_ID
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.PLAYING_FROM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.QUEUE
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.QUEUE_INDEX
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SEARCH_QUERY
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SORT
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the library in sync with the player: which song is highlighted, "playing from", the queue. */
fun MusicVM.observePlayer() {
    viewModelScope.launch {
        var lastPlayRequest: Int? = null
        var lastQueue: List<Any>? = null
        player.state.collect { state ->
            if (state.queue !== lastQueue) {
                lastQueue = state.queue
                QUEUE(state.queue.map { it.toUi() }.toImmutableList())
            }
            QUEUE_INDEX(state.currentIndex)
            // a new queue: show where it's from (a playlist -> only its songs), or all songs
            if (state.playRequest != lastPlayRequest) PLAYING_FROM(state.queueSource)
            lastPlayRequest = state.playRequest
        }
    }
    viewModelScope.launch {
        player.state.map { it.currentSong?.id }.distinctUntilChanged().collect { id ->
            CURRENT_ID(id)
            MUSIC_LIST(MUSIC_LIST().markPlaying(id))
            if (id != null) scrollToSong(id)
        }
    }
}

/** next / previous / auto-next: scroll the list to the new song (not when it was just tapped there) */
private fun MusicVM.scrollToSong(songId: Long) {
    if (songId != lastTappedSongId) {
        val index = MUSIC_LIST().visibleSongs(!SEARCH_QUERY, SORT(), BROWSE_TAB(), BROWSE_GROUP())
            .indexOfFirst { it.id == songId }
        if (index >= 0) MusicVM.ScrollToPositionUiEvent((index - 1).coerceAtLeast(0)).emit()
    }
    lastTappedSongId = null
}

/** marks which song of the list is playing (the list highlights it) */
fun ImmutableList<MusicFileUi>.markPlaying(songId: Long?): ImmutableList<MusicFileUi> =
    if (none { it.isPlaying != (it.id == songId) }) this
    else map { it.copy(isPlaying = it.id == songId) }.toImmutableList()
