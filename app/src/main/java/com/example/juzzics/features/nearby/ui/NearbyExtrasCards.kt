package com.example.juzzics.features.nearby.ui

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Hold the big button (low on the screen, under your thumb) and talk; letting go sends it to
 * everyone connected, wherever the finger is. Slide up to cancel.
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
    hasFriends: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val permitted by rememberUpdatedState(hasPermission)
    val ask by rememberUpdatedState(askPermission)
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    val canTalk by rememberUpdatedState(hasFriends)
    /** the finger slid up far enough: letting go now cancels */
    var cancelling by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val cancelDistance = with(density) { 96.dp.toPx() }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Say something to everyone: it plays on their phones over the music.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!hasFriends) {
            Text(
                "Connect to a friend first (Nearby > Find friends).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            when {
                recording && cancelling -> "Let go to cancel"
                recording -> "Recording… let go to send · slide up to cancel"
                playingFrom != null -> "$playingFrom is talking…"
                else -> "Hold to talk"
            },
            style = MaterialTheme.typography.titleMedium,
            color = when {
                recording && cancelling -> MaterialTheme.colorScheme.error
                recording || playingFrom != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    when {
                        recording && cancelling -> MaterialTheme.colorScheme.surfaceContainerHighest
                        recording -> MaterialTheme.colorScheme.error
                        hasFriends -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!canTalk) return@awaitEachGesture
                        if (!permitted()) {
                            ask()
                            return@awaitEachGesture
                        }
                        start()
                        var cancel = false
                        try {
                            // follow the finger anywhere on screen until it lifts
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                cancel = down.position.y - change.position.y > cancelDistance
                                cancelling = cancel
                                change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            cancelling = false
                            stop(!cancel)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (recording && cancelling) Icons.Filled.Close else Icons.Filled.Mic,
                contentDescription = "Hold to talk",
                tint = when {
                    recording && cancelling -> MaterialTheme.colorScheme.error
                    recording -> MaterialTheme.colorScheme.onError
                    hasFriends -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(48.dp))
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
                itemsIndexed(blend.matches, key = { index, match -> "match_${index}_${match.friendName}" }) { _, match -> TasteMatchCard(match) }
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
 * live on it over the music. A big button low on the screen: tap to go live, tap to stop.
 * On the phone playing it: who's singing, how loud, and a way to stop it.
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
    modifier: Modifier = Modifier,
) {
    // whose phone to sing on: the only friend, or the one picked
    var picked by remember { mutableStateOf<String?>(null) }
    val target = friends.find { it.first == picked } ?: friends.firstOrNull()
    val live = singingTo != null
    val liveName = friends.find { it.first == singingTo }?.second

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Your phone becomes a mic: your voice plays live on the phone playing the music.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        // a friend sings through this phone
        if (singer != null) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
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

        if (friends.isEmpty()) {
            Text(
                "Connect to a friend first (Nearby > Find friends).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else if (!live && friends.size > 1) {
            Text(
                "Sing on",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                friends.forEach { (endpointId, name) ->
                    FilterChip(
                        selected = endpointId == target?.first,
                        onClick = { picked = endpointId },
                        label = { Text("$name's phone", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (live) {
            Text(
                "Hold the phone close like a mic, and stay a few steps from the speaker (too close, it squeals).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            when {
                live -> "You're live on ${liveName?.let { "$it's" } ?: "their"} phone · tap to stop"
                target != null -> "Tap to sing on ${target.second}'s phone"
                else -> "Sing along"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // a ring that breathes while you're live
        val pulse = rememberInfiniteTransition(label = "live")
        val ring by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ring"
        )
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            if (live) {
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = ring
                            scaleY = ring
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                )
            }
            Box(
                Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            live -> MaterialTheme.colorScheme.error
                            target != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable(enabled = live || target != null) {
                        when {
                            live -> onStop()
                            !hasPermission() -> askPermission()
                            target != null -> onStart(target.first)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (live) Icons.Filled.Stop else Icons.Filled.MicExternalOn,
                    contentDescription = if (live) "Stop singing" else "Start singing",
                    tint = when {
                        live -> MaterialTheme.colorScheme.onError
                        target != null -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(Modifier.height(40.dp))
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
