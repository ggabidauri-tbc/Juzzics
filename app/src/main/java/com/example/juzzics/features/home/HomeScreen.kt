package com.example.juzzics.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.features.home.components.NameFixesCard
import com.example.juzzics.features.home.components.NameFixesDialog
import com.example.juzzics.features.home.components.TripPrepCard
import com.example.juzzics.features.home.ui.vm.HomeVM
import com.example.juzzics.features.musics.domain.model.MusicFileDomain

/** Recently played, most played and playlists. Tapping plays and opens the player. */
@Composable
fun HomeScreen(
    states: BaseState,
    onAction: (Action) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    with2(states, HomeVM) {
        val recent = RECENTLY_PLAYED()
        val mostPlayed = MOST_PLAYED()
        // an empty "Liked songs" isn't worth showing on Home
        val playlists = PLAYLISTS().filterNot { it.isLikedSongs && it.songs.isEmpty() }

        fun play(action: Action) {
            onAction(action)
            onOpenPlayer()
        }

        val suggestions = NAME_SUGGESTIONS()
        if (SHOW_NAME_FIXES() && suggestions.isNotEmpty()) {
            NameFixesDialog(
                suggestions = suggestions,
                onApply = { onAction(HomeVM.ApplyNameFixesAction(it)) },
                onDismiss = { onAction(HomeVM.ShowNameFixesAction(false)) }
            )
        }

        Surface(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                item {
                    Text(
                        "Juzzics",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    TripPrepCard(
                        state = LYRICS_PREP(),
                        onStart = { onAction(HomeVM.StartTripPrepAction) },
                        onStop = { onAction(HomeVM.StopTripPrepAction) },
                        onDismissMessage = { onAction(HomeVM.DismissTripPrepMessageAction) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                }
                if (suggestions.isNotEmpty()) {
                    item {
                        NameFixesCard(
                            count = suggestions.size,
                            onOpen = { onAction(HomeVM.ShowNameFixesAction(true)) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        )
                    }
                }

                if (recent.isEmpty() && playlists.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Headphones,
                            title = "Welcome to Juzzics",
                            message = "Play some music from the Musics tab. Your recently and most played songs will show up here.",
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                }

                if (recent.isNotEmpty()) {
                    item { SectionTitle("Recently played") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(recent, key = { _, song -> song.id }) { index, song ->
                                SongCard(song) { play(HomeVM.PlaySongsAction(recent, index, source = "Recently played")) }
                            }
                        }
                    }
                }

                if (mostPlayed.isNotEmpty()) {
                    item { SectionTitle("Most played") }
                    itemsIndexed(mostPlayed, key = { _, (song, _) -> "most_${song.id}" }) { index, (song, count) ->
                        SongRow(
                            position = index + 1,
                            song = song,
                            playCount = count,
                            onClick = { play(HomeVM.PlaySongsAction(mostPlayed.map { it.first }, index, source = "Most played")) }
                        )
                    }
                }

                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Your playlists") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable(enabled = playlist.songs.isNotEmpty()) {
                                            play(HomeVM.PlayPlaylistAction(playlist.id))
                                        }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (playlist.songs.isEmpty()) "Empty" else "${playlist.songs.size} songs · tap to play",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SongCard(song: MusicFileDomain, onClick: () -> Unit) {
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        ArtworkImage(
            songId = song.id,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            song.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SongRow(position: Int, song: MusicFileDomain, playCount: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$position",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(28.dp)
        )
        ArtworkImage(songId = song.id, modifier = Modifier.size(44.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(song.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist?.takeUnless { it == "<unknown>" } ?: "Unknown artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            "$playCount plays",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
