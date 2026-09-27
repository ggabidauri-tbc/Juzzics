package com.example.juzzics.features.playlists.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.uiComponents.PageHeader
import com.example.juzzics.common.uiComponents.SongRow
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.components.toClock
import com.example.juzzics.features.musics.ui.model.artistName
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM

/**
 * The Library's Playlists tab: your playlists (Liked songs first) and, when one is open, its
 * songs. Starting playback shows the mini player.
 */
@Composable
fun PlaylistsContent(
    states: BaseState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    with2(first = states, second = PlaylistsVM) {
        val playlists = PLAYLIST_LIST()
        // look the selected playlist up in the (live) list, so edits show right away
        val selectedPlaylist = SELECTED_PLAYLIST_ID()?.let { id -> playlists.find { it.id == id } }

        Box(modifier.fillMaxSize()) {
            if (selectedPlaylist != null) {
                BackHandler { onAction(PlaylistsVM.SelectPlaylistAction(null)) }
                PlaylistDetail(playlist = selectedPlaylist, onAction = onAction)
            } else {
                PlaylistList(
                    playlists = playlists,
                    onCreate = { onAction(PlaylistsVM.ToggleCreateDialogAction(true)) },
                    onOpen = { onAction(PlaylistsVM.SelectPlaylistAction(it.id)) },
                )
            }
        }

        if (SHOW_CREATE_DIALOG()) {
            NameDialog(
                title = "New playlist",
                name = !NEW_PLAYLIST_NAME,
                onNameChange = { onAction(PlaylistsVM.UpdateNewPlaylistNameAction(it)) },
                confirmText = "Create",
                onConfirm = { onAction(PlaylistsVM.CreatePlaylistAction()) },
                onDismiss = { onAction(PlaylistsVM.ToggleCreateDialogAction(false)) },
            )
        }
        if (SHOW_RENAME_DIALOG() && selectedPlaylist != null) {
            var newName by remember(selectedPlaylist.id) { mutableStateOf(selectedPlaylist.name) }
            NameDialog(
                title = "Rename playlist",
                name = newName,
                onNameChange = { newName = it },
                confirmText = "Rename",
                onConfirm = { onAction(PlaylistsVM.RenamePlaylistAction(newName)) },
                onDismiss = { onAction(PlaylistsVM.ShowRenameDialogAction(false)) },
            )
        }
        if (SHOW_DELETE_DIALOG() && selectedPlaylist != null) {
            AlertDialog(
                onDismissRequest = { onAction(PlaylistsVM.ShowDeleteDialogAction(false)) },
                title = { Text("Delete \"${selectedPlaylist.name}\"?") },
                text = { Text("The songs stay on your phone, only the playlist goes.") },
                confirmButton = {
                    Button(onClick = { onAction(PlaylistsVM.DeletePlaylistAction) }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(PlaylistsVM.ShowDeleteDialogAction(false)) }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistDomain>,
    onCreate: () -> Unit,
    onOpen: (PlaylistDomain) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("New playlist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(playlist, onClick = { onOpen(playlist) })
        }
        if (playlists.none { !it.isLikedSongs }) {
            item {
                Text(
                    "Make playlists for trips, workouts, late nights… Add songs from a song's ⋮ menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistDomain, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playlist.isLikedSongs) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            ArtworkImage(
                songId = playlist.songs.firstOrNull()?.id,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                songCount(playlist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun songCount(playlist: PlaylistDomain): String {
    val count = playlist.songs.size
    val minutes = playlist.songs.sumOf { it.duration } / 60_000
    return when {
        count == 0 -> "Empty"
        minutes >= 60 -> "$count songs · ${minutes / 60} h ${minutes % 60} min"
        else -> "$count song${if (count == 1) "" else "s"} · $minutes min"
    }
}

@Composable
private fun PlaylistDetail(
    playlist: PlaylistDomain,
    onAction: (Action) -> Unit,
) {
    fun play(startIndex: Int) = onAction(PlaylistsVM.PlayPlaylistAction(playlist.id, startIndex))

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = playlist.name,
            subtitle = songCount(playlist),
            onBack = { onAction(PlaylistsVM.SelectPlaylistAction(null)) },
            actions = {
                if (!playlist.isLikedSongs) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuOpen = false
                                    onAction(PlaylistsVM.ShowRenameDialogAction(true))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                onClick = {
                                    menuOpen = false
                                    onAction(PlaylistsVM.ShowDeleteDialogAction(true))
                                }
                            )
                        }
                    }
                }
            }
        )

        if (playlist.songs.isEmpty()) {
            EmptyState(
                icon = if (playlist.isLikedSongs) Icons.Filled.FavoriteBorder else Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Nothing here yet",
                message = if (playlist.isLikedSongs) "Tap the heart on a song and it shows up here"
                else "Add songs from a song's ⋮ menu, or the + in the player",
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { play(0) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Play", modifier = Modifier.padding(start = 6.dp))
            }
            FilledTonalButton(onClick = { play(playlist.songs.indices.random()) }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Any song", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(
            if (playlist.isLikedSongs) "Newest likes first"
            else "Hold and drag to reorder · swipe a song away to remove it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        val songs = remember(playlist.songs) { playlist.songs.map { it.toUi() }.toMutableStateList() }
        ReorderableList(
            modifier = Modifier.weight(1f),
            list = songs,
            key = { it.id },
            lazyListState = rememberLazyListState(),
            reorderEnabled = !playlist.isLikedSongs,
            onDragEnd = { reordered ->
                onAction(PlaylistsVM.ReorderPlaylistAction(playlist.id, reordered.map { it.id }))
            },
            onClick = { song -> play(songs.indexOf(song).coerceAtLeast(0)) }
        ) { song ->
            val dismiss = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.Settled) false
                    else {
                        onAction(PlaylistsVM.RemoveSongFromPlaylistAction(playlist.id, song.id))
                        true
                    }
                }
            )
            SwipeToDismissBox(
                state = dismiss,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                SongRow(
                    title = song.title.orEmpty(),
                    subtitle = song.artistName,
                    detail = song.duration.toClock(),
                    songId = song.id,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Road trip") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
