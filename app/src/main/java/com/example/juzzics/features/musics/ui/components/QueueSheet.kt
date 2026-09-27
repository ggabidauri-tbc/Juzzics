package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.uiComponents.SongRow
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName

/**
 * The play queue. Tap a song to jump to it, long-press + drag to reorder,
 * swipe a song away to remove it (not the one playing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<MusicFileUi>,
    currentIndex: Int,
    onPlay: (index: Int) -> Unit,
    onReorder: (songIds: List<Long>) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (queue.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "The queue is empty",
                message = "Play a song, or use \"Add to queue\" from a song's menu"
            )
            return@ModalBottomSheet
        }
        Text(
            "Hold and drag to reorder · swipe a song away to remove it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        val currentQueue by rememberUpdatedState(queue)
        val items = remember(queue) { queue.toMutableStateList() }
        val currentId = queue.getOrNull(currentIndex)?.id
        fun indexOf(song: MusicFileUi) = currentQueue.indexOfFirst { it.id == song.id }

        ReorderableList(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 24.dp),
            list = items,
            key = { it.id },
            lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0)),
            onDragEnd = { reordered -> onReorder(reordered.map { it.id }) },
            onClick = { song -> onPlay(indexOf(song)) }
        ) { song ->
            if (song.id == currentId) {
                QueueRow(song, isCurrent = true)
            } else {
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.Settled) false
                        else {
                            onRemove(indexOf(song))
                            true
                        }
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                ) {
                    QueueRow(song, isCurrent = false)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(song: MusicFileUi, isCurrent: Boolean) {
    SongRow(
        title = song.title.orEmpty(),
        subtitle = if (isCurrent) "Playing now" else song.artistName,
        songId = song.id,
        isCurrent = isCurrent,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 4.dp)
    )
}
