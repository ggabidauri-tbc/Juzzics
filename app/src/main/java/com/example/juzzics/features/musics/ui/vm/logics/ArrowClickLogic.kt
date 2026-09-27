package com.example.juzzics.features.musics.ui.vm.logics

import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.Companion.SCENE_NAME
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIFTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FOURTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD

fun MusicVM.arrowClick() {
    when (!SCENE_NAME) {
        FIFTH -> SCENE_NAME(FOURTH)
        FOURTH -> SCENE_NAME(THIRD)
        THIRD -> SCENE_NAME(SECOND)
    }
}