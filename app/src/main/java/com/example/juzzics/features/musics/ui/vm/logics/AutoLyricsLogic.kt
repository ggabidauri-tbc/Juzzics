package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.model.knownArtist
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.LYRICS_STATUS

/**
 * On the lyrics screen: if the song has no saved lyrics, search them by its artist + title
 * (once per song) and save what's found. Otherwise the user can still search by hand.
 */
fun MusicVM.lookUpLyricsIfMissing() {
    val song = CLICKED_MUSIC() ?: return
    if (song.lyrics.isNotBlank() || !autoLyricsTried.add(song.id)) return

    val title = song.title.orEmpty()
    if (title.isBlank()) {
        LYRICS_STATUS("Tap here to search lyrics")
        return
    }
    LYRICS_STATUS("Searching lyrics…")
    launch(emitLoadingAction = false) {
        // cleans YouTube-style names, tries artist/title guesses, checks the song's length
        findLyricsUseCase(rawTitle = title, artistTag = song.knownArtist, durationMs = song.duration)
            .onSuccess {
                saveLyricsUseCase(song.id, it)
                LYRICS_STATUS("")
            }
            .onFailure { LYRICS_STATUS("No lyrics found, tap here to search") }
    }
}
