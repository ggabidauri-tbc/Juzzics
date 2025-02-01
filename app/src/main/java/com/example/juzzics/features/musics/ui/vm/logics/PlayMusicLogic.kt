package com.example.juzzics.features.musics.ui.vm.logics

import android.app.Application
import android.content.ContentUris
import android.media.MediaPlayer
import android.provider.MediaStore
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.CLICKED_MUSIC
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.IS_PLAYING
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MEDIA_PLAYER
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.MUSIC_LIST
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun MusicVM.playMusicLogic(musicFile: MusicFileUi?, context: Application, pause: Boolean = false) {
    val mediaPlayer = MEDIA_PLAYER<MediaPlayer>()
    val musicList = MUSIC_LIST<ImmutableList<MusicFileUi>>()
    val clickedMusic = CLICKED_MUSIC<MusicFileUi>()
    fun onSameMusicCLicked() {
        if (mediaPlayer?.isPlaying == true || pause) {
            mediaPlayer?.pause()
            IS_PLAYING(false)
        } else {
            mediaPlayer?.start()
            IS_PLAYING(true)
        }
    }

    fun onNewMusicClicked() {
        musicFile?.let {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            MediaPlayer.create(
                context, ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicFile.id
                )
            ) saveIn MEDIA_PLAYER
            CLICKED_MUSIC(musicFile)
            musicList?.map {
                it.copy(isPlaying = it.id == musicFile.id)
            }?.toImmutableList() saveIn MUSIC_LIST
            IS_PLAYING(true)
            MEDIA_PLAYER<MediaPlayer>()?.apply {
                setOnCompletionListener { onAction(MusicVM.PlayNextAction) }
                start()
            }
        }
    }

    if (musicFile?.id == clickedMusic?.id) {
        onSameMusicCLicked()
    } else {
        onNewMusicClicked()
    }
}