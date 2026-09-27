package com.example.juzzics.common.uiComponents.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.MotionLayoutScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Fling speed (px/s) above which release goes to the next/previous scene instead of the nearest. */
private const val FLING_VELOCITY = 1000f

/** Part of the layout height one swipe has to travel to move a full scene. */
private const val SWIPE_DISTANCE_RATIO = 0.6f

/** How far towards the previous scene the predictive back gesture peeks. */
private const val BACK_PREVIEW_FRACTION = 0.3f

/** Duration of scene changes that come from clicks (same as the old MotionScene animation). */
private const val CLICK_ANIMATION_MS = 800

/**
 * Drives a MotionLayout through a chain of scenes (index 0..lastIndex) with a continuous
 * [position]. Up to [horizontalFrom] scenes change vertically (swipe up = forward, down = back),
 * after it horizontally (swipe left = forward, right = back).
 * Scene changes made elsewhere (clicks) are animated through the same position,
 * so swipes and clicks never fight.
 */
@Stable
class SceneSwipeState internal constructor(
    initialIndex: Int,
    private val lastIndex: Int,
    private val scope: CoroutineScope,
) {
    private val position = Animatable(initialIndex.toFloat())

    /** Scene the owner (ViewModel) currently has. */
    internal var settledIndex = initialIndex
    internal var onSettled: (from: Int, to: Int) -> Unit = { _, _ -> }

    /** Scenes above this index hold scrollable content: pull down at its top goes back. */
    internal var nestedScrollAbove = Int.MAX_VALUE

    /** Scene index from which on transitions are horizontal. */
    internal var horizontalFrom = lastIndex + 1

    /** light tick when a swipe snaps to another scene */
    internal var haptics: HapticFeedback? = null

    internal var swipeDistancePx by mutableFloatStateOf(1f)
    internal var horizontalSwipeDistancePx by mutableFloatStateOf(1f)

    var isDragging by mutableStateOf(false)
        private set

    private var draggedByNestedScroll = false

    val value: Float get() = position.value
    internal val segment: Int get() = position.value.toInt().coerceIn(0, lastIndex - 1)
    internal val progressInSegment: Float get() = position.value - segment
    private val isBetweenScenes: Boolean get() = position.value != position.value.roundToInt().toFloat()

    private val lastVerticalIndex get() = minOf(horizontalFrom, lastIndex)

    /** Vertical swipes work up to [horizontalFrom]. */
    internal val canSwipeVertically get() = position.value <= lastVerticalIndex

    /** Horizontal swipes work from [horizontalFrom] on. */
    internal val canSwipeHorizontally get() = horizontalFrom <= lastIndex && position.value >= horizontalFrom

    /** finger up / left = negative delta = forward */
    internal fun dragBy(delta: Float, horizontal: Boolean = false) {
        val min = if (horizontal) horizontalFrom else 0
        val max = if (horizontal) lastIndex else lastVerticalIndex
        val current = position.value
        if (current < min || current > max) return
        val distance = if (horizontal) horizontalSwipeDistancePx else swipeDistancePx
        val next = (current - delta / distance).coerceIn(min.toFloat(), max.toFloat())
        scope.launch(start = CoroutineStart.UNDISPATCHED) { position.snapTo(next) }
    }

    internal fun onDragStarted() {
        isDragging = true
    }

    internal fun settle(velocity: Float) {
        isDragging = false
        val current = position.value
        val target = when {
            velocity < -FLING_VELOCITY -> ceil(current)
            velocity > FLING_VELOCITY -> floor(current)
            else -> current.roundToInt().toFloat()
        }.toInt().coerceIn(0, lastIndex)
        scope.launch {
            position.animateTo(target.toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
            val from = settledIndex
            if (target != from) {
                settledIndex = target
                haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettled(from, target)
            }
        }
    }

    /**
     * Predictive back: while the back gesture is in progress ([progress] 0..1), peek a bit
     * towards the previous scene. Committing changes the scene (animates on from here),
     * [cancelBackPreview] returns.
     */
    fun previewBack(progress: Float) {
        if (settledIndex == 0) return
        val peek = settledIndex - progress.coerceIn(0f, 1f) * BACK_PREVIEW_FRACTION
        scope.launch(start = CoroutineStart.UNDISPATCHED) { position.snapTo(peek) }
    }

    fun cancelBackPreview() {
        scope.launch { position.animateTo(settledIndex.toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    internal suspend fun animateToScene(index: Int) {
        settledIndex = index
        if (!isDragging) position.animateTo(index.toFloat(), tween(CLICK_ANIMATION_MS))
    }

    internal val draggableState = DraggableState { dragBy(it) }
    internal val horizontalDraggableState = DraggableState { dragBy(it, horizontal = true) }

    /** Lets scrollable content (lyrics) hand over to the scene swipe once it's scrolled to the top. */
    internal val nestedScrollConnection = object : NestedScrollConnection {
        private val inScrollableScenes
            get() = position.value > nestedScrollAbove && position.value <= lastVerticalIndex

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // halfway between scenes: keep moving the scenes before the content scrolls again
            if (source != NestedScrollSource.UserInput || !draggedByNestedScroll || !isBetweenScenes) {
                return Offset.Zero
            }
            dragBy(available.y)
            return Offset(0f, available.y)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // content is at its top and the finger keeps pulling down
            if (source != NestedScrollSource.UserInput || !inScrollableScenes || available.y <= 0f) {
                return Offset.Zero
            }
            if (!draggedByNestedScroll) {
                draggedByNestedScroll = true
                onDragStarted()
            }
            dragBy(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!draggedByNestedScroll) return Velocity.Zero
            draggedByNestedScroll = false
            settle(available.y)
            return available
        }
    }
}

/**
 * @param sceneIndex scene the owner currently has; when it changes (e.g. after a click)
 * the layout animates there.
 * @param onSettled called when a swipe ends on a different scene than [sceneIndex].
 * @param nestedScrollAbove scenes with index greater than this contain vertically scrollable
 * content; pulling it down while at its top swipes back.
 * @param horizontalFrom transitions after this scene index are swiped horizontally
 * (left = forward). By default every transition is vertical.
 */
@Composable
fun rememberSceneSwipeState(
    sceneIndex: Int,
    lastIndex: Int,
    nestedScrollAbove: Int = Int.MAX_VALUE,
    horizontalFrom: Int = lastIndex + 1,
    onSettled: (from: Int, to: Int) -> Unit,
): SceneSwipeState {
    val scope = rememberCoroutineScope()
    val state = remember { SceneSwipeState(sceneIndex, lastIndex, scope) }
    state.haptics = LocalHapticFeedback.current
    state.onSettled = onSettled
    state.nestedScrollAbove = nestedScrollAbove
    state.horizontalFrom = horizontalFrom
    LaunchedEffect(sceneIndex) {
        if (sceneIndex != state.settledIndex) state.animateToScene(sceneIndex)
    }
    return state
}

/** Makes this element start scene swipes (vertical and, where configured, horizontal). */
fun Modifier.sceneSwipeable(state: SceneSwipeState, enabled: Boolean = true): Modifier =
    draggable(
        state = state.draggableState,
        orientation = Orientation.Vertical,
        enabled = (enabled && state.canSwipeVertically) || state.isDragging,
        onDragStarted = { state.onDragStarted() },
        onDragStopped = { velocity -> state.settle(velocity) }
    ).draggable(
        state = state.horizontalDraggableState,
        orientation = Orientation.Horizontal,
        enabled = (enabled && state.canSwipeHorizontally) || state.isDragging,
        onDragStarted = { state.onDragStarted() },
        onDragStopped = { velocity -> state.settle(velocity) }
    )

/** MotionLayout that shows the pair of [scenes] around the swipe position. */
@OptIn(ExperimentalMotionApi::class)
@Composable
fun SwipeableMotionLayout(
    state: SceneSwipeState,
    scenes: List<ConstraintSet>,
    modifier: Modifier = Modifier,
    content: @Composable MotionLayoutScope.() -> Unit,
) {
    val segment = state.segment
    MotionLayout(
        start = scenes[segment],
        end = scenes[segment + 1],
        progress = state.progressInSegment,
        modifier = modifier.onSizeChanged {
            state.swipeDistancePx = it.height * SWIPE_DISTANCE_RATIO
            state.horizontalSwipeDistancePx = it.width * SWIPE_DISTANCE_RATIO
        }
    ) {
        content()
    }
}
