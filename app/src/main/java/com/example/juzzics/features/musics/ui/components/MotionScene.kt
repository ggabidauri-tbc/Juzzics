package com.example.juzzics.features.musics.ui.components

import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionScene
import com.example.juzzics.features.musics.ui.vm.MusicVM

@OptIn(ExperimentalMotionApi::class)
val motionScene = MotionScene {
    val musicList = createRefFor("music_list")
    val box = createRefFor("box")
    val nowPlaying = createRefFor("now_playing")
    val musicName = createRefFor("music_name")
    val icon = createRefFor("icon")
    val musicProgress = createRefFor("music_progress")
    val btPrev = createRefFor("bt_prev")
    val btNext = createRefFor("bt_next")
    val playOrStop = createRefFor("play_or_stop")
    val findLyrics = createRefFor("find_lyrics")
    val allViews = listOf(
        musicList, box, nowPlaying, musicName, icon, musicProgress,
        btPrev, btNext, playOrStop, findLyrics
    )

    val firstSet = constraintSet(MusicVM.MotionScenes.FIRST) {
        constrain(musicList) {
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)
            top.linkTo(parent.top, 16.dp)
            height = Dimension.fillToConstraints
        }
        listOf(
            box, nowPlaying, playOrStop, musicName, icon,
            btPrev, btNext, musicProgress, findLyrics
        ).forEach {
            constrain(it) {
                top.linkTo(parent.bottom)
                centerHorizontallyTo(parent)
            }
        }
    }
    val secondSet = constraintSet(MusicVM.MotionScenes.SECOND) {
        constrain(musicList) {
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)
            top.linkTo(parent.top, 16.dp)
            height = Dimension.percent(0.9f)
        }
        constrain(box) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(musicList.bottom)
            bottom.linkTo(parent.bottom)
        }
        constrain(nowPlaying) {
            top.linkTo(box.top, 16.dp)
            start.linkTo(icon.end)
        }
        constrain(playOrStop) {
            end.linkTo(parent.end, 16.dp)
            top.linkTo(box.top, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(musicName) {
            height = Dimension.wrapContent
            width = Dimension.fillToConstraints
            start.linkTo(nowPlaying.start)
            end.linkTo(playOrStop.start, (-16).dp)
            top.linkTo(nowPlaying.bottom)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(icon) {
            width = Dimension.wrapContent
            height = Dimension.fillToConstraints
            top.linkTo(box.top, 16.dp)
            start.linkTo(parent.start, 16.dp)
            bottom.linkTo(box.bottom, 16.dp)
        }
        constrain(btPrev) {
            alpha = 0f
            start.linkTo(playOrStop.start)
            end.linkTo(parent.end)
            top.linkTo(box.top, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(btNext) {
            alpha = 0f
            start.linkTo(playOrStop.start)
            end.linkTo(parent.end)
            top.linkTo(box.top, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(musicProgress) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom, 8.dp)
            start.linkTo(parent.start, 16.dp)
            end.linkTo(musicName.end, 16.dp)
        }
        constrain(findLyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
    }

    constraintSet(MusicVM.MotionScenes.THIRD) {
        constrain(musicList) {
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)
            top.linkTo(parent.top, 16.dp)
        }
        constrain(box) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom, (-16).dp)
        }
        constrain(nowPlaying) {
            start.linkTo(parent.start, 16.dp)
            top.linkTo(parent.top, 16.dp)
        }
        constrain(playOrStop) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom, 100.dp)
        }
        constrain(musicName) {
            height = Dimension.wrapContent
            width = Dimension.fillToConstraints
            start.linkTo(icon.start)
            end.linkTo(icon.end)
            bottom.linkTo(playOrStop.top, 32.dp)
        }
        constrain(icon) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top, 100.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(musicName.top)
        }
        constrain(btPrev) {
            end.linkTo(playOrStop.start, 32.dp)
            top.linkTo(playOrStop.top)
            bottom.linkTo(playOrStop.bottom)
        }
        constrain(btNext) {
            start.linkTo(playOrStop.end, 32.dp)
            top.linkTo(playOrStop.top)
            bottom.linkTo(playOrStop.bottom)
        }
        constrain(musicProgress) {
            width = Dimension.fillToConstraints
            height = Dimension.value(10.dp)
            bottom.linkTo(musicName.top, 32.dp)
            start.linkTo(parent.start, 32.dp)
            end.linkTo(parent.end, 32.dp)
        }
        constrain(findLyrics) {
            bottom.linkTo(parent.bottom, 16.dp)
            centerHorizontallyTo(parent)
        }
    }
    constraintSet(MusicVM.MotionScenes.FORTH) {
        constrain(findLyrics) {
            top.linkTo(parent.top, 32.dp)
            centerHorizontallyTo(parent)
        }
        allViews.filterNot { it == findLyrics }.forEach {
            constrain(it) {
                bottom.linkTo(parent.top)
                centerHorizontallyTo(parent)
            }
        }
    }
    defaultTransition(firstSet, secondSet)
}