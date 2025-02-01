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
    val lyrics = createRefFor("lyrics")
    val downArrow = createRefFor("arrow_down")
    val goSearchLyrics = createRefFor("go_search_lyrics")
    val searchLyricsScreen = createRefFor("search_lyrics_screen")
    val tieLyrics = createRefFor("tie_lyrics")
    val editLyrics = createRefFor("edit_lyrics")
    val allViews = listOf(
        musicList, box, nowPlaying, musicName, icon, musicProgress, btPrev, btNext, playOrStop,
        lyrics, downArrow, goSearchLyrics, searchLyricsScreen, tieLyrics, editLyrics
    )

    val firstSet = constraintSet(MusicVM.MotionScenes.FIRST) {
        allViews.except(musicList, nowPlaying, downArrow, searchLyricsScreen).forEach {
            constrain(it) {
                top.linkTo(parent.bottom)
                centerHorizontallyTo(parent)
            }
        }
        constrain(musicList) {
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)
            top.linkTo(parent.top, 16.dp)
            height = Dimension.fillToConstraints
        }
        constrain(nowPlaying) { bottom.linkTo(parent.top) }
        constrain(downArrow) {
            top.linkTo(parent.bottom)
            end.linkTo(parent.end, 16.dp)
        }
        constrain(searchLyricsScreen) {
            top.linkTo(parent.top, 50.dp)
            start.linkTo(parent.end, 50.dp)
        }
        constrain(editLyrics){
            start.linkTo(parent.end, 16.dp)
            bottom.linkTo(downArrow.top, 16.dp)
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
            bottom.linkTo(parent.top)
        }
        constrain(playOrStop) {
            end.linkTo(parent.end, 16.dp)
            top.linkTo(box.top, 32.dp)
            bottom.linkTo(box.bottom, 32.dp)
        }
        constrain(musicName) {
            height = Dimension.wrapContent
            width = Dimension.fillToConstraints
            start.linkTo(icon.end)
            end.linkTo(playOrStop.start, (16).dp)
            top.linkTo(icon.top)
            bottom.linkTo(icon.bottom, 16.dp)
        }
        constrain(icon) {
            width = Dimension.wrapContent
            height = Dimension.fillToConstraints
            top.linkTo(box.top, 8.dp)
            start.linkTo(parent.start, 8.dp)
            bottom.linkTo(box.bottom, 8.dp)
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
            height = Dimension.wrapContent
            width = Dimension.fillToConstraints
            start.linkTo(icon.end, 16.dp)
            end.linkTo(playOrStop.start, 16.dp)
            bottom.linkTo(box.bottom, 16.dp)
        }
        constrain(lyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(downArrow) {
            top.linkTo(parent.bottom)
            end.linkTo(parent.end, 16.dp)
        }
        constrain(searchLyricsScreen) {
            top.linkTo(parent.top, 50.dp)
            start.linkTo(parent.end, 50.dp)
        }
        constrain(goSearchLyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(tieLyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(editLyrics){
            start.linkTo(parent.end, 16.dp)
            bottom.linkTo(downArrow.top, 16.dp)
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
            top.linkTo(parent.top, 50.dp)
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
        constrain(lyrics) {
            bottom.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(downArrow) {
            top.linkTo(lyrics.top)
            end.linkTo(parent.end, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(searchLyricsScreen) {
            top.linkTo(parent.top, 50.dp)
            start.linkTo(parent.end, 50.dp)
        }
        constrain(goSearchLyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(tieLyrics) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
        }
        constrain(editLyrics){
            start.linkTo(parent.end, 16.dp)
            bottom.linkTo(downArrow.top, 16.dp)
        }
    }
    constraintSet(MusicVM.MotionScenes.FORTH) {
        allViews.except(lyrics, downArrow, searchLyricsScreen, tieLyrics, editLyrics).forEach {
            constrain(it) {
                bottom.linkTo(parent.top)
                centerHorizontallyTo(parent)
            }
        }
        constrain(lyrics) {
            top.linkTo(parent.top, 50.dp)
            centerHorizontallyTo(parent)
        }
        constrain(downArrow) {
            end.linkTo(parent.end, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(goSearchLyrics) {
            top.linkTo(lyrics.bottom, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
            centerHorizontallyTo(parent)
        }
        constrain(searchLyricsScreen) {
            top.linkTo(parent.top, 50.dp)
            start.linkTo(parent.end, 50.dp)
        }
        constrain(tieLyrics) {
            top.linkTo(parent.bottom, 16.dp)
            centerHorizontallyTo(parent)
        }
        constrain(editLyrics){
            end.linkTo(downArrow.end)
            start.linkTo(downArrow.start)
            bottom.linkTo(downArrow.top, 16.dp)
        }
    }
    constraintSet(MusicVM.MotionScenes.FIFTH) {
        allViews.except(
            lyrics, downArrow, goSearchLyrics, searchLyricsScreen, musicName, tieLyrics, editLyrics
        ).forEach {
            constrain(it) {
                bottom.linkTo(parent.top)
                centerHorizontallyTo(parent)
            }
        }
        constrain(musicName) {
            top.linkTo(parent.top, 50.dp)
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)
        }
        constrain(lyrics) {
            top.linkTo(parent.top, 50.dp)
            end.linkTo(parent.start, 16.dp)
        }
        constrain(downArrow) {
            rotationZ = 90f
            end.linkTo(parent.end, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
        }
        constrain(goSearchLyrics) {
            centerVerticallyTo(parent)
            end.linkTo(parent.start, 16.dp)
        }
        constrain(searchLyricsScreen) {
            top.linkTo(musicName.bottom, 50.dp)
            bottom.linkTo(tieLyrics.top, 16.dp)
            centerHorizontallyTo(parent)
//            width = Dimension.fillToConstraints
        }
        constrain(tieLyrics) {
            bottom.linkTo(parent.bottom, 16.dp)
            centerHorizontallyTo(parent)
        }
        constrain(editLyrics){
            start.linkTo(parent.end, 16.dp)
            bottom.linkTo(downArrow.top, 16.dp)
        }
    }
    defaultTransition(firstSet, secondSet)
}

fun <T> List<T>.except(vararg values: T): List<T> = filterNot { it in values.toSet() }