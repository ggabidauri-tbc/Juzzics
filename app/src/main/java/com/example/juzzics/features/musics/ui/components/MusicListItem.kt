package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juzzics.common.base.extensions.toMusicDuration
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.artistName

/** What a song's ⋮ menu can do. [onAddToPlaylist] null hides that item. */
class SongMenuActions(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleLike: () -> Unit,
    val onAddToPlaylist: (() -> Unit)? = null,
)

@Composable
fun MusicListItem(
    musicFile: MusicFileUi,
    isLiked: Boolean,
    menu: SongMenuActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (musicFile.isPlaying) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.background
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(songId = musicFile.id, modifier = Modifier.size(44.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = musicFile.title ?: "",
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLiked) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Liked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    Text(
                        text = musicFile.artistName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "  ·  " + musicFile.duration.toMusicDuration(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SongMenu(isLiked = isLiked, menu = menu)
        }
    }
}

@Composable
private fun SongMenu(isLiked: Boolean, menu: SongMenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Song options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { open = false; menu.onPlayNext() })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { open = false; menu.onAddToQueue() })
            DropdownMenuItem(
                text = { Text(if (isLiked) "Remove from liked" else "Like") },
                onClick = { open = false; menu.onToggleLike() }
            )
            menu.onAddToPlaylist?.let { addToPlaylist ->
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { open = false; addToPlaylist() })
            }
        }
    }
}
