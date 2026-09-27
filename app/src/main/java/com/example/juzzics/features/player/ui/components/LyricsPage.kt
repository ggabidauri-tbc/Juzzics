package com.example.juzzics.features.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.parseLrc
import com.example.juzzics.features.musics.ui.components.SyncedLyricsView
import com.example.juzzics.features.musics.ui.components.toClock
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName
import com.example.juzzics.features.player.ui.vm.PlayerVM
import com.example.juzzics.features.player.ui.vm.PlayerVM.Companion.PAGE_FULL

/**
 * Lyrics of the playing song, over the full player: karaoke-style when synced, plain text
 * otherwise, or a way to find them. Pull down at the top (or press back) to go back.
 *
 * @param active on screen: only then does the karaoke fill animate (battery)
 */
@Composable
fun LyricsPage(
    song: MusicFileUi,
    states: BaseState,
    active: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    with2(states, PlayerVM) {
        Column(
            modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- the song, small ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(songId = song.id, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(44.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(song.title.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val playing = IS_PLAYING()
                IconButton(onClick = { onAction(PlayerVM.TogglePlayAction) }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
                }
                IconButton(onClick = { onAction(PlayerVM.ShowLyricsSearchAction(true)) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Find other lyrics")
                }
                IconButton(onClick = { onAction(PlayerVM.PageAction(PAGE_FULL)) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back to the player")
                }
            }

            val synced = song.syncedLyrics
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    synced != null -> SyncedLyricsView(
                        lines = remember(synced) { parseLrc(synced) },
                        positionMs = { PROGRESS.stateValue().positionMs },
                        isPlaying = IS_PLAYING(),
                        active = active,
                        offsetMs = LYRICS_OFFSETS()[song.id] ?: 0L,
                        onShift = { onAction(PlayerVM.ShiftLyricsAction(it)) },
                        onSeek = { onAction(PlayerVM.SeekToMsAction(it)) },
                        modifier = Modifier.fillMaxSize()
                    )

                    song.lyrics.isNotBlank() -> Text(
                        song.lyrics,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    )

                    else -> NoLyrics(
                        status = !LYRICS_STATUS,
                        onFind = { onAction(PlayerVM.ShowLyricsSearchAction(true)) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoLyrics(status: String, onFind: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val searching = status.startsWith("Looking")
        if (searching) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
        Text(
            status.ifBlank { "No lyrics for this song yet" },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (!searching) {
            Text(
                "Search by the song's name. Lyrics you pick stay on the phone, so they work offline too.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(onClick = onFind) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Find lyrics", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * "Find lyrics": the song's name (cleaned up, editable) and the results, best first.
 * One tap on a result saves it to the song (with Undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchSheet(states: BaseState, onAction: (Action) -> Unit) {
    with2(states, PlayerVM) {
        ModalBottomSheet(
            onDismissRequest = { onAction(PlayerVM.ShowLyricsSearchAction(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Find lyrics", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = !SEARCH_TITLE,
                    onValueChange = { onAction(PlayerVM.SearchTitleAction(it)) },
                    label = { Text("Song") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = !SEARCH_ARTIST,
                    onValueChange = { onAction(PlayerVM.SearchArtistAction(it)) },
                    label = { Text("Artist (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onAction(PlayerVM.SearchLyricsAction) }),
                    trailingIcon = {
                        IconButton(onClick = { onAction(PlayerVM.SearchLyricsAction) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (SEARCHING_LYRICS()) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Box(Modifier.height(4.dp))
            }

            val results = LYRICS_CANDIDATES()
            when {
                results == null -> Box(Modifier.height(120.dp))
                results.isEmpty() -> Text(
                    "Nothing found. Try only the song's name, without \"(Official Video)\" and such.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
                else -> {
                    Text(
                        "Tap the right one to use it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(results) { index, candidate ->
                            CandidateCard(candidate, onClick = { onAction(PlayerVM.UseLyricsAction(index)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: LyricsCandidate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(candidate.artist.ifBlank { null }, candidate.durationMs?.toClock()).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val badges = listOfNotNull(
                "Synced".takeIf { candidate.isSynced },
                "Same length as your song".takeIf { candidate.sameLength },
            )
            if (badges.isNotEmpty()) {
                Text(
                    badges.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // the first lines, to recognize the right song
            Text(
                candidate.lyrics.lyrics.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
