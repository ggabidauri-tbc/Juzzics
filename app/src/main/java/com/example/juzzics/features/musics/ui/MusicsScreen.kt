package com.example.juzzics.features.musics.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.MotionScene
import coil.compose.AsyncImage
import com.example.juzzics.R
import com.example.juzzics.common.base.extensions.returnIfNull
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.listen
import com.example.juzzics.common.uiComponents.SimpleFAB
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.components.MusicListItem
import com.example.juzzics.features.musics.ui.components.MusicProgress
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMotionApi::class)
@Composable
fun MusicsScreen(
    states: BaseState,
    uiEvent: SharedFlow<UiEvent>,
    onAction: (Action) -> Unit
) {
    with2(states, MusicVM) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val lazyListState = rememberLazyListState()
                uiEvent.listen {
                    when (it) {
                        is MusicVM.ScrollToPositionUiEvent -> lazyListState.animateScrollToItem(it.position)
                    }
                }

                MotionLayout(
                    motionScene = MotionScene {
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

                        val firstSet = constraintSet("0") {
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
                        val secondSet = constraintSet("1") {
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

                        constraintSet("2") {
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
                        constraintSet("3") {
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
                    },
                    constraintSetName = SCENE_NAME(),
                    animationSpec = tween(800),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
//                        .onSizeChanged { size -> componentHeight = size.height.toFloat() }
                ) {
                    val clickedMusic = CLICKED_MUSIC<MusicFileUi>()
                    val isPlaying = IS_PLAYING() ?: false
                    val list = MUSIC_LIST<ImmutableList<MusicFileUi>>()?.toMutableStateList()
                        ?: remember { mutableStateListOf() }
                    val scene = SCENE_NAME<String>()

                    ReorderableList(
                        modifier = Modifier.layoutId("music_list"),
                        list = list,
                        lazyListState = lazyListState,
                        onDragEnd = { onAction(MusicVM.OnDragEndAction(it)) },
                        onClick = {
                            onAction(MusicVM.PlayMusicAction(it))
                            val newScene = if (scene == "0"
                                || (it.id != clickedMusic?.id && !it.isPlaying)
                                || it.id == clickedMusic?.id
                            ) "1" else "0"
                            onAction(MusicVM.UpdateSceneAction(newScene))
                        }
                    ) {
                        MusicListItem(it)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .layoutId("box")
                            .clickable {
                                onAction(
                                    MusicVM.UpdateSceneAction(
                                        (if (scene == "1") "2" else "1")
                                    )
                                )
                            }
                    )
                    Text(
                        text = "Now Playing:",
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .layoutId("now_playing"),
                    )
                    Text(
                        text = clickedMusic?.title ?: "No song playing",
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .basicMarquee(
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 1000
                            )
                            .layoutId("music_name"),
                        fontSize = 18.sp
                    )
                    AsyncImage(
                        model = clickedMusic?.icon.returnIfNull { R.drawable.ic_launcher_foreground },
                        contentDescription = "music icon",
                        placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                        modifier = Modifier
                            .layoutId("icon")
                            .aspectRatio(
                                //if (swipeAbleState.currentValue == "Bottom") 2f else 0.8f,//todo: animate
                                1f,
                                true
                            )
                            .clickable {
                                val newScene = if (scene == "1") "2" else "1"
                                onAction(MusicVM.UpdateSceneAction(newScene))
                            },
                        contentScale = ContentScale.Fit,
                    )
                    MusicProgress(
                        modifier = Modifier.layoutId("music_progress"),
                        mediaPlayer = MEDIA_PLAYER(),
                        seekTo = { onAction(MusicVM.SeekToAction(it)) })

                    SimpleFAB(
                        modifier = Modifier.layoutId("bt_prev"),
                        text = "Prev",
                        image = Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    ) { onAction(MusicVM.PlayPrevAction) }

                    SimpleFAB(
                        modifier = Modifier.layoutId("bt_next"),
                        text = "Next",
                        image = Icons.AutoMirrored.Filled.KeyboardArrowRight
                    ) { onAction(MusicVM.PlayNextAction) }

                    SimpleFAB(
                        modifier = Modifier.layoutId("play_or_stop"),
                        image = if (isPlaying) Icons.Filled.Warning else Icons.Filled.PlayArrow,
                        text = if (isPlaying) "Pause" else "Play"
                    ) { onAction(MusicVM.PlayOrPauseAction) }

                    Text(
                        modifier = Modifier
                            .layoutId("find_lyrics")
                            .clickable {
                                onAction(MusicVM.FindLyricsAction)
                                val newScene = if (scene == "2") "3" else "2"
                                onAction(MusicVM.UpdateSceneAction(newScene))
                            },
                        text = "Find Lyrics"
                    )
                }
            }
        }
    }
}