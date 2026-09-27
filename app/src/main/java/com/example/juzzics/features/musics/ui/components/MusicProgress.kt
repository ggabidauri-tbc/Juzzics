package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.player.PlaybackProgress

/**
 * Song progress. Mini player: a thin line; full player: a seek slider with times.
 * [expansion] (0 = mini player, 1 = full player) follows the swipe, so the two cross-fade
 * instead of snapping. [progress] is a lambda so only this Composable updates as the song plays.
 */
@Composable
fun MusicProgress(
    modifier: Modifier,
    progress: () -> PlaybackProgress,
    expansion: () -> Float,
    seekTo: (Float) -> Unit
) {
    val current = progress()
    /** while dragging, the slider shows the finger's position instead of the song's */
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val sliderUsable by remember { derivedStateOf { expansion() > 0.5f } }

    Box(modifier.clipToBounds()) {
        LinearProgressIndicator(
            progress = { current.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 1f - expansion() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = expansion() }
        ) {
            Slider(
                value = dragValue ?: current.fraction,
                enabled = sliderUsable,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let(seekTo)
                    dragValue = null
                }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val shownPosition = dragValue?.let { (it * current.durationMs).toLong() } ?: current.positionMs
                Text(shownPosition.toClock(), style = MaterialTheme.typography.labelSmall)
                Text(current.durationMs.toClock(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 185000 -> "3:05" */
fun Long.toClock(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
