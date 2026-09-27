package com.example.juzzics.features.player.ui.vm

import androidx.lifecycle.viewModelScope
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.common.messages.AppMessages
import com.example.juzzics.common.songs.SongSettings
import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.usecase.FindLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.ObserveSavedLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SaveLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SearchLyricsUseCase
import com.example.juzzics.features.lyrics.domain.util.SongNameCleaner
import com.example.juzzics.features.lyrics.domain.util.suggestName
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.knownArtist
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.features.player.PlaybackProgress
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.player.RepeatMode
import com.example.juzzics.features.playlists.domain.usecase.ObserveLikedSongIdsUseCase
import com.example.juzzics.features.playlists.domain.usecase.ToggleLikeUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The player shown over every tab: mini player, full player and lyrics (the three pages the
 * player swipes between), the queue, and finding lyrics.
 * One for the whole app (created at the app's root).
 */
class PlayerVM(
    private val player: PlayerController,
    private val findLyricsUseCase: FindLyricsUseCase,
    private val searchLyricsUseCase: SearchLyricsUseCase,
    private val observeSavedLyricsUseCase: ObserveSavedLyricsUseCase,
    private val saveLyricsUseCase: SaveLyricsUseCase,
    observeLikedSongIdsUseCase: ObserveLikedSongIdsUseCase,
    private val toggleLikeUseCase: ToggleLikeUseCase,
    private val songSettings: SongSettings,
) : BaseViewModel(
    listOf(
        CURRENT, IS_PLAYING, PROGRESS, SHUFFLE, REPEAT, QUEUE, QUEUE_INDEX, QUEUE_SOURCE, SHOW_QUEUE,
        LIKED_IDS, LYRICS_OFFSETS, PAGE, LYRICS_STATUS,
        SHOW_LYRICS_SEARCH, SEARCH_ARTIST, SEARCH_TITLE, LYRICS_CANDIDATES, SEARCHING_LYRICS,
    )
) {
    companion object {
        /** the song playing, with its saved lyrics; null = nothing to show (no player) */
        val CURRENT = StateKey<MusicFileUi?>("current", null)
        val IS_PLAYING = StateKey("isPlaying", false)
        val PROGRESS = StateKey("progress", PlaybackProgress())
        val SHUFFLE = StateKey("shuffle", false)
        val REPEAT = StateKey("repeat", RepeatMode.OFF)
        val QUEUE = StateKey<ImmutableList<MusicFileUi>>("queue", persistentListOf())
        val QUEUE_INDEX = StateKey("queueIndex", -1)
        /** where the queue comes from ("Road trip", "Party with Anna"...), null = the library */
        val QUEUE_SOURCE = StateKey<String?>("queueSource", null)
        val SHOW_QUEUE = StateKey("showQueue", false)
        val LIKED_IDS = StateKey<Set<Long>>("likedIds", emptySet())
        /** song id to how much its synced lyrics are shifted (ms) */
        val LYRICS_OFFSETS = StateKey<Map<Long, Long>>("lyricsOffsets", emptyMap())

        /** [PAGE_MINI], [PAGE_FULL] or [PAGE_LYRICS] */
        val PAGE = StateKey("page", PAGE_MINI)
        /** "Looking for lyrics…" / "No lyrics found" while the lyrics page has none; "" otherwise */
        val LYRICS_STATUS = StateKey("lyricsStatus", "")

        val SHOW_LYRICS_SEARCH = StateKey("showLyricsSearch", false)
        val SEARCH_ARTIST = StateKey("searchArtist", "")
        val SEARCH_TITLE = StateKey("searchTitle", "")
        /** results of "Find lyrics", best first */
        val LYRICS_CANDIDATES = StateKey<List<LyricsCandidate>?>("lyricsCandidates", null)
        val SEARCHING_LYRICS = StateKey("searchingLyrics", false)

        const val PAGE_MINI = 0
        const val PAGE_FULL = 1
        const val PAGE_LYRICS = 2
    }

    /** songs whose lyrics were already looked up automatically (once per song) */
    private val autoLyricsTried = mutableSetOf<Long>()

    init {
        observePlayer()
        observeLikedSongIdsUseCase().collectIn(LIKED_IDS)
        songSettings.lyricsOffsets.collectIn(LYRICS_OFFSETS)
        player.progress.collectIn(PROGRESS)
        // notification / widget / "play" somewhere in the app asked for the full player
        viewModelScope.launch { OpenPlayerRequests.count.collect { openIfRequested() } }
    }

    override fun onAction(action: Action) {
        when (action) {
            is TogglePlayAction -> player.togglePlayPause()
            is NextAction -> player.next()
            is PreviousAction -> player.previous()
            is SeekAction -> player.seekTo(action.fraction)
            is SeekToMsAction -> player.seekToMs(action.positionMs)
            is ShuffleAction -> player.toggleShuffle()
            is RepeatAction -> player.cycleRepeatMode()
            is ToggleLikeAction -> CURRENT()?.let { song ->
                val liked = song.id in LIKED_IDS()
                launch(emitLoadingAction = false, emitErrorMsgAction = true) {
                    toggleLikeUseCase(song.toDomain()).getOrThrow()
                    AppMessages.show(if (liked) "Removed from Liked songs" else "Added to Liked songs")
                }
            }

            is ShowQueueAction -> SHOW_QUEUE(action.show)
            is PlayQueueIndexAction -> player.playQueueIndex(action.index)
            is ReorderQueueAction -> player.reorderQueue(action.songIds)
            is RemoveFromQueueAction -> player.removeFromQueue(action.index)

            is PageAction -> {
                PAGE(action.page)
                if (action.page == PAGE_LYRICS) lookUpLyricsIfMissing()
            }

            is ShowLyricsSearchAction -> {
                SHOW_LYRICS_SEARCH(action.show)
                if (action.show && LYRICS_CANDIDATES() == null) searchLyrics()
            }
            is SearchArtistAction -> SEARCH_ARTIST(action.value)
            is SearchTitleAction -> SEARCH_TITLE(action.value)
            is SearchLyricsAction -> searchLyrics()
            is UseLyricsAction -> useLyrics(action.index)
            is ShiftLyricsAction -> CURRENT()?.id?.let { id ->
                val offset = (LYRICS_OFFSETS()[id] ?: 0L) + action.deltaMs
                songSettings.setLyricsOffset(id, offset.coerceIn(-10_000L, 10_000L))
            }
        }
    }

    // ---------------------- player ----------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePlayer() {
        viewModelScope.launch {
            var lastQueue: List<Any>? = null
            player.state.collect { state ->
                IS_PLAYING(state.isPlaying)
                SHUFFLE(state.shuffle)
                REPEAT(state.repeatMode)
                QUEUE_SOURCE(state.queueSource)
                if (state.queue !== lastQueue) {
                    lastQueue = state.queue
                    QUEUE(state.queue.map { it.toUi() }.toImmutableList())
                }
                QUEUE_INDEX(state.currentIndex)
                // nothing left to play: the player hides, next time it starts small
                if (state.currentSong == null) PAGE(PAGE_MINI)
            }
        }
        // the current song + its lyrics saved in the database
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
                    val previousId = CURRENT()?.id
                    CURRENT(song)
                    if (song != null && song.id != previousId) onSongChanged(song)
                    openIfRequested()
                }
        }
    }

    /** a request that came before the song was ready is handled as soon as it is */
    private fun openIfRequested() {
        val request = OpenPlayerRequests.count.value
        if (request > OpenPlayerRequests.handledByPlayer && CURRENT() != null) {
            OpenPlayerRequests.handledByPlayer = request
            PAGE(PAGE_FULL)
        }
    }

    private fun onSongChanged(song: MusicFileUi) {
        // "Find lyrics" starts with this song's name, cleaned up
        // ("Aerosmith - Crazy (official video)" -> Aerosmith / Crazy)
        val guess = SongNameCleaner.guesses(song.title.orEmpty(), song.knownArtist).firstOrNull()
        SEARCH_ARTIST(guess?.artist ?: song.knownArtist)
        SEARCH_TITLE(guess?.title ?: song.title.orEmpty())
        LYRICS_CANDIDATES(null)
        LYRICS_STATUS("")
        if (PAGE() == PAGE_LYRICS) lookUpLyricsIfMissing()
    }

    // ---------------------- lyrics ----------------------

    /** the lyrics page is open and the song has none: look them up once, save what's found */
    private fun lookUpLyricsIfMissing() {
        val song = CURRENT() ?: return
        if (song.lyrics.isNotBlank() || !autoLyricsTried.add(song.id)) return
        val title = song.title.orEmpty()
        if (title.isBlank()) return
        LYRICS_STATUS("Looking for lyrics…")
        launch(emitLoadingAction = false) {
            findLyricsUseCase(rawTitle = title, artistTag = song.knownArtist, durationMs = song.duration)
                .onSuccess {
                    saveLyricsUseCase(song.id, it)
                    if (CURRENT()?.id == song.id) LYRICS_STATUS("")
                    // a messy name and a sure match: suggest the match's name (Home > Fix song names)
                    suggestName(song.id, title, song.knownArtist, it.match)?.let(songSettings::suggest)
                }
                .onFailure { if (CURRENT()?.id == song.id) LYRICS_STATUS("No lyrics found for this song") }
        }
    }

    /** "Find lyrics": results for the typed artist + title, best first */
    private fun searchLyrics() {
        val durationMs = CURRENT()?.duration ?: 0
        SEARCHING_LYRICS(true)
        launch(emitLoadingAction = false, onFinish = { SEARCHING_LYRICS(false) }) {
            LYRICS_CANDIDATES(
                searchLyricsUseCase(!SEARCH_ARTIST, !SEARCH_TITLE, durationMs).getOrDefault(emptyList())
            )
        }
    }

    /** one tap on a result: saved to the song right away (with Undo) */
    private fun useLyrics(index: Int) {
        val song = CURRENT() ?: return
        val picked = LYRICS_CANDIDATES()?.getOrNull(index)?.lyrics ?: return
        val previous = LyricsDomain(lyrics = song.lyrics, synced = song.syncedLyrics)
        SHOW_LYRICS_SEARCH(false)
        LYRICS_STATUS("")
        launch(emitLoadingAction = false, emitErrorMsgAction = true) {
            saveLyricsUseCase(song.id, picked.copy(match = null))
            AppMessages.showWithUndo("Lyrics saved") {
                viewModelScope.launch { saveLyricsUseCase(song.id, previous) }
            }
        }
    }

    // ---------------------- actions ----------------------

    data object TogglePlayAction : Action
    data object NextAction : Action
    data object PreviousAction : Action
    data class SeekAction(val fraction: Float) : Action
    data class SeekToMsAction(val positionMs: Long) : Action
    data object ShuffleAction : Action
    data object RepeatAction : Action
    data object ToggleLikeAction : Action

    data class ShowQueueAction(val show: Boolean) : Action
    data class PlayQueueIndexAction(val index: Int) : Action
    data class ReorderQueueAction(val songIds: List<Long>) : Action
    data class RemoveFromQueueAction(val index: Int) : Action

    /** [PAGE_MINI], [PAGE_FULL] or [PAGE_LYRICS] */
    data class PageAction(val page: Int) : Action

    data class ShowLyricsSearchAction(val show: Boolean) : Action
    data class SearchArtistAction(val value: String) : Action
    data class SearchTitleAction(val value: String) : Action
    data object SearchLyricsAction : Action
    /** saves [LYRICS_CANDIDATES] #[index] to the song */
    data class UseLyricsAction(val index: Int) : Action
    /** synced lyrics run early (negative) or late (positive): shift them */
    data class ShiftLyricsAction(val deltaMs: Long) : Action
}
