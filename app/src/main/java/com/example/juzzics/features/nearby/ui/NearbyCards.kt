package com.example.juzzics.features.nearby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.nearby.data.ReceivedSong
import com.example.juzzics.features.nearby.domain.PartyRole
import com.example.juzzics.features.nearby.domain.PartyState

/** party mode: start one, see who's in, end / leave it */
@Composable
fun PartyCard(
    party: PartyState,
    hasFriends: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    val on = party.role != PartyRole.NONE
    Card(
        Modifier.fillMaxWidth(),
        colors = if (on) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Celebration, contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        when (party.role) {
                            PartyRole.NONE -> "Party mode"
                            PartyRole.HOST -> "Your party is on"
                            PartyRole.GUEST -> "In ${party.hostName}'s party"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when (party.role) {
                            PartyRole.NONE -> "All connected phones play the same song at the same moment, like one big speaker"
                            PartyRole.HOST ->
                                if (party.guestNames.isEmpty()) "Waiting for friends to connect"
                                else "Playing along: ${party.guestNames.joinToString()}. Play music as usual, " +
                                        "their phones follow (a new song starts there once it arrives)."
                            PartyRole.GUEST ->
                                if (party.waitingForSong) "Getting the song from ${party.hostName}…"
                                else "This phone follows ${party.hostName}'s music"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (party.role == PartyRole.GUEST && party.waitingForSong) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            when (party.role) {
                PartyRole.NONE -> Button(
                    onClick = onStart,
                    enabled = hasFriends,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (hasFriends) "Start a party" else "Connect to a friend first") }

                PartyRole.HOST -> OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                    Text("End the party")
                }

                PartyRole.GUEST -> OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave")
                }
            }
        }
    }
}

/** songs friends sent: play again, or keep them (if the sender allows it) */
@Composable
fun ReceivedSongsCard(
    songs: List<ReceivedSong>,
    onPlay: (Long) -> Unit,
    onSave: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "Songs friends sent",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                "The last ${songs.size} are kept for a while",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            songs.forEach { song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onPlay(song.id) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(song.title.ifBlank { "Unknown song" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(song.artist, "from ${song.from}").filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    when {
                        song.saved -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        song.canSave -> TextButton(onClick = { onSave(song.id) }) { Text("Save") }
                    }
                }
            }
        }
    }
}
