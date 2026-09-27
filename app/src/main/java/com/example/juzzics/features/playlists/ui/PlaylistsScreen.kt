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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.stateOrFalse
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun PlaylistsScreen(
    states: BaseState,
    uiEvent: SharedFlow<UiEvent>,
    onAction: (Action) -> Unit
) {
    with2(first = states, second = PlaylistsVM) {
        val playlists: List<PlaylistDomain> = PLAYLIST_LIST<List<PlaylistDomain>>() ?: emptyList()
        val selectedPlaylist: PlaylistDomain? = SELECTED_PLAYLIST<PlaylistDomain>()
        val showCreateDialog: Boolean = SHOW_CREATE_DIALOG.stateOrFalse()
        val newPlaylistName: String = !NEW_PLAYLIST_NAME

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            uiEvent.BaseHandler(
                content = {
                    Scaffold(
                        floatingActionButton = {
                            if (selectedPlaylist == null) {
                                FloatingActionButton(
                                    onClick = { onAction(PlaylistsVM.ToggleCreateDialogAction(true)) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Playlist"
                                    )
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
                                BackHandler {
                                    onAction(PlaylistsVM.SelectPlaylistAction(null))
                                }
                                PlaylistDetailView(
                                    playlist = selectedPlaylist,
                                    onBack = { onAction(PlaylistsVM.SelectPlaylistAction(null)) },
                                    onRemoveSong = { songId ->
                                        onAction(
                                            PlaylistsVM.RemoveSongFromPlaylistAction(
                                                playlistId = selectedPlaylist.id,
                                                songId = songId
                                            )
                                        )
                                    }
                                )
                            } else {
                                PlaylistListView(
                                    playlists = playlists,
                                    onSelectPlaylist = { playlist ->
                                        onAction(PlaylistsVM.SelectPlaylistAction(playlist))
                                    }
                                )
                            }
                        }
                    }

                    if (showCreateDialog) {
                        AlertDialog(
                            onDismissRequest = { onAction(PlaylistsVM.ToggleCreateDialogAction(false)) },
                            title = { Text("Create Playlist") },
                            text = {
                                Column {
                                    Text("Enter an optional name for your playlist:")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = {
                                            onAction(PlaylistsVM.UpdateNewPlaylistNameAction(it))
                                        },
                                        label = { Text("Playlist Name (Optional)") },
                                        placeholder = { Text("Leave empty for default name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { onAction(PlaylistsVM.CreatePlaylistAction()) }
                                ) {
                                    Text("Create")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { onAction(PlaylistsVM.ToggleCreateDialogAction(false)) }
                                ) {
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No playlists found.\nTap + to create a new playlist!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleLarge
                                )
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
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistDomain,
    onBack: () -> Unit,
    onRemoveSong: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (playlist.songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This playlist is empty.\nAdd songs from the Musics tab!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlist.songs) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title ?: "Unknown Title",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = song.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onRemoveSong(song.id) }) {
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
        }
    }
}
