package com.example.juzzics.features.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.musics.ui.components.MiniPlayerSwipeState
import com.example.juzzics.features.musics.ui.components.followMiniPlayerSwipe
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName

/**
 * The mini player's contents (the artwork is drawn by the overlay, it glides to the full
 * player): song, play/pause, next, and a thin progress line along the bottom.
 */
@Composable
fun MiniPlayerBar(
    song: MusicFileUi,
    isPlaying: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    songSwipe: MiniPlayerSwipeState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                // room for the artwork on the left
                .padding(start = 68.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .followMiniPlayerSwipe(songSwipe, enabled = true)
            ) {
                Text(
                    song.title.orEmpty().ifBlank { "Unknown song" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next song") }
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .padding(horizontal = 12.dp)
                .align(Alignment.BottomCenter),
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}
