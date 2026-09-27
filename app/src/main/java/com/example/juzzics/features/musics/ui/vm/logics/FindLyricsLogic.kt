package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FOURTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD

fun MusicVM.findLyricsSceneUpdate() {
    (if (!SCENE_NAME == THIRD) FOURTH else THIRD) saveIn SCENE_NAME
}
