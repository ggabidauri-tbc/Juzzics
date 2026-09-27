package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juzzics.common.base.extensions.toMusicDuration
import com.example.juzzics.features.musics.ui.model.MusicFileUi

@Composable
fun MusicListItem(
    musicFile: MusicFileUi,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (musicFile.isPlaying) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.background
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = musicFile.title ?: "",
                fontSize = 16.sp
            )
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                modifier = Modifier.weight(0.8f),
                text = "artist: ${musicFile.artist}",
                fontSize = 14.sp
            )
            Text(text = musicFile.duration.toMusicDuration(), fontSize = 12.sp)
        }
    }
}
