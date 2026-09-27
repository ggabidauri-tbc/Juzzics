package com.example.juzzics.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.songs.NameSuggestion
import com.example.juzzics.features.lyrics.data.LyricsPrepState

/** "Trip prep": get lyrics for all songs while there's internet */
@Composable
fun TripPrepCard(
    state: LyricsPrepState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Landscape, contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text("Trip prep", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Get lyrics for all your songs now, so they're there without internet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.running) TextButton(onClick = onStop) { Text("Stop") }
                else FilledTonalButton(onClick = onStart) { Text("Start") }
            }
            if (state.running) {
                val progress = if (state.total > 0) state.done.toFloat() / state.total else 0f
                if (state.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.total == 0) "Checking your songs…"
                    else "${state.done} of ${state.total} songs · ${state.found} got lyrics",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.message?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissMessage) { Text("OK") }
                }
            }
        }
    }
}

/** "Fix N song names": opens [NameFixesDialog] */
@Composable
fun NameFixesCard(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text("Fix $count song name${if (count == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cleaner names found while looking up lyrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

/**
 * Each song with its suggested name, checked by default; "Rename" applies the checked ones.
 * Only what Juzzics shows changes: the files stay as they are.
 */
@Composable
fun NameFixesDialog(
    suggestions: List<NameSuggestion>,
    onApply: (acceptedIds: Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var accepted by remember(suggestions) { mutableStateOf(suggestions.map { it.songId }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix song names") },
        text = {
            Column {
                Text(
                    "Unchecked songs keep their name. Your files aren't changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(suggestions, key = { it.songId }) { suggestion ->
                        val checked = suggestion.songId in accepted
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    accepted = if (checked) accepted - suggestion.songId else accepted + suggestion.songId
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    suggestion.currentTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    suggestion.suggested.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (suggestion.suggested.artist.isNotBlank()) {
                                    Text(
                                        suggestion.suggested.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(accepted) }) {
                Text(if (accepted.isEmpty()) "Keep all names" else "Rename ${accepted.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
