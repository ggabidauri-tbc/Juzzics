package com.example.juzzics.features.musics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOut
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import coil.compose.AsyncImage
import com.example.juzzics.R
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.returnIfNull
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.listen
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.common.uiComponents.SimpleFAB
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.common.util.converters.ofHeight
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.musics.ui.components.MusicListItem
import com.example.juzzics.features.musics.ui.components.MusicProgress
import com.example.juzzics.features.musics.ui.components.motionScene
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIFTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FORTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD
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
            BackHandler(!SCENE_NAME != FIRST) {
                when (SCENE_NAME.stateValue<String>()) {
                    FIFTH -> onAction(MusicVM.UpdateSceneAction(FORTH))
                    FORTH -> onAction(MusicVM.UpdateSceneAction(THIRD))
                    THIRD -> onAction(MusicVM.UpdateSceneAction(SECOND))
                    SECOND -> {
                        onAction(MusicVM.UpdateSceneAction(FIRST))
                        onAction(MusicVM.PlayOrPauseAction(true))
                    }
                }
            }
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
                        onClick = {
                            if (SCENE_NAME.stateValue<String>() !in listOf(THIRD, FORTH))
                                onAction(MusicVM.PlayMusicAction(it))
                        }
                    ) {
                        MusicListItem(it)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .layoutId("box")
                            .then(
                                if (!SCENE_NAME == SECOND)
                                    Modifier.clickable { onAction(MusicVM.BoxClickAction) }
                                else Modifier
                            )
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
                            ),
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
                    ) { onAction(MusicVM.PlayOrPauseAction()) }

                    Column(
                        Modifier.layoutId("lyrics"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier
//                                .layoutId("lyrics")
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                                .clickable { onAction(MusicVM.FindLyricsClickedAction) }
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            text = "Lyrics"
                        )
                        HorizontalDivider(Modifier.padding(8.dp))
                    }

                    Crossfade(
                        clickedMusic?.lyrics.isNullOrBlank(),
                        modifier = Modifier.layoutId("go_search_lyrics"),
                        label = ""
                    ) {
                        if (it) {
                            Text(
                                "Click here to search lyrics for this song",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.DarkGray)
                                    .clickable { onAction(MusicVM.UpdateSceneAction(FIFTH)) }
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        } else {
                            Text(
                                clickedMusic?.lyrics.orEmpty(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 50.dp)
                                    .verticalScroll(rememberScrollState(0))
                            )
                        }
                    }

                    uiEvent.BaseHandler(
                        modifier = Modifier.layoutId("search_lyrics_screen"),
                        content = {
                            Column(
                                Modifier
                                    .padding(top = 8.ofHeight(), bottom = 10.ofHeight()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TextField(
                                    value = !ARTIST,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    ),
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                    onValueChange = { onAction(MusicVM.UpdateArtistAction(it)) })
                                TextField(
                                    value = !TITLE,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                    onValueChange = { onAction(MusicVM.UpdateTitleAction(it)) })

                                Button(
                                    modifier = Modifier.padding(16.dp),
                                    onClick = { onAction(MusicVM.FetchLyricsAction) }) {
                                    Text("Search Lyrics")
                                }
                                Text(
                                    text = LYRICS<LyricsDomain>()?.lyrics.orEmpty(),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .verticalScroll(rememberScrollState(0))
                                )
                            }
                        }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .layoutId("tie_lyrics")
                            .alpha(if (LYRICS<LyricsDomain>()?.lyrics.isNullOrBlank()) 0f else 1f)
                    ) {
                        HorizontalDivider(Modifier.padding(16.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "tie lyrics to the song",
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onAction(MusicVM.TieLyrics) }
                                .background(Color.DarkGray)
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }


                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "arrow_down",
                        modifier = Modifier
                            .layoutId("arrow_down")
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAction(MusicVM.ArrowDownClickAction) }
                    )
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "edit_lyrics",
                        modifier = Modifier
                            .layoutId("edit_lyrics")
                            .alpha(if (LYRICS<LyricsDomain>()?.lyrics.isNullOrBlank()) 0f else 1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAction(MusicVM.UpdateSceneAction(FIFTH)) }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}