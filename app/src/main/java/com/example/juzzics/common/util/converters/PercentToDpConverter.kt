package com.example.juzzics.common.util.converters

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/*** transforms percentage of the screen width in dp */
@Composable
fun Int.ofWidth(): Dp {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        context.resources.displayMetrics.widthPixels.toDp()
    }
    return (screenWidth * this / 100f)
}

const val FigmaScreenWidth = 375f

/** dpRelative value calculated relative to width */
val Int.dpR: Dp
    @Composable
    get() = percentageRatio.ofWidth()

/** spRelative value calculated relative to width */
val Int.spR: TextUnit
    @Composable
    get() = percentageRatio.spOfWidth()

val Int.percentageRatio: Float
    @Composable
    get() = (this / FigmaScreenWidth).times(100f)

/*** transforms percentage of the screen width in sp */
@Composable
fun Int.spOfWidth(): TextUnit {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        context.resources.displayMetrics.widthPixels.toDp().toSp()
    }
    return (screenWidth * this / 100f)
}

/*** transforms percentage of the screen width in sp */
@Composable
fun Float.spOfWidth(): TextUnit {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        (context.resources.displayMetrics.widthPixels.toDp().toSp())
    }
    return (screenWidth * (this - 0.32f) / 100f)
}

/*** transforms percentage of the screen height in dp */
@Composable
fun Int.ofHeight(): Dp {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        context.resources.displayMetrics.heightPixels.toDp()
    }
    return (screenWidth * this / 100f)
}

/*** transforms percentage of the screen width in dp */
@Composable
fun Float.ofWidth(): Dp {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        context.resources.displayMetrics.widthPixels.toDp()
    }
    return (screenWidth * (this - 0.32f) / 100f)
}

/*** transforms percentage of the screen height in dp */
@Composable
fun Float.ofHeight(): Dp {
    val context = LocalContext.current
    val screenWidth = LocalDensity.current.run {
        context.resources.displayMetrics.heightPixels.toDp()
    }
    return (screenWidth * this / 100f)
}