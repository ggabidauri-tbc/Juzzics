package com.example.juzzics.features.musics.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Mini player swipe: the song follows the finger; past the threshold it slides out,
 * the next/previous song is started and slides in from the other side.
 */
@Stable
class MiniPlayerSwipeState internal constructor(private val scope: CoroutineScope) {
    internal val offset = Animatable(0f)

    internal fun dragBy(dx: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(offset.value + dx) }
    }

    internal fun release(width: Float, threshold: Float, onNext: () -> Unit, onPrevious: () -> Unit) {
        scope.launch {
            val x = offset.value
            when {
                x <= -threshold -> {
                    offset.animateTo(-width)
                    onNext()
                    offset.snapTo(width)
                    offset.animateTo(0f)
                }
                x >= threshold -> {
                    offset.animateTo(width)
                    onPrevious()
                    offset.snapTo(-width)
                    offset.animateTo(0f)
                }
                else -> offset.animateTo(0f)
            }
        }
    }
}

@Composable
fun rememberMiniPlayerSwipeState(): MiniPlayerSwipeState {
    val scope = rememberCoroutineScope()
    return remember { MiniPlayerSwipeState(scope) }
}

/** Mini player: swipe left = next song, swipe right = previous song. */
fun Modifier.swipeForNextPrevious(
    state: MiniPlayerSwipeState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = pointerInput(state) {
    val threshold = 72.dp.toPx()
    detectHorizontalDragGestures(
        onDragEnd = { state.release(size.width.toFloat(), threshold, onNext, onPrevious) },
        onDragCancel = { state.release(size.width.toFloat(), Float.MAX_VALUE, onNext, onPrevious) },
        onHorizontalDrag = { change, dx ->
            change.consume()
            state.dragBy(dx)
        }
    )
}

/** Moves the mini player's song details with the swipe ([enabled] only in the mini player scene). */
fun Modifier.followMiniPlayerSwipe(state: MiniPlayerSwipeState, enabled: Boolean): Modifier =
    if (!enabled) this else graphicsLayer {
        val x = state.offset.value
        translationX = x
        alpha = 1f - (abs(x) / (size.width * 3f)).coerceIn(0f, 0.6f)
    }
