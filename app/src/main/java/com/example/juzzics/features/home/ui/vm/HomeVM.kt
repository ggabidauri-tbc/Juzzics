package com.example.juzzics.features.home.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.common.songs.NameSuggestion
import com.example.juzzics.common.songs.SongSettings
import com.example.juzzics.features.home.domain.usecase.GetMostPlayedUseCase
import com.example.juzzics.features.home.domain.usecase.GetRecentlyPlayedUseCase
import com.example.juzzics.features.lyrics.data.LyricsCoverage
import com.example.juzzics.features.lyrics.data.LyricsPrep
import com.example.juzzics.features.lyrics.data.LyricsPrepState
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

class HomeVM(
    getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    getMostPlayedUseCase: GetMostPlayedUseCase,
    getPlaylistsUseCase: GetPlaylistsUseCase,
    private val getAllLocalMusicFilesUseCase: GetAllLocalMusicFilesUseCase,
    private val player: PlayerController,
    private val lyricsPrep: LyricsPrep,
    private val songSettings: SongSettings,
) : BaseViewModel(
    listOf(
        RECENTLY_PLAYED, MOST_PLAYED, PLAYLISTS, LYRICS_PREP, LYRICS_COVERAGE,
        NAME_SUGGESTIONS, SHOW_NAME_FIXES, SHOW_TRIP_PREP,
    )
) {

    companion object {
        val RECENTLY_PLAYED = StateKey<List<MusicFileDomain>>("recentlyPlayed", emptyList())
        /** song to how many times it was played */
        val MOST_PLAYED = StateKey<List<Pair<MusicFileDomain, Int>>>("mostPlayed", emptyList())
        val PLAYLISTS = StateKey<List<PlaylistDomain>>("playlists", emptyList())
        /** "trip prep": getting lyrics for all songs */
        val LYRICS_PREP = StateKey("lyricsPrep", LyricsPrepState())
        /** songs with lyrics saved, of all (null until counted) */
        val LYRICS_COVERAGE = StateKey<LyricsCoverage?>("lyricsCoverage", null)
        /** better names found for songs with messy ones */
        val NAME_SUGGESTIONS = StateKey<List<NameSuggestion>>("nameSuggestions", emptyList())
        val SHOW_NAME_FIXES = StateKey("showNameFixes", false)
        /** the "offline ready" sheet (trip prep) */
        val SHOW_TRIP_PREP = StateKey("showTripPrep", false)
    }

    init {
        getRecentlyPlayedUseCase().collectIn(RECENTLY_PLAYED)
        getMostPlayedUseCase().collectIn(MOST_PLAYED)
        getPlaylistsUseCase().collectIn(PLAYLISTS)
        lyricsPrep.state.collectIn(LYRICS_PREP)
        songSettings.suggestions.map { it.values.sortedBy { s -> s.currentTitle.lowercase() } }.collectIn(NAME_SUGGESTIONS)
        countLyrics()
        // counted again whenever trip prep stops / finishes, and as it goes
        launch(emitLoadingAction = false) {
            lyricsPrep.state.distinctUntilChangedBy { it.running to it.found / 10 }.collect { countLyrics() }
        }
    }

    private fun countLyrics() = launch(emitLoadingAction = false) { LYRICS_COVERAGE(lyricsPrep.coverage()) }

    override fun onAction(action: Action) {
        when (action) {
            is PlaySongsAction -> player.playQueue(action.songs, action.startIndex, action.source)
            is PlayPlaylistAction -> PLAYLISTS().find { it.id == action.playlistId }
                ?.let { player.playQueue(it.songs, 0, source = it.name) }
            is ShuffleAllAction -> launch(emitLoadingAction = false, emitErrorMsgAction = true) {
                val songs = getAllLocalMusicFilesUseCase().getOrThrow()
                if (songs.isNotEmpty()) player.playQueue(songs.shuffled(), 0, source = "Shuffle")
            }
            is ShowTripPrepAction -> SHOW_TRIP_PREP(action.show)
            is StartTripPrepAction -> lyricsPrep.start()
            is StopTripPrepAction -> lyricsPrep.stop()
            is DismissTripPrepMessageAction -> lyricsPrep.clearMessage()
            is ShowNameFixesAction -> SHOW_NAME_FIXES(action.show)
            is ApplyNameFixesAction -> {
                NAME_SUGGESTIONS().forEach { suggestion ->
                    if (suggestion.songId in action.acceptedIds) songSettings.rename(suggestion.songId, suggestion.suggested)
                    else songSettings.dismissSuggestion(suggestion.songId)
                }
                SHOW_NAME_FIXES(false)
            }
        }
    }

    /** plays [songs] (e.g. the "recently played" row) starting from [startIndex] */
    data class PlaySongsAction(
        val songs: List<MusicFileDomain>,
        val startIndex: Int,
        val source: String,
    ) : Action
    data class PlayPlaylistAction(val playlistId: Long) : Action
    data object ShuffleAllAction : Action
    data class ShowTripPrepAction(val show: Boolean) : Action
    data object StartTripPrepAction : Action
    data object StopTripPrepAction : Action
    data object DismissTripPrepMessageAction : Action
    data class ShowNameFixesAction(val show: Boolean) : Action
    /** renames the songs in [acceptedIds] to their suggested names, forgets the other suggestions */
    data class ApplyNameFixesAction(val acceptedIds: Set<Long>) : Action
}
