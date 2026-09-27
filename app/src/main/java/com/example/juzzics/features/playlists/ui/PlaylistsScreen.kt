package com.example.juzzics.features.playlists.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.LOADING
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.model.knownArtist
import com.example.juzzics.features.musics.ui.model.toUi
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistsScreen(
    states: BaseState,
    uiEvent: Flow<UiEvent>,
    onAction: (Action) -> Unit,
    /** called after starting playback, to show the player */
    onOpenPlayer: () -> Unit,
) {
    with2(first = states, second = PlaylistsVM) {
        val playlists = PLAYLIST_LIST()
        // look the selected playlist up in the (live) list, so edits show right away
        val selectedPlaylist = SELECTED_PLAYLIST_ID()?.let { id -> playlists.find { it.id == id } }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            uiEvent.BaseHandler(
                loading = LOADING(),
                content = {
                    Scaffold(
                        floatingActionButton = {
                            if (selectedPlaylist == null) {
                                FloatingActionButton(
                                    onClick = { onAction(PlaylistsVM.ToggleCreateDialogAction(true)) }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                                }
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            if (selectedPlaylist != null) {
                                BackHandler { onAction(PlaylistsVM.SelectPlaylistAction(null)) }
                                PlaylistDetailView(
                                    playlist = selectedPlaylist,
                                    onAction = onAction,
                                    onOpenPlayer = onOpenPlayer,
                                )
                            } else {
                                PlaylistListView(
                                    playlists = playlists,
                                    onSelectPlaylist = { playlist ->
                                        onAction(PlaylistsVM.SelectPlaylistAction(playlist.id))
                                    }
                                )
                            }
                        }
                    }

                    if (SHOW_CREATE_DIALOG()) {
                        NameDialog(
                            title = "Create Playlist",
                            name = !NEW_PLAYLIST_NAME,
                            onNameChange = { onAction(PlaylistsVM.UpdateNewPlaylistNameAction(it)) },
                            confirmText = "Create",
                            placeholder = "Leave empty for default name",
                            onConfirm = { onAction(PlaylistsVM.CreatePlaylistAction()) },
                            onDismiss = { onAction(PlaylistsVM.ToggleCreateDialogAction(false)) },
                        )
                    }

                    if (SHOW_RENAME_DIALOG() && selectedPlaylist != null) {
                        var newName by remember(selectedPlaylist.id) { mutableStateOf(selectedPlaylist.name) }
                        NameDialog(
                            title = "Rename Playlist",
                            name = newName,
                            onNameChange = { newName = it },
                            confirmText = "Rename",
                            placeholder = "Playlist name",
                            onConfirm = { onAction(PlaylistsVM.RenamePlaylistAction(newName)) },
                            onDismiss = { onAction(PlaylistsVM.ShowRenameDialogAction(false)) },
                        )
                    }

                    if (SHOW_DELETE_DIALOG() && selectedPlaylist != null) {
                        AlertDialog(
                            onDismissRequest = { onAction(PlaylistsVM.ShowDeleteDialogAction(false)) },
                            title = { Text("Delete \"${selectedPlaylist.name}\"?") },
                            text = { Text("The songs stay on your device, only the playlist is deleted.") },
                            confirmButton = {
                                Button(onClick = { onAction(PlaylistsVM.DeletePlaylistAction) }) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { onAction(PlaylistsVM.ShowDeleteDialogAction(false)) }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirmText: String,
    placeholder: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Playlist Name") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistListView(
    playlists: List<PlaylistDomain>,
    onSelectPlaylist: (PlaylistDomain) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "No playlists yet",
                message = "Tap + to create one"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (playlist.isLikedSongs) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Text(text = playlist.name, style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "${playlist.songs.size} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistDomain,
    onAction: (Action) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    fun play(startIndex: Int) {
        onAction(PlaylistsVM.PlayPlaylistAction(playlist.id, startIndex))
        onOpenPlayer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            IconButton(onClick = { onAction(PlaylistsVM.SelectPlaylistAction(null)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            var menuOpen by remember { mutableStateOf(false) }
            if (!playlist.isLikedSongs) Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
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

        if (playlist.songs.isEmpty()) {
            EmptyState(
                icon = if (playlist.isLikedSongs) Icons.Default.FavoriteBorder else Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "This playlist is empty",
                message = if (playlist.isLikedSongs) "Tap the heart on a song to add it here"
                else "Add songs from a song's menu in the Musics tab"
            )
            return@Column
        }

        Button(
            onClick = { play(0) },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Play", modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            if (playlist.isLikedSongs) "Tap a song to play from it · newest likes first"
            else "Tap a song to play from it, long-press and drag to reorder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.knownArtist.ifEmpty { "Unknown Artist" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = {
                    onAction(PlaylistsVM.RemoveSongFromPlaylistAction(playlist.id, song.id))
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove from playlist",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
