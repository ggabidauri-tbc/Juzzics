package com.example.juzzics.features.nearby.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.share.ShareApp
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.features.musics.ui.components.toClock
import com.example.juzzics.features.nearby.domain.Blend
import com.example.juzzics.features.nearby.domain.QueueEntry
import com.example.juzzics.features.nearby.domain.TasteMatch
import kotlinx.coroutines.launch

/** Car DJ on this phone: friends add songs to its queue (they take turns) */
@Composable
fun CarDjCard(on: Boolean, queue: List<QueueEntry>, onToggle: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (on) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text("Car DJ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (on) "Friends add songs to this phone's queue. Their songs take turns."
                        else "Is this phone playing in the car? Let everyone add songs to it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = on, onCheckedChange = onToggle)
            }
            if (on) UpNext(queue, emptyText = "Nothing queued yet")
        }
    }
}

/** a Car DJ's "Up next", with who added each song */
@Composable
fun UpNext(queue: List<QueueEntry>, emptyText: String, max: Int = 6) {
    if (queue.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("Up next", style = MaterialTheme.typography.labelLarge)
    queue.take(max).forEachIndexed { index, entry ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                entry.title + entry.artist.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.addedBy.isNotBlank()) {
                Text(
                    entry.addedBy,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
    if (queue.size > max) {
        Text(
            "and ${queue.size - max} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** opens the blend of everyone's music */
@Composable
fun BlendCard(friendNames: List<String>, onOpen: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text("Blend & taste match", style = MaterialTheme.typography.titleMedium)
                Text(
                    "How your taste matches ${friendNames.joinToString()}'s, the songs you share, and a mix of all of you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onOpen) { Text("Open") }
        }
    }
}

/**
 * hold the button and talk; letting go sends it to everyone connected (sliding off cancels).
 * [hasPermission] / [askPermission]: the microphone.
 */
@Composable
fun ShoutOutCard(
    recording: Boolean,
    playingFrom: String?,
    hasPermission: () -> Boolean,
    askPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: (send: Boolean) -> Unit,
) {
    val permitted by rememberUpdatedState(hasPermission)
    val ask by rememberUpdatedState(askPermission)
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shout-out", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        recording -> "Recording… let go to send"
                        playingFrom != null -> "$playingFrom is talking…"
                        else -> "Hold to talk: it plays on everyone's phone over the music"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recording || playingFrom != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (!permitted()) {
                                ask()
                                return@detectTapGestures
                            }
                            start()
                            // true: let go on the button (send); false: slid away (cancel)
                            stop(tryAwaitRelease())
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Hold to talk",
                    tint = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/** your music mixed with friends': play the mix, or the songs you have in common */
@Composable
fun BlendPage(
    blend: Blend?,
    onBack: () -> Unit,
    onPlayMix: (index: Int) -> Unit,
    onReshuffle: () -> Unit,
    onPlayShared: (index: Int) -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Blend", style = MaterialTheme.typography.titleLarge)
                Text(
                    blend?.people?.joinToString(" · ") ?: "Mixing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (blend == null || blend.mix.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Groups,
                title = if (blend == null) "Mixing your music…" else "No songs to mix yet",
                message = if (blend == null) null else "Wait until friends' song lists have arrived"
            )
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onPlayMix(0) }, modifier = Modifier.weight(1f)) { Text("Play the mix") }
                    OutlinedButton(onClick = onReshuffle) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, Modifier.size(18.dp))
                        Text("New mix", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    "Friends' songs are sent over one at a time while the mix plays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (blend.matches.isNotEmpty()) {
                item { SectionHeader("Taste match") }
                items(blend.matches, key = { "match_${it.friendName}" }) { match -> TasteMatchCard(match) }
            }
            if (blend.shared.isNotEmpty()) {
                item { SectionHeader("Songs you share (${blend.shared.size})") }
                itemsIndexed(blend.shared, key = { _, shared -> "shared_${shared.song.id}" }) { index, shared ->
                    BlendRow(
                        title = shared.song.title,
                        subtitle = shared.song.artist,
                        tag = "with ${shared.alsoWith.joinToString()}",
                        durationMs = shared.song.durationMs,
                        onClick = { onPlayShared(index) }
                    )
                }
            }
            item { SectionHeader("The mix (${blend.mix.size})") }
            itemsIndexed(blend.mix, key = { index, item -> "mix_${index}_${item.song.id}" }) { index, item ->
                BlendRow(
                    title = item.song.title,
                    subtitle = item.song.artist,
                    tag = item.ownerName,
                    durationMs = item.song.durationMs,
                    onClick = { onPlayMix(index) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun BlendRow(title: String, subtitle: String, tag: String, durationMs: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "Unknown song" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle.ifBlank { "Unknown artist" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                durationMs.toClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sing along: pick a friend's phone (the one playing the music) and sing, the voice plays
 * live on it over the music. On that phone: who's singing, how loud, and a way to stop it.
 */
@Composable
fun SingCard(
    friends: List<Pair<String, String>>,
    singingTo: String?,
    singer: String?,
    micGain: Float,
    hasPermission: () -> Boolean,
    askPermission: () -> Unit,
    onStart: (endpointId: String) -> Unit,
    onStop: () -> Unit,
    onStopSinger: () -> Unit,
    onGain: (Float) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (singingTo != null || singer != null) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MicExternalOn, contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text("Sing along", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            singingTo != null -> "You're live on ${friends.find { it.first == singingTo }?.second?.let { "$it's" } ?: "their"} phone. Sing!"
                            else -> "Your phone becomes a mic: your voice plays live on the phone playing the music"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (singingTo != null) {
                Text(
                    "Hold the phone close like a mic, and stay a few steps from the speaker (too close, it squeals)",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop singing") }
            } else {
                friends.forEach { (endpointId, name) ->
                    OutlinedButton(
                        onClick = { if (hasPermission()) onStart(endpointId) else askPermission() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sing on $name's phone", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            // a friend sings through this phone
            if (singer != null) {
                Text("$singer is singing on this phone", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Voice", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = micGain,
                        onValueChange = onGain,
                        valueRange = 0.5f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = onStopSinger) { Text("Mic off") }
                }
            }
        }
    }
}

/** "You and Anna: 67% · Great match", what you share, what each brings; shareable as text */
@Composable
fun TasteMatchCard(match: TasteMatch) {
    val context = LocalContext.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("You & ${match.friendName}", style = MaterialTheme.typography.titleMedium)
                    Text(match.label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${match.percent}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "${match.sharedSongs} song${if (match.sharedSongs == 1) "" else "s"} in common",
                style = MaterialTheme.typography.bodyMedium
            )
            if (match.sharedArtists.isNotEmpty()) {
                Text("You both love ${match.sharedArtists.joinToString()}", style = MaterialTheme.typography.bodyMedium)
            }
            val bring = listOfNotNull(
                match.youBring?.let { "You bring $it" },
                match.theyBring?.let { "${match.friendName} brings $it" },
            )
            if (bring.isNotEmpty()) {
                Text(
                    bring.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    val text = buildString {
                        append("Me & ${match.friendName}: ${match.percent}% music match (${match.label}) on Juzzics 🎧")
                        append("\n${match.sharedSongs} songs in common")
                        if (match.sharedArtists.isNotEmpty()) append(", we both love ${match.sharedArtists.joinToString()}")
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(Intent.createChooser(send, "Share your match")) }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                Text("Share", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * A friend doesn't have Juzzics: send them the app itself (share sheet: Quick Share,
 * Bluetooth...), no internet needed. [onMessage]: shows why it couldn't.
 */
@Composable
fun ShareAppCard(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text("Friend doesn't have Juzzics?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Send them the app from your phone, no internet needed. They'll need to allow installing it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (preparing) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = {
                    preparing = true
                    scope.launch {
                        when (val result = ShareApp.prepare(context)) {
                            is ShareApp.Result.Ready -> runCatching { context.startActivity(result.intent) }
                                .onFailure { onMessage("No app to share it with (turn on Quick Share or Bluetooth)") }
                            ShareApp.Result.InstalledInParts -> onMessage(
                                "This copy of Juzzics came from the Play Store in parts, so it can't be sent as one file. " +
                                        "Use the Play Store's \"Share apps\" (Manage apps & device) instead."
                            )
                            ShareApp.Result.TestBuild -> onMessage(
                                "This copy was installed by Android Studio as a test build, and other phones refuse to " +
                                        "install those from a file. Install a normal build first (Build > Build APK(s)), then send it."
                            )
                            is ShareApp.Result.Failed -> onMessage("Couldn't prepare the app: ${result.reason}")
                        }
                        preparing = false
                    }
                }) { Text("Send") }
            }
        }
    }
}
