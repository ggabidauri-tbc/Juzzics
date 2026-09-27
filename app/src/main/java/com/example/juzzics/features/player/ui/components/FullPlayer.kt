package com.example.juzzics.features.player.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.features.musics.ui.components.toClock
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName
import com.example.juzzics.features.player.PlaybackProgress
import com.example.juzzics.features.player.RepeatMode
import com.example.juzzics.features.player.ui.vm.PlayerVM
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_LYRICS
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_MINI

/**
 * The full player (the artwork itself is drawn by the overlay into [artworkSpace]):
 * where it plays from, the song, a seek bar, the controls, and lyrics / queue / playlist.
 */
@Composable
fun FullPlayer(
    song: MusicFileUi,
    states: BaseState,
    artworkSpace: Dp,
    onAction: (Action) -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    with2(states, PlayerVM) {
        Column(
            modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- top: close + where it's playing from ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onAction(PlayerVM.PageAction(PAGE_MINI)) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                    Text(
                        QUEUE_SOURCE() ?: "Your library",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                // balances the close button, so the title stays centered
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(16.dp + artworkSpace + 28.dp))

            // ---- the song ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title.orEmpty().ifBlank { "Unknown song" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            animationMode = MarqueeAnimationMode.Immediately,
                            initialDelayMillis = 1500
                        )
                    )
                    Text(
                        song.artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val liked = song.id in LIKED_IDS()
                IconButton(onClick = { onAction(PlayerVM.ToggleLikeAction) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Remove from Liked songs" else "Add to Liked songs",
                        tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SeekBar(
                progress = { PROGRESS.stateValue() },
                onSeek = { onAction(PlayerVM.SeekAction(it)) }
            )

            // ---- controls ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val active = MaterialTheme.colorScheme.primary
                val inactive = LocalContentColor.current.copy(alpha = 0.6f)
                val shuffle = SHUFFLE()
                IconButton(onClick = { onAction(PlayerVM.ShuffleAction) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (shuffle) active else inactive
                    )
                }
                IconButton(onClick = { onAction(PlayerVM.PreviousAction) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous song", modifier = Modifier.size(36.dp))
                }
                val playing = IS_PLAYING()
                FilledIconButton(
                    onClick = { onAction(PlayerVM.TogglePlayAction) },
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { onAction(PlayerVM.NextAction) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next song", modifier = Modifier.size(36.dp))
                }
                val repeat = REPEAT()
                IconButton(onClick = { onAction(PlayerVM.RepeatAction) }) {
                    Icon(
                        if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = when (repeat) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat this song"
                        },
                        tint = if (repeat != RepeatMode.OFF) active else inactive
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ---- bottom: lyrics, add to playlist, queue ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = { onAction(PlayerVM.PageAction(PAGE_LYRICS)) }) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (song.lyrics.isBlank()) "Lyrics" else if (song.syncedLyrics != null) "Lyrics · synced" else "Lyrics",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
                }
                IconButton(onClick = { onAction(PlayerVM.ShowQueueAction(true)) }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Up next")
                }
            }
        }
    }
}

/**
 * Seek bar with the time on both sides. While dragging it shows where you'd land;
 * [progress] is a lambda so only this part redraws as the song plays.
 */
@Composable
private fun SeekBar(progress: () -> PlaybackProgress, onSeek: (Float) -> Unit) {
    val current = progress()
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragging ?: current.fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onSeek)
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.let { (it * current.durationMs).toLong() } ?: current.positionMs
            val muted = LocalContentColor.current.copy(alpha = 0.7f)
            Text(shown.toClock(), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(
                "-" + (current.durationMs - shown).coerceAtLeast(0).toClock(),
                style = MaterialTheme.typography.labelSmall,
                color = muted
            )
        }
    }
}
