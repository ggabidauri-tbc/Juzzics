package com.example.juzzics.features.musics.ui.vm

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.juzzics.common.base.extensions.mapList
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.usecase.FindLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SearchLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.ObserveSavedLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SaveLyricsUseCase
import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.musics.domain.usecases.SaveSongOrderUseCase
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.playlists.domain.usecase.ObserveLikedSongIdsUseCase
import com.example.juzzics.features.playlists.domain.usecase.ToggleLikeUseCase
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.SongSort
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FOURTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD
import com.example.juzzics.features.musics.ui.vm.logics.arrowClick
import com.example.juzzics.features.musics.ui.vm.logics.fetchLyrics
import com.example.juzzics.features.musics.ui.vm.logics.findLyricsSceneUpdate
import com.example.juzzics.features.musics.ui.vm.logics.lookUpLyricsIfMissing
import com.example.juzzics.features.musics.ui.vm.logics.markPlaying
import com.example.juzzics.features.musics.ui.vm.logics.observePlayer
import com.example.juzzics.features.musics.ui.vm.logics.onDragEnd
import com.example.juzzics.features.musics.ui.vm.logics.playMusic
import com.example.juzzics.features.musics.ui.vm.logics.tieLyrics
import com.example.juzzics.features.player.PlaybackProgress
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.player.RepeatMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf


