package com.example.juzzics.features.player.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.artwork.SongArtwork
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.motion.rememberSceneSwipeState
import com.example.juzzics.common.uiComponents.motion.sceneSwipeable
import com.example.juzzics.features.musics.ui.components.QueueSheet
import com.example.juzzics.features.musics.ui.components.followMiniPlayerSwipe
import com.example.juzzics.features.musics.ui.components.rememberArtworkColor
import com.example.juzzics.features.musics.ui.components.rememberMiniPlayerSwipeState
import com.example.juzzics.features.musics.ui.components.swipeForNextPrevious
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.player.ui.components.FullPlayer
import com.example.juzzics.features.player.ui.components.LyricsPage
import com.example.juzzics.features.player.ui.components.LyricsSearchSheet
import com.example.juzzics.features.player.ui.components.MiniPlayerBar
import com.example.juzzics.features.player.ui.vm.PlayerVM
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_FULL
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_LYRICS
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_MINI
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

/** mini player: height, and gap to the screen edges / bottom bar */
val MiniPlayerHeight = 64.dp
private val MiniPlayerMargin = 8.dp

/** space screens leave free at the bottom while the mini player shows */
val MiniPlayerSpace = MiniPlayerHeight + MiniPlayerMargin * 2

/**
 * The player, drawn above every tab. One continuous swipe position moves it between three
 * pages: mini player (a bar above the bottom bar) -> full player -> lyrics (slide up from the
 * bottom). Swipe up / down, tap, or press back; everything follows the finger.
 * Nothing playing: nothing shown.
 *
 * @param bottomInset height of the bottom bar: the mini player sits on it
 */
