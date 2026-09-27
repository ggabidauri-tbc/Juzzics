package com.example.juzzics.features.musics.ui.vm.logics

import androidx.lifecycle.viewModelScope
import com.example.juzzics.features.lyrics.domain.util.SongNameCleaner
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.knownArtist
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.ARTIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_GROUP
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.BROWSE_TAB
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.IS_PLAYING
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS_CANDIDATES
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS_STATUS
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.PLAYING_FROM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.PROGRESS
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.QUEUE
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.QUEUE_INDEX
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.REPEAT
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SEARCH_QUERY
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SHUFFLE
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SORT
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.TITLE
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD
import com.example.juzzics.features.player.OpenPlayerRequests
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the screen's states in sync with the player (which also runs without this screen). */
@OptIn(ExperimentalCoroutinesApi::class)
fun MusicVM.observePlayer() {
    viewModelScope.launch {
        var lastPlayRequest: Int? = null
        var hadSong = false
        var lastQueue: List<Any>? = null
        player.state.collect { state ->
            IS_PLAYING(state.isPlaying)
            SHUFFLE(state.shuffle)
            REPEAT(state.repeatMode)
            if (state.queue !== lastQueue) {
                lastQueue = state.queue
                QUEUE(state.queue.map { it.toUi() }.toImmutableList())
            }
            QUEUE_INDEX(state.currentIndex)

            // show the mini player when something starts (also when started from Home/Playlists)
            val hasSong = state.currentSong != null
            val newPlayback = hasSong && (!hadSong || state.playRequest != lastPlayRequest)
            if (newPlayback && !SCENE_NAME == FIRST) SCENE_NAME(SECOND)
            // a new queue: show where it's from (a playlist -> only its songs), or all songs
            if (state.playRequest != lastPlayRequest) PLAYING_FROM(state.queueSource)
            openFullPlayerIfRequested(hasSong)
            lastPlayRequest = state.playRequest
            hadSong = hasSong
        }
    }

    // current song + its lyrics saved in the database
    viewModelScope.launch {
        player.state
            .map { it.currentSong }
            .distinctUntilChangedBy { it?.id }
            .flatMapLatest { song ->
                if (song == null) flowOf(null)
                else observeSavedLyricsUseCase(song.id).map { saved ->
                    song.toUi().copy(lyrics = saved?.lyrics.orEmpty(), syncedLyrics = saved?.synced)
                }
            }
            .collect { song ->
                val previousId = CLICKED_MUSIC()?.id
                CLICKED_MUSIC(song)
                MUSIC_LIST(MUSIC_LIST().markPlaying(song?.id))
                if (song != null && song.id != previousId) onSongChanged(song)
            }
    }

    player.progress.collectIn(PROGRESS)

    // notification / widget tapped: open the full player
    viewModelScope.launch {
        OpenPlayerRequests.count.collect { openFullPlayerIfRequested(CLICKED_MUSIC() != null) }
    }
}

private fun MusicVM.openFullPlayerIfRequested(hasSong: Boolean) {
    val request = OpenPlayerRequests.count.value
    if (!hasSong || request <= OpenPlayerRequests.handledByPlayer) return
    OpenPlayerRequests.handledByPlayer = request
    SCENE_NAME(THIRD)
}

private fun MusicVM.onSongChanged(song: MusicFileUi) {
    // lyrics search starts with this song's details, cleaned up ("Aerosmith - Crazy (official video)"
    // -> Aerosmith / Crazy)
    val guess = SongNameCleaner.guesses(song.title.orEmpty(), song.knownArtist).firstOrNull()
    ARTIST(guess?.artist ?: song.knownArtist)
    TITLE(guess?.title ?: song.title.orEmpty())
    LYRICS(null)
    LYRICS_CANDIDATES(emptyList())
    LYRICS_STATUS("")

    // next/prev/auto-next: scroll the list to the new song (not when it was just tapped there)
    if (song.id != lastTappedSongId) {
        val index = MUSIC_LIST().visibleSongs(!SEARCH_QUERY, SORT(), BROWSE_TAB(), BROWSE_GROUP())
            .indexOfFirst { it.id == song.id }
        if (index >= 0) MusicVM.ScrollToPositionUiEvent((index - 1).coerceAtLeast(0)).emit()
    }
    lastTappedSongId = null
}

/** marks which song of the list is playing (the list highlights it) */
fun ImmutableList<MusicFileUi>.markPlaying(songId: Long?): ImmutableList<MusicFileUi> =
    if (none { it.isPlaying != (it.id == songId) }) this
    else map { it.copy(isPlaying = it.id == songId) }.toImmutableList()
