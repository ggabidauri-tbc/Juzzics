package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain

/**
 * Search lyrics by hand: artist + title fields (pre-filled with the cleaned-up song name),
 * then pick the right song from the results, then see its lyrics (and tie them to the song).
 */
@Composable
fun LyricsSearchPanel(
    artist: String,
    title: String,
    picked: LyricsDomain?,
    candidates: List<LyricsCandidate>,
    onArtistChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (index: Int) -> Unit,
    onBackToResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = artist,
            onValueChange = onArtistChange,
            label = { Text("Artist") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Song title") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text("Search lyrics", modifier = Modifier.padding(start = 8.dp))
        }

        when {
            picked != null -> PickedLyrics(
                lyrics = picked,
                canGoBack = candidates.size > 1,
                onBackToResults = onBackToResults,
                modifier = Modifier.weight(1f)
            )

            candidates.isNotEmpty() -> {
                Text(
                    "Pick the right song",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(candidates) { index, candidate ->
                        CandidateRow(candidate, onClick = { onPick(index) })
                    }
                }
            }

            else -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tip: keep only the artist and the song's name, e.g. without \"(Official Video)\" or \"Lyrics\"",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PickedLyrics(
    lyrics: LyricsDomain,
    canGoBack: Boolean,
    onBackToResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                TextButton(onClick = onBackToResults) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text("Other results", modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (lyrics.synced != null) {
                Text(
                    "✓ time-synced",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = lyrics.lyrics,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun CandidateRow(candidate: LyricsCandidate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                candidate.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(candidate.artist.ifBlank { null }, candidate.durationMs?.toClock())
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (candidate.isSynced || candidate.sameLength) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (candidate.isSynced) SuggestionChip(onClick = onClick, label = { Text("Time-synced") })
                    if (candidate.sameLength) SuggestionChip(onClick = onClick, label = { Text("Same length as your song") })
                }
            }
        }
    }
}
