package com.example.juzzics.features.musics.ui.vm

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.juzzics.common.base.extensions.mapList
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.State
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.usecase.FetchLyricsUseCase
import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD
import com.example.juzzics.features.musics.ui.vm.logics.arrowClick
import com.example.juzzics.features.musics.ui.vm.logics.fetchLyrics
import com.example.juzzics.features.musics.ui.vm.logics.findLyricsSceneUpdate
import com.example.juzzics.features.musics.ui.vm.logics.onDragEnd
import com.example.juzzics.features.musics.ui.vm.logics.playMusicLogic
import com.example.juzzics.features.musics.ui.vm.logics.playNextOrPrevLogic
import com.example.juzzics.features.musics.ui.vm.logics.seekTo
import com.example.juzzics.features.musics.ui.vm.logics.tieLyrics
import kotlinx.collections.immutable.ImmutableList


class MusicVM(
    private val context: Application,
    getAllLocalMusicFilesUseCase: GetAllLocalMusicFilesUseCase,
    val fetchLyricsUseCase: FetchLyricsUseCase
) : BaseViewModel(
    states = mutableMapOf<String, Any>(
        MUSIC_LIST to State<ImmutableList<MusicFileUi>>(),
        MEDIA_PLAYER to State<MediaPlayer>(),
        CLICKED_MUSIC to State<MusicFileUi>(),
        IS_PLAYING to State(false),
        SCROLL_POSITION to State(0),
        SCENE_NAME to State(FIRST),
        LYRICS to State<LyricsDomain>(),
        ARTIST to State("type artist"),
        TITLE to State("type song")
    )
) {
    companion object {
        const val MUSIC_LIST = "musicList"
        const val MEDIA_PLAYER = "mediaPlayer"
        const val CLICKED_MUSIC = "clickedMusic"
        const val IS_PLAYING = "isPlaying"
        const val SCROLL_POSITION = "Scroll_POSISION"
        const val SCENE_NAME = "sceneName"

        const val LYRICS = "Lyrics"
        const val ARTIST = "Artist"
        const val TITLE = "Title"
    }

    object MotionScenes {
        const val FIRST = "1"
        const val SECOND = "2"
        const val THIRD = "3"
        const val FORTH = "4"
        const val FIFTH = "5"
    }

    init {
        launch { call(getAllLocalMusicFilesUseCase().mapList { it.toUi() }, MUSIC_LIST) }
    }

    override fun onAction(action: Action) {
        when (action) {
            is PlayMusicAction -> playMusicLogic(action.music, context)
                .also { if (action.updateScene) SCENE_NAME(SECOND) }

            is PlayNextAction -> playNextOrPrevLogic(true)
            is PlayPrevAction -> playNextOrPrevLogic(false)
            is SeekToAction -> seekTo(action.position)
            is PlayOrPauseAction -> playMusicLogic(CLICKED_MUSIC(), context, action.pause)
            is OnDragEndAction -> onDragEnd(action.list)
            is FindLyricsClickedAction -> findLyricsSceneUpdate()
            is UpdateSceneAction -> SCENE_NAME(action.scene)
            is ArrowDownClickAction -> arrowClick()
            is BoxClickAction -> SCENE_NAME(THIRD)
            is FetchLyricsAction -> fetchLyrics()
            is UpdateArtistAction -> ARTIST(action.value)
            is UpdateTitleAction -> TITLE(action.value)
            is TieLyrics -> tieLyrics()
        }
    }

    data object FetchLyricsAction : Action
    data class UpdateArtistAction(val value: String) : Action
    data class UpdateTitleAction(val value: String) : Action

    data class PlayMusicAction(val music: MusicFileUi, val updateScene: Boolean = true) : Action
    data class SeekToAction(val position: Float) : Action
    object PlayNextAction : Action
    object PlayPrevAction : Action
    data class PlayOrPauseAction(val pause: Boolean = false) : Action
    object FindLyricsClickedAction : Action
    object BoxClickAction : Action
    object ArrowDownClickAction : Action
    object TieLyrics : Action
    data class UpdateSceneAction(val scene: String) : Action
    data class OnDragEndAction(val list: SnapshotStateList<MusicFileUi>) : Action
    data class ScrollToPositionUiEvent(val position: Int) : UiEvent
}