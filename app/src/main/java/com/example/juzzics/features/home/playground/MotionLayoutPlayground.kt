package com.example.juzzics.features.home.playground

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.layoutId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ---------------------- click version (kept for reference) ----------------------

//@OptIn(ExperimentalMotionApi::class)
//@Composable
//fun HomeScreen(
//) {
//    /**this works with clicks*/
//    var sceneName by remember("sceneName") { mutableStateOf("0") }
//    MotionLayout(
//        motionScene = MotionScene {
//            val tFirst = createRefFor("text_first")
//            val tSecond = createRefFor("text_second")
//            val tThird = createRefFor("text_Third")
//            val firstSet = constraintSet("0") {
//                constrain(tFirst) {
//                    bottom.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tThird) {
//                    top.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tSecond) {
//                    top.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//            }
//            val secondSet = constraintSet("1") {
//                constrain(tFirst) {
//                    top.linkTo(parent.top)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tSecond) {
//                    bottom.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tThird) {
//                    top.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//            }
//            constraintSet("2") {
//                constrain(tFirst) {
//                    top.linkTo(parent.top)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tSecond) {
//                    top.linkTo(parent.top)
//                    centerHorizontallyTo(parent)
//                }
//                constrain(tThird) {
//                    bottom.linkTo(parent.bottom)
//                    centerHorizontallyTo(parent)
//                }
//            }
//            defaultTransition(firstSet, secondSet)
//        },
//        constraintSetName = sceneName,
//        animationSpec = tween(1200),
//        modifier = Modifier
//            .fillMaxWidth()
//            .fillMaxHeight()
//            .padding(top = 40.dp)
//    ) {
//        Text(
//            "Text First",
//            Modifier
//                .layoutId("text_first")
//                .clip(RoundedCornerShape(200.dp))
//                .background(Color.Blue)
//                .padding(16.dp)
//                .clickable { sceneName = if (sceneName == "1") "0" else "1" }
//        )
//        Text(
//            "Text Second",
//            Modifier
//                .layoutId("text_second")
//                .clip(RoundedCornerShape(200.dp))
//                .background(Color.Blue)
//                .padding(16.dp)
//                .clickable { sceneName = if (sceneName == "2") "1" else "2" }
//        )
//        Text(
//            "Text Third",
//            Modifier
//                .layoutId("text_Third")
//                .clip(RoundedCornerShape(200.dp))
//                .background(Color.Blue)
//                .padding(16.dp)
//        )
//    }
//}

// ---------------------- swipe version ----------------------

/** Scenes, from start to end. Swipe up goes forward, swipe down goes back. */
private const val FIRST_SCENE = 0f
private const val LAST_SCENE = 2f

/** Fling speed (px/s) above which release goes to the next/previous scene instead of the nearest. */
private const val FLING_VELOCITY = 1000f

/** Part of the screen height one swipe has to travel to move a full scene. */
private const val SWIPE_DISTANCE_RATIO = 0.6f

/**
 * Playground for MotionLayout driven by swipes (the Musics screen uses the same idea).
 * Not in the bottom bar: open it with the preview below.
 */
@OptIn(ExperimentalMotionApi::class)
@Composable
fun MotionLayoutPlayground() {
    val scenes = remember { homeScenes() }
    val scope = rememberCoroutineScope()

    /** Continuous position between scenes: 0.0 = scene 0, 1.5 = halfway between 1 and 2, etc. */
    var position by remember { mutableFloatStateOf(FIRST_SCENE) }
    var swipeDistancePx by remember { mutableFloatStateOf(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val dragState = rememberDraggableState { deltaY ->
        // finger up = negative delta = forward
        position = (position - deltaY / swipeDistancePx).coerceIn(FIRST_SCENE, LAST_SCENE)
    }

    fun Modifier.swipeable() = draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = { settleJob?.cancel() },
        onDragStopped = { velocity ->
            val target = when {
                velocity < -FLING_VELOCITY -> ceil(position)
                velocity > FLING_VELOCITY -> floor(position)
                else -> position.roundToInt().toFloat()
            }.coerceIn(FIRST_SCENE, LAST_SCENE)
            settleJob = scope.launch {
                animate(
                    initialValue = position,
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { value, _ -> position = value }
            }
        }
    )

    // at the first scene only the "playing" text starts a swipe,
    // after that the whole screen is swipeable (the text stays swipeable too)
    val atStart = position <= FIRST_SCENE

    // MotionLayout animates between two sets, so pick the pair around the current position
    val segment = position.toInt().coerceAtMost(scenes.lastIndex - 1)
    MotionLayout(
        start = scenes[segment],
        end = scenes[segment + 1],
        progress = position - segment,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(top = 40.dp)
            .onSizeChanged { swipeDistancePx = it.height * SWIPE_DISTANCE_RATIO }
            .then(if (atStart) Modifier else Modifier.swipeable())
    ) {
        Text(
            "Text First",
            Modifier
                .layoutId("text_first")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .swipeable()
                .padding(16.dp)
        )
        Text(
            "Text Second",
            Modifier
                .layoutId("text_second")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .padding(16.dp)
        )
        Text(
            "Text Third",
            Modifier
                .layoutId("text_Third")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .padding(16.dp)
        )
    }
}

/** Same three layouts as the click version: "0", "1" and "2". */
private fun homeScenes(): List<ConstraintSet> {
    fun scene(firstOnTop: Boolean, secondOnTop: Boolean, secondVisible: Boolean, thirdVisible: Boolean) =
        ConstraintSet {
            val tFirst = createRefFor("text_first")
            val tSecond = createRefFor("text_second")
            val tThird = createRefFor("text_Third")
            constrain(tFirst) {
                if (firstOnTop) top.linkTo(parent.top) else bottom.linkTo(parent.bottom)
                centerHorizontallyTo(parent)
            }
            constrain(tSecond) {
                when {
                    secondOnTop -> top.linkTo(parent.top)
                    secondVisible -> bottom.linkTo(parent.bottom)
                    else -> top.linkTo(parent.bottom)
                }
                centerHorizontallyTo(parent)
            }
            constrain(tThird) {
                if (thirdVisible) bottom.linkTo(parent.bottom) else top.linkTo(parent.bottom)
                centerHorizontallyTo(parent)
            }
        }
    return listOf(
        scene(firstOnTop = false, secondOnTop = false, secondVisible = false, thirdVisible = false),
        scene(firstOnTop = true, secondOnTop = false, secondVisible = true, thirdVisible = false),
        scene(firstOnTop = true, secondOnTop = true, secondVisible = true, thirdVisible = true),
    )
}

@Preview
@Composable
private fun MotionLayoutPlaygroundPreview() {
    MotionLayoutPlayground()
}
