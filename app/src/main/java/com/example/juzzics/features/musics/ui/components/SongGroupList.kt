package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.uiComponents.ArtworkImage
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.SongGroup

/** Albums or artists; tap one to see its songs. */
@Composable
fun SongGroupList(
    groups: List<SongGroup>,
    tab: BrowseTab,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        EmptyState(
            icon = if (tab == BrowseTab.ALBUMS) Icons.Filled.Album else Icons.Filled.Person,
            title = if (tab == BrowseTab.ALBUMS) "No albums found" else "No artists found",
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier) {
        items(groups, key = { it.name }) { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(group.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    songId = group.songs.firstOrNull()?.id,
                    shape = RoundedCornerShape(if (tab == BrowseTab.ARTISTS) 24.dp else 8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${group.songs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Title row of an opened album/artist: back, name, play all. */
@Composable
fun SongGroupHeader(name: String, onBack: () -> Unit, onPlayAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPlayAll) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
        }
    }
}
