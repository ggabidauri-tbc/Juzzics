package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.lyrics.domain.model.LyricLine
import com.example.juzzics.features.lyrics.domain.model.indexAt

/**
 * Lyrics that follow the song: the current line is highlighted and kept in view,
 * tapping a line jumps the song there.
 */
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by remember(lines) { derivedStateOf { lines.indexAt(positionMs()) } }
    val listState = rememberLazyListState()

    LaunchedEffect(current) {
        // follow the song, unless the user is scrolling through the lyrics
        if (current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == current
            Text(
                text = line.text.ifBlank { "♪" },
                textAlign = TextAlign.Center,
                style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
    }
}
