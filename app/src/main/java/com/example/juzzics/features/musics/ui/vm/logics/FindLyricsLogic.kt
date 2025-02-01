package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FORTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD

fun MusicVM.findLyricsSceneUpdate() {
    (if (MusicVM.SCENE_NAME<String>() == THIRD) FORTH else THIRD) saveIn SCENE_NAME
}