class MusicVM(
    getAllLocalMusicFilesUseCase: GetAllLocalMusicFilesUseCase,
    val findLyricsUseCase: FindLyricsUseCase,
    val searchLyricsUseCase: SearchLyricsUseCase,
    val observeSavedLyricsUseCase: ObserveSavedLyricsUseCase,
    val saveLyricsUseCase: SaveLyricsUseCase,
    val saveSongOrderUseCase: SaveSongOrderUseCase,
    observeLikedSongIdsUseCase: ObserveLikedSongIdsUseCase,
    val toggleLikeUseCase: ToggleLikeUseCase,
    val player: PlayerController,
) : BaseViewModel(
    listOf(
        MUSIC_LIST, CLICKED_MUSIC, IS_PLAYING, SCENE_NAME,
        LYRICS, LYRICS_CANDIDATES, LYRICS_STATUS, ARTIST, TITLE,
        SEARCH_QUERY, SORT, BROWSE_TAB, BROWSE_GROUP, LIKED_IDS, PLAYING_FROM,
        SHUFFLE, REPEAT, QUEUE, QUEUE_INDEX, SHOW_QUEUE, PROGRESS,
    )
) {
    companion object {
        /** all songs, in your own (drag-to-reorder) order */
        val MUSIC_LIST = StateKey<ImmutableList<MusicFileUi>>("musicList", persistentListOf())
        /** the player's current song, with its saved lyrics */
        val CLICKED_MUSIC = StateKey<MusicFileUi?>("clickedMusic", null)
        val IS_PLAYING = StateKey("isPlaying", false)
        val SCENE_NAME = StateKey("sceneName", FIRST)

        /** lyrics picked on the search lyrics screen (shown, can be tied to the song) */
        val LYRICS = StateKey<LyricsDomain?>("lyrics", null)
        /** results of the manual lyrics search, best first */
        val LYRICS_CANDIDATES = StateKey<List<LyricsCandidate>>("lyricsCandidates", emptyList())
        /** message on the lyrics screen while looking lyrics up automatically */
        val LYRICS_STATUS = StateKey("lyricsStatus", "")
        val ARTIST = StateKey("artist", "")
        val TITLE = StateKey("title", "")

        val SEARCH_QUERY = StateKey("searchQuery", "")
        val SORT = StateKey("sort", SongSort.CUSTOM)
        val BROWSE_TAB = StateKey("browseTab", BrowseTab.SONGS)
        /** album / artist opened in the Albums / Artists tab */
        val BROWSE_GROUP = StateKey<String?>("browseGroup", null)
        /** name of the playlist (etc.) being played: the list shows its songs instead of all */
        val PLAYING_FROM = StateKey<String?>("playingFrom", null)
        /** songs with a heart */
        val LIKED_IDS = StateKey<Set<Long>>("likedIds", emptySet())

        val SHUFFLE = StateKey("shuffle", false)
        val REPEAT = StateKey("repeat", RepeatMode.OFF)
        val QUEUE = StateKey<ImmutableList<MusicFileUi>>("queue", persistentListOf())
        val QUEUE_INDEX = StateKey("queueIndex", -1)
        val SHOW_QUEUE = StateKey("showQueue", false)
        val PROGRESS = StateKey("progress", PlaybackProgress())
    }

    object MotionScenes {
        const val FIRST = "1"
        const val SECOND = "2"
        const val THIRD = "3"
        const val FOURTH = "4"
        const val FIFTH = "5"
    }

    /** song the user tapped last, so the list doesn't auto-scroll to it */
    internal var lastTappedSongId: Long? = null

    /** songs whose lyrics were already looked up automatically */
    internal val autoLyricsTried = mutableSetOf<Long>()

    init {
        launch {
            call(getAllLocalMusicFilesUseCase().mapList { it.toUi() }, MUSIC_LIST)
            MUSIC_LIST(MUSIC_LIST().markPlaying(CLICKED_MUSIC()?.id))
        }
        observePlayer()
        observeLikedSongIdsUseCase().collectIn(LIKED_IDS)
    }

    override fun onAction(action: Action) {
        when (action) {
            is PlayMusicAction -> playMusic(action.music, action.updateScene)
            is PlayNextAction -> player.next()
            is PlayPrevAction -> player.previous()
            is SeekToAction -> player.seekTo(action.position)
            is SeekToMsAction -> player.seekToMs(action.positionMs)
            is PlayOrPauseAction -> if (action.pause) player.pause() else player.togglePlayPause()
            is ToggleShuffleAction -> player.toggleShuffle()
            is CycleRepeatAction -> player.cycleRepeatMode()
            is ShowQueueAction -> SHOW_QUEUE(action.show)
            is PlayQueueIndexAction -> player.playQueueIndex(action.index)
            is PlayNextAfterCurrentAction -> player.playNext(action.song.toDomain())
            is AddToQueueAction -> player.addToQueue(action.song.toDomain())
            is ReorderQueueAction -> player.reorderQueue(action.songIds)
            is RemoveFromQueueAction -> player.removeFromQueue(action.index)
            is ToggleLikeAction -> launch(emitLoadingAction = false, emitErrorMsgAction = true) {
                toggleLikeUseCase(action.song.toDomain()).getOrThrow()
            }

            is OnDragEndAction -> onDragEnd(action.list)
            is SearchAction -> SEARCH_QUERY(action.query)
            is SortAction -> SORT(action.sort)
            is BrowseTabAction -> {
                BROWSE_TAB(action.tab)
                BROWSE_GROUP(null)
            }
            is OpenGroupAction -> BROWSE_GROUP(action.name)
            is ShowAllSongsAction -> PLAYING_FROM(null)

            is FindLyricsClickedAction -> findLyricsSceneUpdate()
            is UpdateSceneAction -> SCENE_NAME(action.scene)
            is ArrowDownClickAction -> arrowClick()
            is BoxClickAction -> SCENE_NAME(THIRD)
            is FetchLyricsAction -> fetchLyrics()
            is PickLyricsAction -> LYRICS(LYRICS_CANDIDATES().getOrNull(action.index)?.lyrics)
            is BackToLyricsResultsAction -> LYRICS(null)
            is UpdateArtistAction -> ARTIST(action.value)
            is UpdateTitleAction -> TITLE(action.value)
            is TieLyrics -> tieLyrics()
        }
        // opening the lyrics screen looks lyrics up if the song has none yet
        if (!SCENE_NAME == FOURTH) lookUpLyricsIfMissing()
    }

    data object FetchLyricsAction : Action
    /** picks one of [LYRICS_CANDIDATES] */
    data class PickLyricsAction(val index: Int) : Action
    data object BackToLyricsResultsAction : Action
    data class UpdateArtistAction(val value: String) : Action
    data class UpdateTitleAction(val value: String) : Action

    data class PlayMusicAction(val music: MusicFileUi, val updateScene: Boolean = true) : Action
    data class SeekToAction(val position: Float) : Action
    data object PlayNextAction : Action
    data object PlayPrevAction : Action
    data class PlayOrPauseAction(val pause: Boolean = false) : Action
    data object ToggleShuffleAction : Action
    data object CycleRepeatAction : Action
    data class ShowQueueAction(val show: Boolean) : Action
    data class PlayQueueIndexAction(val index: Int) : Action
    data class SeekToMsAction(val positionMs: Long) : Action
    /** "Play next" from a song's menu */
    data class PlayNextAfterCurrentAction(val song: MusicFileUi) : Action
    data class AddToQueueAction(val song: MusicFileUi) : Action
    data class ReorderQueueAction(val songIds: List<Long>) : Action
    data class RemoveFromQueueAction(val index: Int) : Action
    data class ToggleLikeAction(val song: MusicFileUi) : Action

    data class SearchAction(val query: String) : Action
    data class SortAction(val sort: SongSort) : Action
    data class BrowseTabAction(val tab: BrowseTab) : Action
    /** leaves the "Playing from <playlist>" view, back to all songs */
    data object ShowAllSongsAction : Action
    /** null closes the opened album/artist */
    data class OpenGroupAction(val name: String?) : Action

    data object FindLyricsClickedAction : Action
    data object BoxClickAction : Action
    data object ArrowDownClickAction : Action
    data object TieLyrics : Action
    data class UpdateSceneAction(val scene: String) : Action
    data class OnDragEndAction(val list: SnapshotStateList<MusicFileUi>) : Action
    data class ScrollToPositionUiEvent(val position: Int) : UiEvent
}
