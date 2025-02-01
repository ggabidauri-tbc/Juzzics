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
import com.example.juzzics.features.musics.ui.components.motionScene
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
                    motionScene = motionScene,
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

                    ReorderableList(
                        modifier = Modifier.layoutId("music_list"),
                        list = list,
                        lazyListState = lazyListState,
                        onDragEnd = { onAction(MusicVM.OnDragEndAction(it)) },
                        onClick = { onAction(MusicVM.PlayMusicAction(it)) }
                    ) {
                        MusicListItem(it)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .layoutId("box")
                            .clickable { onAction(MusicVM.BoxClickAction) }
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
                            .clickable { onAction(MusicVM.ImageClickAction) },
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
                            .clickable { onAction(MusicVM.FindLyricsClickedAction) },
                        text = "Find Lyrics"
                    )
                }
            }
        }
    }
}