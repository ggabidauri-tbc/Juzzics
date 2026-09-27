package com.example.juzzics.features.musics.ui.vm

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.juzzics.common.base.extensions.mapList
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.messages.AppMessages
import com.example.juzzics.common.songs.SongSettings
import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.musics.domain.usecases.SaveSongOrderUseCase
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.SongSort
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.vm.logics.markPlaying
import com.example.juzzics.features.musics.ui.vm.logics.observePlayer
import com.example.juzzics.features.musics.ui.vm.logics.onDragEnd
import com.example.juzzics.features.musics.ui.vm.logics.playMusic
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.playlists.domain.usecase.ObserveLikedSongIdsUseCase
import com.example.juzzics.features.playlists.domain.usecase.ToggleLikeUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop

/**
 * The Library tab: all songs (your own drag-to-reorder order), search, sort, albums and
 * artists. The player itself is [com.example.juzzics.features.player.ui.vm.PlayerVM].
 */
class MusicVM(
    private val getAllLocalMusicFilesUseCase: GetAllLocalMusicFilesUseCase,
    val saveSongOrderUseCase: SaveSongOrderUseCase,
    observeLikedSongIdsUseCase: ObserveLikedSongIdsUseCase,
    private val toggleLikeUseCase: ToggleLikeUseCase,
    val player: PlayerController,
    songSettings: SongSettings,
) : BaseViewModel(
    listOf(
        MUSIC_LIST, CURRENT_ID, SEARCH_QUERY, SORT, BROWSE_TAB, BROWSE_GROUP,
        LIKED_IDS, PLAYING_FROM, QUEUE, QUEUE_INDEX,
    )
) {
    companion object {
        /** all songs, in your own (drag-to-reorder) order */
        val MUSIC_LIST = StateKey<ImmutableList<MusicFileUi>>("musicList", persistentListOf())
        /** the song playing (highlighted in the list) */
        val CURRENT_ID = StateKey<Long?>("currentId", null)

        val SEARCH_QUERY = StateKey("searchQuery", "")
        val SORT = StateKey("sort", SongSort.CUSTOM)
        val BROWSE_TAB = StateKey("browseTab", BrowseTab.SONGS)
        /** album / artist opened in the Albums / Artists tab */
        val BROWSE_GROUP = StateKey<String?>("browseGroup", null)
        /** name of the playlist (etc.) being played: the list shows its songs instead of all */
        val PLAYING_FROM = StateKey<String?>("playingFrom", null)
        /** songs with a heart */
        val LIKED_IDS = StateKey<Set<Long>>("likedIds", emptySet())

        /** the play queue (shown instead of all songs while [PLAYING_FROM] is set) */
        val QUEUE = StateKey<ImmutableList<MusicFileUi>>("queue", persistentListOf())
        val QUEUE_INDEX = StateKey("queueIndex", -1)
    }

    /** song the user tapped last, so the list doesn't auto-scroll to it */
    internal var lastTappedSongId: Long? = null

    init {
        loadSongs()
        observePlayer()
        observeLikedSongIdsUseCase().collectIn(LIKED_IDS)
        // a song was renamed (Home > Fix song names): show the new names
        launch(emitLoadingAction = false) {
            songSettings.names.drop(1).collect { loadSongs() }
        }
    }

    private fun loadSongs() = launch {
        call(getAllLocalMusicFilesUseCase().mapList { it.toUi() }, MUSIC_LIST)
        MUSIC_LIST(MUSIC_LIST().markPlaying(CURRENT_ID()))
    }

    override fun onAction(action: Action) {
        when (action) {
            is PlayMusicAction -> playMusic(action.music)
            is PlayQueueIndexAction -> player.playQueueIndex(action.index)
            is PlayNextAfterCurrentAction -> {
                player.playNext(action.song.toDomain())
                AppMessages.show("Plays next: ${action.song.title.orEmpty()}")
            }
            is AddToQueueAction -> {
                player.addToQueue(action.song.toDomain())
                AppMessages.show("Added to the queue")
            }
            is ReorderQueueAction -> player.reorderQueue(action.songIds)
            is ToggleLikeAction -> {
                val liked = action.song.id in LIKED_IDS()
                launch(emitLoadingAction = false, emitErrorMsgAction = true) {
                    toggleLikeUseCase(action.song.toDomain()).getOrThrow()
                    AppMessages.show(if (liked) "Removed from Liked songs" else "Added to Liked songs")
                }
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
        }
    }

    data class PlayMusicAction(val music: MusicFileUi) : Action
    data class PlayQueueIndexAction(val index: Int) : Action
    /** "Play next" from a song's menu */
    data class PlayNextAfterCurrentAction(val song: MusicFileUi) : Action
    data class AddToQueueAction(val song: MusicFileUi) : Action
    data class ReorderQueueAction(val songIds: List<Long>) : Action
    data class ToggleLikeAction(val song: MusicFileUi) : Action

    data class SearchAction(val query: String) : Action
    data class SortAction(val sort: SongSort) : Action
    data class BrowseTabAction(val tab: BrowseTab) : Action
    /** leaves the "Playing from <playlist>" view, back to all songs */
    data object ShowAllSongsAction : Action
    /** null closes the opened album/artist */
    data class OpenGroupAction(val name: String?) : Action

    data class OnDragEndAction(val list: SnapshotStateList<MusicFileUi>) : Action
    data class ScrollToPositionUiEvent(val position: Int) : UiEvent
}
