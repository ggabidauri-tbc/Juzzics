package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.juzzics.common.uiComponents.SongRow
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName

/** What a song's ⋮ menu can do. [onAddToPlaylist] null hides that item. */
class SongMenuActions(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleLike: () -> Unit,
    val onAddToPlaylist: (() -> Unit)? = null,
)

/** A song in the library: the shared [SongRow] plus its ⋮ menu. */
@Composable
fun MusicListItem(
    musicFile: MusicFileUi,
    isLiked: Boolean,
    menu: SongMenuActions,
    modifier: Modifier = Modifier,
) {
    SongRow(
        title = musicFile.title.orEmpty(),
        subtitle = musicFile.artistName,
        detail = musicFile.duration.toClock(),
        songId = musicFile.id,
        isCurrent = musicFile.isPlaying,
        isLiked = isLiked,
        modifier = modifier,
        trailing = { SongMenu(isLiked = isLiked, menu = menu) }
    )
}

@Composable
fun SongMenu(isLiked: Boolean, menu: SongMenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Song options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                onClick = { open = false; menu.onPlayNext() }
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                onClick = { open = false; menu.onAddToQueue() }
            )
            DropdownMenuItem(
                text = { Text(if (isLiked) "Remove from Liked songs" else "Add to Liked songs") },
                leadingIcon = {
                    Icon(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null)
                },
                onClick = { open = false; menu.onToggleLike() }
            )
            menu.onAddToPlaylist?.let { addToPlaylist ->
                DropdownMenuItem(
                    text = { Text("Add to playlist…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = { open = false; addToPlaylist() }
                )
            }
        }
    }
}
