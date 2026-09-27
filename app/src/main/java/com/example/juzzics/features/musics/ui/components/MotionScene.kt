package com.example.juzzics.features.musics.ui.components

import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension
import com.example.juzzics.features.musics.ui.vm.MusicVM

/** Scene names in the order the screen moves through them (swipe up = next). */
val musicSceneOrder = listOf(
    MusicVM.MotionScenes.FIRST,
    MusicVM.MotionScenes.SECOND,
    MusicVM.MotionScenes.THIRD,
    MusicVM.MotionScenes.FOURTH,
    MusicVM.MotionScenes.FIFTH,
)

/** height of the full player's slider + times */
private val PROGRESS_HEIGHT = 64.dp

/** layoutIds used by the Musics screen. */
private class MusicRefs(scope: ConstraintSetScope) {
    val musicList = scope.createRefFor("music_list")
    val box = scope.createRefFor("box")
    val nowPlaying = scope.createRefFor("now_playing")
    val musicName = scope.createRefFor("music_name")
    val icon = scope.createRefFor("icon")
    val musicProgress = scope.createRefFor("music_progress")
    val btPrev = scope.createRefFor("bt_prev")
    val btNext = scope.createRefFor("bt_next")
    val playOrStop = scope.createRefFor("play_or_stop")
    val lyrics = scope.createRefFor("lyrics")
    val downArrow = scope.createRefFor("arrow_down")
    val goSearchLyrics = scope.createRefFor("go_search_lyrics")
    val searchLyricsScreen = scope.createRefFor("search_lyrics_screen")
    val tieLyrics = scope.createRefFor("tie_lyrics")
    val editLyrics = scope.createRefFor("edit_lyrics")
    val extraControls = scope.createRefFor("extra_controls")
    val allViews = listOf(
        musicList, box, nowPlaying, musicName, icon, musicProgress, btPrev, btNext, playOrStop,
        lyrics, downArrow, goSearchLyrics, searchLyricsScreen, tieLyrics, editLyrics, extraControls
    )
}

/** A scene with the Musics screen's layoutIds in scope. */
private fun musicScene(content: MusicRefs.(ConstraintSetScope) -> Unit) =
    ConstraintSet { MusicRefs(this).content(this) }

private val firstScene = musicScene { scope ->
    with(scope) {
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
        constrain(musicProgress) { height = Dimension.value(PROGRESS_HEIGHT) }
    }
}
private val secondScene = musicScene { scope ->
    with(scope) {
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
        // thin progress line along the top of the mini player
        constrain(musicProgress) {
            height = Dimension.value(3.dp)
            width = Dimension.fillToConstraints
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(box.top)
        }
        constrain(extraControls) {
            top.linkTo(parent.bottom)
            centerHorizontallyTo(parent)
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
}

private val thirdScene = musicScene { scope ->
    with(scope) {
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
            bottom.linkTo(parent.bottom, 150.dp)
        }
        constrain(extraControls) {
            top.linkTo(playOrStop.bottom, 12.dp)
            centerHorizontallyTo(parent)
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
            bottom.linkTo(musicProgress.top, 8.dp) // artwork above the slider
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
            height = Dimension.value(PROGRESS_HEIGHT) // slider + times
            bottom.linkTo(musicName.top, 16.dp)
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
}
private val fourthScene = musicScene { scope ->
    with(scope) {
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
        // lyrics (synced or plain) fill the space between the title and the bottom arrow
        constrain(goSearchLyrics) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(lyrics.bottom, 8.dp)
            bottom.linkTo(downArrow.top, 8.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
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
        constrain(musicProgress) { height = Dimension.value(PROGRESS_HEIGHT) }
    }
}
private val fifthScene = musicScene { scope ->
    with(scope) {
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
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(musicName.bottom, 24.dp)
            bottom.linkTo(tieLyrics.top, 8.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
        constrain(tieLyrics) {
            bottom.linkTo(parent.bottom, 16.dp)
            centerHorizontallyTo(parent)
        }
        constrain(editLyrics){
            start.linkTo(parent.end, 16.dp)
            bottom.linkTo(downArrow.top, 16.dp)
        }
        constrain(musicProgress) { height = Dimension.value(PROGRESS_HEIGHT) }
    }
}

/** Scenes in [musicSceneOrder] order. */
val musicScenes: List<ConstraintSet> =
    listOf(firstScene, secondScene, thirdScene, fourthScene, fifthScene)

fun <T> List<T>.except(vararg values: T): List<T> = filterNot { it in values.toSet() }
