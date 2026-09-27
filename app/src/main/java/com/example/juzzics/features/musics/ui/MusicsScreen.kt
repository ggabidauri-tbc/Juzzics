package com.example.juzzics.features.musics.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ExperimentalMotionApi
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.LOADING
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.common.artwork.SongArtwork
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.SimpleFAB
import com.example.juzzics.common.uiComponents.motion.SwipeableMotionLayout
import com.example.juzzics.common.uiComponents.motion.rememberSceneSwipeState
import com.example.juzzics.common.uiComponents.motion.sceneSwipeable
import com.example.juzzics.features.lyrics.domain.model.parseLrc
import com.example.juzzics.features.musics.ui.components.SyncedLyricsView
import com.example.juzzics.features.musics.ui.components.followMiniPlayerSwipe
import com.example.juzzics.features.musics.ui.components.rememberMiniPlayerSwipeState
import com.example.juzzics.features.musics.ui.components.LyricsSearchPanel
import com.example.juzzics.features.musics.ui.components.MusicProgress
import com.example.juzzics.features.musics.ui.components.PlayerExtraControls
import com.example.juzzics.features.musics.ui.components.QueueSheet
import com.example.juzzics.features.musics.ui.components.musicSceneOrder
import com.example.juzzics.features.musics.ui.components.musicScenes
import com.example.juzzics.features.musics.ui.components.rememberArtworkColor
import com.example.juzzics.features.musics.ui.components.swipeForNextPrevious
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIFTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FOURTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.SECOND
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMotionApi::class)
@Composable
fun MusicsScreen(
    states: BaseState,
    uiEvent: Flow<UiEvent>,
    onAction: (Action) -> Unit,
    onAddToPlaylist: ((MusicFileUi) -> Unit)? = null
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

                // scenes change by clicks (via the ViewModel) or by swiping; both go through sceneSwipe
                val sceneName = SCENE_NAME()
                val sceneIndex = musicSceneOrder.indexOf(sceneName).coerceAtLeast(0)
                val sceneSwipe = rememberSceneSwipeState(
                    sceneIndex = sceneIndex,
                    lastIndex = musicSceneOrder.lastIndex,
                    nestedScrollAbove = musicSceneOrder.indexOf(THIRD),
                    // lyrics -> search lyrics slides in from the side, so that swipe is horizontal
                    horizontalFrom = musicSceneOrder.indexOf(FOURTH),
                    onSettled = { _, to ->
                        onAction(MusicVM.UpdateSceneAction(musicSceneOrder[to]))
                        // same as the back button: closing the mini player pauses
                        if (musicSceneOrder[to] == FIRST) onAction(MusicVM.PlayOrPauseAction(true))
                    }
                )

                // back steps through the scenes; with gesture navigation the layout peeks back first
                PredictiveBackHandler(enabled = sceneName != FIRST) { backEvents ->
                    try {
                        backEvents.collect { sceneSwipe.previewBack(it.progress) }
                        when (SCENE_NAME.stateValue()) {
                            FIFTH -> onAction(MusicVM.UpdateSceneAction(FOURTH))
                            FOURTH -> onAction(MusicVM.UpdateSceneAction(THIRD))
                            THIRD -> onAction(MusicVM.UpdateSceneAction(SECOND))
                            SECOND -> {
                                onAction(MusicVM.UpdateSceneAction(FIRST))
                                onAction(MusicVM.PlayOrPauseAction(true))
                            }
                        }
                    } catch (e: CancellationException) {
                        sceneSwipe.cancelBackPreview()
                        throw e
                    }
                }

                SwipeableMotionLayout(
                    state = sceneSwipe,
                    scenes = musicScenes,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(sceneSwipe.nestedScrollConnection)
                        // no mini player on the first scene, so nothing to swipe there
                        .sceneSwipeable(sceneSwipe, enabled = sceneIndex > 0)
                ) {
                    val clickedMusic = CLICKED_MUSIC()
                    val isPlaying = IS_PLAYING()
                    val likedIds = LIKED_IDS()
                    MusicListSection(
                        states = states,
                        lazyListState = lazyListState,
                        onAction = onAction,
                        onAddToPlaylist = onAddToPlaylist,
                        modifier = Modifier.layoutId("music_list"),
                    )

                    val miniSwipe = rememberMiniPlayerSwipeState()
                    val inMiniPlayer = sceneName == SECOND
                    // mini player and full player: swipe sideways for next / previous song
                    val canSwipeSongs = sceneName == SECOND || sceneName == THIRD
                    val swipeSongs = Modifier.swipeForNextPrevious(
                        state = miniSwipe,
                        onNext = { onAction(MusicVM.PlayNextAction) },
                        onPrevious = { onAction(MusicVM.PlayPrevAction) }
                    )

                    // full player takes its background color from the artwork
                    val artworkColor = rememberArtworkColor(clickedMusic?.id?.let { SongArtwork(it) })
                    // 0 = mini player, 1 = full player; follows the swipe
                    val expansion = { (sceneSwipe.value - 1f).coerceIn(0f, 1f) }
                    val miniColor = MaterialTheme.colorScheme.secondaryContainer
                    val fullColor by animateColorAsState(
                        targetValue = artworkColor ?: miniColor,
                        label = "playerBackground"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // blends from the mini player's color to the artwork's while swiping
                            // (read while drawing, so the swipe doesn't recompose anything)
                            .drawBehind { drawRect(lerp(miniColor, fullColor, expansion())) }
                            .layoutId("box")
                            .then(
                                if (sceneName == SECOND) Modifier.clickable { onAction(MusicVM.BoxClickAction) }
                                else Modifier
                            )
                            .then(if (canSwipeSongs) swipeSongs else Modifier)
                            // the list is still laid out behind the player: without this it
                            // would take the drags in the full player and scroll invisibly
                            .sceneSwipeable(sceneSwipe, enabled = sceneIndex > 0)
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
                            .followMiniPlayerSwipe(miniSwipe, canSwipeSongs)
                            .basicMarquee(
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 1000
                            )
                            .layoutId("music_name"),
                        fontSize = 18.sp
                    )
                    ArtworkImage(
                        songId = clickedMusic?.id,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .layoutId("icon")
                            .followMiniPlayerSwipe(miniSwipe, canSwipeSongs)
                            .then(if (canSwipeSongs) swipeSongs else Modifier)
                            .sceneSwipeable(sceneSwipe, enabled = sceneIndex > 0)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true),
                    )
                    MusicProgress(
                        modifier = Modifier.layoutId("music_progress"),
                        progress = { PROGRESS.stateValue() },
                        expansion = expansion,
                        seekTo = { onAction(MusicVM.SeekToAction(it)) })

                    SimpleFAB(
                        modifier = Modifier.layoutId("bt_prev"),
                        text = "Previous",
                        image = Icons.Filled.SkipPrevious
                    ) { onAction(MusicVM.PlayPrevAction) }

                    SimpleFAB(
                        modifier = Modifier.layoutId("bt_next"),
                        text = "Next",
                        image = Icons.Filled.SkipNext
                    ) { onAction(MusicVM.PlayNextAction) }

                    SimpleFAB(
                        modifier = Modifier
                            .layoutId("play_or_stop")
                            .followMiniPlayerSwipe(miniSwipe, inMiniPlayer),
                        image = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        text = if (isPlaying) "Pause" else "Play"
                    ) { onAction(MusicVM.PlayOrPauseAction()) }

                    PlayerExtraControls(
                        modifier = Modifier.layoutId("extra_controls"),
                        isLiked = clickedMusic != null && clickedMusic.id in likedIds,
                        shuffle = SHUFFLE(),
                        repeatMode = REPEAT(),
                        onLike = { clickedMusic?.let { onAction(MusicVM.ToggleLikeAction(it)) } },
                        onAddToPlaylist = onAddToPlaylist?.let { add -> { clickedMusic?.let(add) } },
                        onShuffle = { onAction(MusicVM.ToggleShuffleAction) },
                        onRepeat = { onAction(MusicVM.CycleRepeatAction) },
                        onQueue = { onAction(MusicVM.ShowQueueAction(true)) },
                    )

                    Column(
                        Modifier.layoutId("lyrics"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier
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
                        val synced = clickedMusic?.syncedLyrics
                        when {
                            it -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    (!LYRICS_STATUS).ifEmpty { "Click here to search lyrics for this song" },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.DarkGray)
                                        .clickable { onAction(MusicVM.UpdateSceneAction(FIFTH)) }
                                        .padding(vertical = 8.dp, horizontal = 16.dp)
                                )
                            }

                            synced != null -> SyncedLyricsView(
                                lines = remember(synced) { parseLrc(synced) },
                                positionMs = { PROGRESS.stateValue().positionMs },
                                onSeek = { ms -> onAction(MusicVM.SeekToMsAction(ms)) },
                                modifier = Modifier.fillMaxSize()
                            )

                            else -> Text(
                                clickedMusic?.lyrics.orEmpty(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState(0))
                                    .padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    }

                    // the screen's one collector of MusicVM's events (toasts + list scrolling)
                    uiEvent.BaseHandler(
                        loading = LOADING(),
                        modifier = Modifier.layoutId("search_lyrics_screen"),
                        onEvent = {
                            if (it is MusicVM.ScrollToPositionUiEvent) lazyListState.animateScrollToItem(it.position)
                        },
                        content = {
                            LyricsSearchPanel(
                                artist = !ARTIST,
                                title = !TITLE,
                                picked = LYRICS(),
                                candidates = LYRICS_CANDIDATES(),
                                onArtistChange = { onAction(MusicVM.UpdateArtistAction(it)) },
                                onTitleChange = { onAction(MusicVM.UpdateTitleAction(it)) },
                                onSearch = { onAction(MusicVM.FetchLyricsAction) },
                                onPick = { onAction(MusicVM.PickLyricsAction(it)) },
                                onBackToResults = { onAction(MusicVM.BackToLyricsResultsAction) },
                            )
                        }
                    )

                    val hasFoundLyrics = !LYRICS()?.lyrics.isNullOrBlank()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .layoutId("tie_lyrics")
                            .alpha(if (hasFoundLyrics) 1f else 0f)
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
                            .alpha(if (hasFoundLyrics) 1f else 0f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAction(MusicVM.UpdateSceneAction(FIFTH)) }
                            .padding(4.dp)
                    )
                }
            }

            if (SHOW_QUEUE()) {
                QueueSheet(
                    queue = QUEUE(),
                    currentIndex = QUEUE_INDEX(),
                    onPlay = { onAction(MusicVM.PlayQueueIndexAction(it)) },
                    onReorder = { onAction(MusicVM.ReorderQueueAction(it)) },
                    onRemove = { onAction(MusicVM.RemoveFromQueueAction(it)) },
                    onDismiss = { onAction(MusicVM.ShowQueueAction(false)) },
                )
            }
        }
    }
}
