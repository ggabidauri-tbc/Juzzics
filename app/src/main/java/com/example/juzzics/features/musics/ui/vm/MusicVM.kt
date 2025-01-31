package com.example.juzzics.features.musics.ui.vm

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.juzzics.common.base.extensions.mapList
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.State
import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.musics.ui.vm.logics.findLyrics
import com.example.juzzics.features.musics.ui.vm.logics.onDragEnd
import com.example.juzzics.features.musics.ui.vm.logics.playMusicLogic
import com.example.juzzics.features.musics.ui.vm.logics.playNextOrPrevLogic
import com.example.juzzics.features.musics.ui.vm.logics.seekTo
import kotlinx.collections.immutable.ImmutableList


class MusicVM(
    private val context: Application,
    getAllLocalMusicFilesUseCase: GetAllLocalMusicFilesUseCase,
) : BaseViewModel(
    states = mutableMapOf<String, Any>(
        MUSIC_LIST to State<ImmutableList<MusicFileUi>>(),
        MEDIA_PLAYER to State<MediaPlayer>(),
        CLICKED_MUSIC to State<MusicFileUi>(),
        IS_PLAYING to State(false),
        SCROLL_POSITION to State(0),
        SCENE_NAME to State("0"),
    )
) {
    companion object {
        const val MUSIC_LIST = "musicList"
        const val MEDIA_PLAYER = "mediaPlayer"
        const val CLICKED_MUSIC = "clickedMusic"
        const val IS_PLAYING = "isPlaying"
        const val SCROLL_POSITION = "Scroll_POSISION"
        const val SCENE_NAME = "sceneName"
    }

    init {
        launch { call(getAllLocalMusicFilesUseCase().mapList { it.toUi() }, MUSIC_LIST) }
    }

    override fun onAction(action: Action) {
        when (action) {
            is PlayMusicAction -> playMusicLogic(action.music, context)
            is PlayNextAction -> playNextOrPrevLogic(true)
            is PlayPrevAction -> playNextOrPrevLogic(false)
            is SeekToAction -> seekTo(action.position)
            is PlayOrPauseAction -> playMusicLogic(CLICKED_MUSIC(), context)
            is OnDragEndAction -> onDragEnd(action.list)
            is FindLyricsAction -> findLyrics()
            is UpdateSceneAction -> SCENE_NAME(action.scene)
        }
    }

    data class PlayMusicAction(val music: MusicFileUi) : Action
    data class SeekToAction(val position: Float) : Action
    object PlayNextAction : Action
    object PlayPrevAction : Action
    object PlayOrPauseAction : Action
    object FindLyricsAction : Action
    data class UpdateSceneAction(val scene: String) : Action
    data class OnDragEndAction(val list: SnapshotStateList<MusicFileUi>) : Action
    data class ScrollToPositionUiEvent(val position: Int) : UiEvent
}