@Composable
fun PlayerOverlay(
    states: BaseState,
    onAction: (Action) -> Unit,
    bottomInset: Dp,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    with2(states, PlayerVM) {
        val song = CURRENT() ?: return@with2
        val page = PAGE()
        val swipe = rememberSceneSwipeState(
            sceneIndex = page,
            lastIndex = PAGE_LYRICS,
            // the lyrics scroll: pulling down at their top goes back to the full player
            nestedScrollAbove = PAGE_FULL,
            onSettled = { _, to -> onAction(PlayerVM.PageAction(to)) }
        )
        val songSwipe = rememberMiniPlayerSwipeState()
        val next = { onAction(PlayerVM.NextAction) }
        val previous = { onAction(PlayerVM.PreviousAction) }

        // back goes one page down (mini <- full <- lyrics); with gesture navigation it peeks first
        PredictiveBackHandler(enabled = page != PAGE_MINI) { events ->
            try {
                events.collect { swipe.previewBack(it.progress) }
                onAction(PlayerVM.PageAction((PAGE.stateValue() - 1).coerceAtLeast(PAGE_MINI)))
            } catch (e: CancellationException) {
                swipe.cancelBackPreview()
                throw e
            }
        }

        // 0 = mini, 1 = full, 2 = lyrics, and everything in between while swiping
        val expansion = { swipe.value.coerceIn(0f, 1f) }
        val lyricsShown = { (swipe.value - 1f).coerceIn(0f, 1f) }
        val showFull by remember { derivedStateOf { swipe.value > 0.01f } }
        val showMini by remember { derivedStateOf { swipe.value < 0.99f } }
        val showLyrics by remember { derivedStateOf { swipe.value > 1.01f } }

        val miniColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val background = MaterialTheme.colorScheme.background
        val artworkColor = rememberArtworkColor(SongArtwork(song.id))
        val fullColor by animateColorAsState(artworkColor ?: MaterialTheme.colorScheme.surfaceContainer, label = "playerColor")
        val density = LocalDensity.current
        val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

        // the player is drawn above the app's Scaffold (which normally sets the text / icon
        // color): without this they'd fall back to black, unreadable on the dark player
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .nestedScroll(swipe.nestedScrollConnection)
                // a swipe across the whole height moves one page
                .onSizeChanged { swipe.swipeDistancePx = it.height * 0.6f }
        ) {
            val screenW = maxWidth
            val screenH = maxHeight
            val mini = PlayerRect(
                x = MiniPlayerMargin,
                y = screenH - bottomInset - MiniPlayerMargin - MiniPlayerHeight,
                width = screenW - MiniPlayerMargin * 2,
                height = MiniPlayerHeight,
            )
            val full = PlayerRect(0.dp, 0.dp, screenW, screenH)
            val artSize = min((screenW - 48.dp).value, (screenH * 0.42f).value).dp
            val miniArt = PlayerRect(mini.x + 8.dp, mini.y + 8.dp, 48.dp, 48.dp)
            val fullArt = PlayerRect((screenW - artSize) / 2, statusBarTop + 72.dp, artSize, artSize)

            // ---------------------- the player's surface ----------------------
            Box(
                Modifier
                    .placeAt { lerp(mini, full, expansion()) }
                    .graphicsLayer {
                        val corner = (1f - expansion()) * 16.dp.toPx()
                        shape = RoundedCornerShape(corner)
                        clip = true
                        shadowElevation = (1f - expansion()) * 6.dp.toPx()
                    }
                    .drawBehind {
                        val e = expansion()
                        drawRect(miniColor)
                        if (e > 0f) {
                            drawRect(
                                Brush.verticalGradient(listOf(fullColor, lerp(fullColor, background, 0.85f))),
                                alpha = e
                            )
                        }
                    }
                    .sceneSwipeable(swipe)
                    .then(
                        if (page == PAGE_MINI) {
                            Modifier
                                .clickable { onAction(PlayerVM.PageAction(PAGE_FULL)) }
                                .swipeForNextPrevious(songSwipe, onNext = next, onPrevious = previous)
                        } else {
                            // the full player catches taps (nothing underneath should get them)
                            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        }
                    )
            ) {
                if (showMini) {
                    MiniPlayerBar(
                        song = song,
                        isPlaying = IS_PLAYING(),
                        progress = { PROGRESS.stateValue().fraction },
                        onPlayPause = { onAction(PlayerVM.TogglePlayAction) },
                        onNext = next,
                        songSwipe = songSwipe,
                        modifier = Modifier.graphicsLayer { alpha = (1f - expansion() * 3f).coerceIn(0f, 1f) }
                    )
                }
                if (showFull) {
                    FullPlayer(
                        song = song,
                        states = states,
                        artworkSpace = artSize,
                        onAction = onAction,
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(screenW, screenH)
                            .graphicsLayer {
                                // appears in the second half of the swipe, moving up a little
                                val e = expansion()
                                alpha = ((e - 0.4f) / 0.6f).coerceIn(0f, 1f) * (1f - lyricsShown())
                                translationY = (1f - e) * 48.dp.toPx()
                            }
                    )
                }
            }

            // ---------------------- the artwork glides between mini and full ----------------------
            ArtworkImage(
                songId = song.id,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .placeAt { lerp(miniArt, fullArt, expansion()) }
                    .graphicsLayer {
                        val e = expansion()
                        shape = RoundedCornerShape((8f + 12f * e).dp.toPx())
                        clip = true
                        shadowElevation = e * 16.dp.toPx()
                        alpha = 1f - lyricsShown()
                    }
                    .followMiniPlayerSwipe(songSwipe, enabled = true)
                    .sceneSwipeable(swipe)
                    .swipeForNextPrevious(songSwipe, onNext = next, onPrevious = previous)
                    .clickable(enabled = page == PAGE_MINI) { onAction(PlayerVM.PageAction(PAGE_FULL)) }
            )

            // ---------------------- lyrics slide up over the full player ----------------------
            if (showLyrics || page == PAGE_LYRICS) {
                LyricsPage(
                    song = song,
                    states = states,
                    active = page == PAGE_LYRICS,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (1f - lyricsShown()) * size.height }
                        .drawBehind { drawRect(lerp(fullColor, background, 0.7f)) }
                        .sceneSwipeable(swipe)
                )
            }
        }

        }

        if (SHOW_QUEUE()) {
            QueueSheet(
                queue = QUEUE(),
                currentIndex = QUEUE_INDEX(),
                onPlay = { onAction(PlayerVM.PlayQueueIndexAction(it)) },
                onReorder = { onAction(PlayerVM.ReorderQueueAction(it)) },
                onRemove = { onAction(PlayerVM.RemoveFromQueueAction(it)) },
                onDismiss = { onAction(PlayerVM.ShowQueueAction(false)) },
            )
        }
        if (SHOW_LYRICS_SEARCH()) {
            LyricsSearchSheet(states = states, onAction = onAction)
        }
    }
}

/** a rectangle of the player on screen (dp) */
private data class PlayerRect(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

private fun lerp(a: PlayerRect, b: PlayerRect, t: Float) = PlayerRect(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t,
    width = a.width + (b.width - a.width) * t,
    height = a.height + (b.height - a.height) * t,
)

/**
 * Places the element at [rect] (in the overlay), read while laying out: a swipe moves it
 * every frame without recomposing anything.
 */
private fun Modifier.placeAt(rect: () -> PlayerRect): Modifier = layout { measurable, constraints ->
    val r = rect()
    val width = r.width.roundToPx().coerceAtLeast(0)
    val height = r.height.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(r.x.roundToPx(), r.y.roundToPx())
    }
}
