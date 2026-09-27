package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.player.RepeatMode

/** Like, add to playlist, shuffle, repeat and the "Up next" queue, under the full player's buttons. */
@Composable
fun PlayerExtraControls(
    modifier: Modifier,
    isLiked: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onLike: () -> Unit,
    /** null hides the button */
    onAddToPlaylist: (() -> Unit)?,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueue: () -> Unit,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.6f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onLike) {
            Icon(
                if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isLiked) "Remove from liked" else "Like",
                tint = if (isLiked) active else inactive
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
            }
        }
        IconButton(onClick = onShuffle) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                tint = if (shuffle) active else inactive
            )
        }
        IconButton(onClick = onRepeat) {
            Icon(
                if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
                tint = if (repeatMode != RepeatMode.OFF) active else inactive
            )
        }
        IconButton(onClick = onQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Up next")
        }
    }
}
