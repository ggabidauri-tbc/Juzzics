package com.example.juzzics.features.musics.ui.components

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.lyrics.domain.model.LyricLine
import com.example.juzzics.features.lyrics.domain.model.indexAt
import kotlin.math.abs

/** one tap on "Earlier" / "Later" moves the lyrics this much */
private const val SHIFT_STEP_MS = 250L

/** a line is sung in about this long per character (when the next line is far away) */
private const val MS_PER_CHAR = 110L
private const val MIN_LINE_MS = 1_500L

/**
 * Lyrics that follow the song, karaoke style: the current line fills in as it's sung and is
 * kept in view; tapping a line jumps the song there.
 *
 * @param offsetMs this song's timing fix: positive shows the lyrics later
 * @param active the lyrics are on screen: only then is the fill animated every frame (battery)
 */
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    isPlaying: Boolean,
    active: Boolean,
    offsetMs: Long,
    onShift: (deltaMs: Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val smoothPosition by rememberSmoothPosition(positionMs, isPlaying && active)
    val lyricsPosition = { smoothPosition - offsetMs }
    val current by remember(lines, offsetMs) { derivedStateOf { lines.indexAt(lyricsPosition()) } }
    val listState = rememberLazyListState()

    LaunchedEffect(current) {
        // follow the song, unless the user is scrolling through the lyrics
        if (current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }

    Column(modifier) {
        TimingRow(offsetMs, onShift)
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == current
                val modifierLine = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs + offsetMs) }
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                if (isCurrent) {
                    KaraokeLine(
                        text = line.text.ifBlank { "♪" },
                        fill = { lineFill(lines, index, lyricsPosition()) },
                        modifier = modifierLine
                    )
                } else {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = modifierLine
                    )
                }
            }
        }
    }
}

/** the current line: sung part in the accent color, the rest dimmed */
@Composable
private fun KaraokeLine(text: String, fill: () -> Float, modifier: Modifier) {
    val sung = MaterialTheme.colorScheme.primary
    val notYet = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    // read here (not in the parent) so only this line redraws while it fills
    val filledChars = (fill() * text.length).toInt().coerceIn(0, text.length)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = sung)) { append(text.substring(0, filledChars)) }
            withStyle(SpanStyle(color = notYet)) { append(text.substring(filledChars)) }
        },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.Unspecified,
        modifier = modifier
    )
}

/**
 * how much of line [index] is sung at [positionMs], 0..1.
 * LRC only says when a line starts, so its length is guessed from the next line and its text.
 */
private fun lineFill(lines: List<LyricLine>, index: Int, positionMs: Long): Float {
    val line = lines[index]
    val untilNext = lines.getOrNull(index + 1)?.let { it.timeMs - line.timeMs } ?: Long.MAX_VALUE
    val byText = (line.text.length * MS_PER_CHAR).coerceAtLeast(MIN_LINE_MS)
    val length = minOf(untilNext, byText).coerceAtLeast(1)
    return ((positionMs - line.timeMs).toFloat() / length).coerceIn(0f, 1f)
}

/** "Lyrics early? Earlier | +0.5 s | Later": shifts this song's lyrics */
@Composable
private fun TimingRow(offsetMs: Long, onShift: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onShift(-SHIFT_STEP_MS) }) { Text("‹ Earlier") }
        Text(
            if (offsetMs == 0L) "Lyrics timing"
            else "${if (offsetMs > 0) "+" else "−"}${"%.2f".format(abs(offsetMs) / 1000f)} s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                // tap to reset
                .clickable(enabled = offsetMs != 0L) { onShift(-offsetMs) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        TextButton(onClick = { onShift(SHIFT_STEP_MS) }) { Text("Later ›") }
    }
}

/**
 * The player reports its position 4 times a second; for a smooth karaoke fill this moves it
 * on every frame in between while [animate] (playing and on screen).
 */
@Composable
private fun rememberSmoothPosition(positionMs: () -> Long, animate: Boolean): State<Long> {
    val position = remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(animate) {
        if (!animate) {
            snapshotFlow { positionMs() }.collect { position.longValue = it }
            return@LaunchedEffect
        }
        var sample = positionMs()
        var sampledAt = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis {
                val now = SystemClock.uptimeMillis()
                val latest = positionMs()
                if (latest != sample) {
                    sample = latest
                    sampledAt = now
                }
                val estimate = sample + (now - sampledAt)
                val shown = position.longValue
                // a fresh report slightly behind our estimate: don't jump back (a seek does)
                if (estimate >= shown || shown - estimate > 300) position.longValue = estimate
            }
        }
    }
    return position
}
