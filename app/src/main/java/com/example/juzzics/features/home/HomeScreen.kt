package com.example.juzzics.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.ScreenHeader
import com.example.juzzics.common.uiComponents.SectionHeader
import com.example.juzzics.common.uiComponents.SongRow
import com.example.juzzics.features.home.components.NameFixesCard
import com.example.juzzics.features.home.components.NameFixesDialog
import com.example.juzzics.features.home.components.TripPrepCard
import com.example.juzzics.features.home.ui.vm.HomeVM
import com.example.juzzics.features.lyrics.data.LyricsCoverage
import com.example.juzzics.features.lyrics.data.LyricsPrepState
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.LIKED_SONGS_PLAYLIST_ID
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import java.util.Calendar

/**
 * Home: a greeting, quick actions, how ready you are for offline (lyrics), and what you
 * listen to: jump back in, on repeat, your playlists. Tapping plays (the mini player shows).
 */
@Composable
fun HomeScreen(
    states: BaseState,
    onAction: (Action) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenNearby: () -> Unit,
) {
    with2(states, HomeVM) {
        val recent = RECENTLY_PLAYED()
        val mostPlayed = MOST_PLAYED()
        // an empty "Liked songs" isn't worth showing on Home
        val playlists = PLAYLISTS().filterNot { it.isLikedSongs && it.songs.isEmpty() }
        val hasLiked = PLAYLISTS().any { it.isLikedSongs && it.songs.isNotEmpty() }
        val suggestions = NAME_SUGGESTIONS()

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ScreenHeader(title = greeting(), subtitle = "What are we listening to?") }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAction(Icons.Filled.Shuffle, "Shuffle all", Modifier.weight(1f)) {
                        onAction(HomeVM.ShuffleAllAction)
                    }
                    if (hasLiked) {
                        QuickAction(Icons.Filled.Favorite, "Liked", Modifier.weight(1f)) {
                            onAction(HomeVM.PlayPlaylistAction(LIKED_SONGS_PLAYLIST_ID))
                        }
                    }
                    QuickAction(Icons.Filled.WifiTethering, "Nearby", Modifier.weight(1f), onClick = onOpenNearby)
                }
            }

            item {
                OfflineReadyRow(
                    coverage = LYRICS_COVERAGE(),
                    prep = LYRICS_PREP(),
                    onClick = { onAction(HomeVM.ShowTripPrepAction(true)) },
                )
            }
            if (suggestions.isNotEmpty()) {
                item {
                    NameFixesCard(
                        count = suggestions.size,
                        onOpen = { onAction(HomeVM.ShowNameFixesAction(true)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (recent.isEmpty() && mostPlayed.isEmpty()) {
                item { Welcome(onOpenLibrary) }
            }

            if (recent.isNotEmpty()) {
                item { SectionHeader("Jump back in") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recent, key = { _, song -> song.id }) { index, song ->
                            SongCard(song) { onAction(HomeVM.PlaySongsAction(recent, index, source = "Recently played")) }
                        }
                    }
                }
            }

            if (mostPlayed.isNotEmpty()) {
                item { SectionHeader("On repeat") }
                itemsIndexed(mostPlayed.take(5), key = { _, (song, _) -> "most_${song.id}" }) { index, (song, count) ->
                    SongRow(
                        title = song.title.orEmpty(),
                        subtitle = song.artist?.takeUnless { it == "<unknown>" }.orEmpty(),
                        detail = "$count plays",
                        songId = song.id,
                        leading = {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(20.dp)
                            )
                        },
                        onClick = { onAction(HomeVM.PlaySongsAction(mostPlayed.map { it.first }, index, source = "On repeat")) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                item { SectionHeader("Your playlists", actionLabel = "See all", onAction = onOpenLibrary) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(playlist) { onAction(HomeVM.PlayPlaylistAction(playlist.id)) }
                        }
                    }
                }
            }
        }

        if (SHOW_TRIP_PREP()) {
            TripPrepSheet(states, onAction)
        }
        if (SHOW_NAME_FIXES() && suggestions.isNotEmpty()) {
            NameFixesDialog(
                suggestions = suggestions,
                onApply = { onAction(HomeVM.ApplyNameFixesAction(it)) },
                onDismiss = { onAction(HomeVM.ShowNameFixesAction(false)) }
            )
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night"
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** "Offline lyrics: 420 of 600 songs", with a bar; opens trip prep */
@Composable
private fun OfflineReadyRow(coverage: LyricsCoverage?, prep: LyricsPrepState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Landscape, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                when {
                    prep.running && prep.total > 0 -> "Getting lyrics… ${prep.done} of ${prep.total}"
                    prep.running -> "Getting lyrics…"
                    coverage == null -> "Offline lyrics"
                    coverage.total == 0 -> "Offline lyrics"
                    coverage.withLyrics == coverage.total -> "Offline ready: all ${coverage.total} songs have lyrics"
                    else -> "Offline lyrics: ${coverage.withLyrics} of ${coverage.total} songs"
                },
                style = MaterialTheme.typography.titleSmall
            )
            val fraction = when {
                prep.running && prep.total > 0 -> prep.done.toFloat() / prep.total
                coverage != null && coverage.total > 0 -> coverage.withLyrics.toFloat() / coverage.total
                else -> 0f
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripPrepSheet(states: BaseState, onAction: (Action) -> Unit) {
    with2(states, HomeVM) {
        ModalBottomSheet(onDismissRequest = { onAction(HomeVM.ShowTripPrepAction(false)) }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                Text("Get ready for offline", style = MaterialTheme.typography.titleLarge)
                Text(
                    "While you have internet, Juzzics looks up lyrics for every song that doesn't have them yet " +
                            "and keeps them on the phone. Then they work in the mountains, on a plane, anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                TripPrepCard(
                    state = LYRICS_PREP(),
                    onStart = { onAction(HomeVM.StartTripPrepAction) },
                    onStop = { onAction(HomeVM.StopTripPrepAction) },
                    onDismissMessage = { onAction(HomeVM.DismissTripPrepMessageAction) },
                )
                Text(
                    "Going somewhere without signal? Save a map of the area too: Nearby › Friend radar › Map › Offline maps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun Welcome(onOpenLibrary: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            "Your music, anywhere",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Play something from your library. What you listen to shows up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Button(onClick = onOpenLibrary) { Text("Open your library") }
    }
}

@Composable
private fun SongCard(song: MusicFileDomain, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        ArtworkImage(
            songId = song.id,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(140.dp)
        )
        Text(
            song.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            song.artist?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistDomain, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        if (playlist.isLikedSongs) {
            Box(
                Modifier
                    .size(140.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            ArtworkImage(
                songId = playlist.songs.firstOrNull()?.id,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(140.dp)
            )
        }
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            if (playlist.songs.isEmpty()) "Empty" else "${playlist.songs.size} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
