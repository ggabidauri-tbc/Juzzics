package com.example.juzzics.features.nearby.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.features.musics.ui.components.toClock
import com.example.juzzics.features.nearby.domain.ConnectedFriend
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.PendingConnection
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.ui.vm.NearbyVM

/** permissions Nearby needs on this Android version */
/** Location is asked everywhere: Google Play services' Nearby checks it even on new Android versions. */
private fun nearbyPermissions(): Array<String> {
    val location = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> location + arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> location + arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
        else -> location
    }
}

/**
 * Music with friends nearby, no internet needed: share your music, find friends' phones,
 * browse their songs and play / control them on their phone.
 */
@Composable
fun NearbyScreen(
    states: BaseState,
    onAction: (Action) -> Unit,
) {
    with2(states, NearbyVM) {
        val context = LocalContext.current
        // "approximate location" is enough, so precise (FINE) location may be refused
        fun allGranted() = nearbyPermissions()
            .filter { it != Manifest.permission.ACCESS_FINE_LOCATION }
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        var granted by remember { mutableStateOf(allGranted()) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted = allGranted() }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = allGranted() }

        val nearby = NEARBY()
        val openFriend = OPEN_FRIEND()?.let { id -> nearby.friends.find { it.endpointId == id } }

        Surface(Modifier.fillMaxSize()) {
            when {
                !granted -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        icon = Icons.Filled.WifiTethering,
                        title = "Music with friends nearby",
                        message = "Juzzics finds friends' phones with Bluetooth and Wi-Fi, no internet needed. " +
                                "Android asks for \"Nearby devices\" access for that.",
                    )
                    Button(onClick = { permissionLauncher.launch(nearbyPermissions()) }) {
                        Text("Allow nearby devices")
                    }
                }

                openFriend != null -> FriendLibrary(
                    friend = openFriend,
                    query = !FRIEND_QUERY,
                    onAction = onAction,
                )

                else -> NearbyHome(nearby = nearby, onAction = onAction)
            }
        }

        nearby.pending?.let { PairingDialog(it, onAction) }
        nearby.error?.let { error ->
            AlertDialog(
                onDismissRequest = { onAction(NearbyVM.DismissErrorAction) },
                title = { Text("Nearby") },
                text = { Text(error) },
                confirmButton = { TextButton(onClick = { onAction(NearbyVM.DismissErrorAction) }) { Text("OK") } }
            )
        }
    }
}

@Composable
private fun NearbyHome(nearby: NearbyState, onAction: (Action) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Nearby", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Play music with friends around you, no internet needed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { ShareCard(nearby, onAction) }

        if (nearby.friends.isNotEmpty()) {
            item { Text("Connected", style = MaterialTheme.typography.titleLarge) }
            items(nearby.friends, key = { it.endpointId }) { friend -> FriendCard(friend, onAction) }
        }

        item { FindFriendsCard(nearby, onAction) }
    }
}

@Composable
private fun ShareCard(nearby: NearbyState, onAction: (Action) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Share my music", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (nearby.sharing) "Friends nearby can find this phone and play its songs"
                        else "Turn on so friends can find you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = nearby.sharing, onCheckedChange = { onAction(NearbyVM.SetSharingAction(it)) })
            }
            var name by remember(nearby.deviceName) { mutableStateOf(nearby.deviceName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name friends see") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(NearbyVM.RenameDeviceAction(name)) }),
                trailingIcon = {
                    if (name != nearby.deviceName) {
                        TextButton(onClick = { onAction(NearbyVM.RenameDeviceAction(name)) }) { Text("Save") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FindFriendsCard(nearby: NearbyState, onAction: (Action) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Friends nearby", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "They need Juzzics open with \"Share my music\" on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (nearby.searching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onAction(NearbyVM.SearchAction(false)) }) { Text("Stop") }
                } else {
                    FilledTonalButton(onClick = { onAction(NearbyVM.SearchAction(true)) }) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Text("Look", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (nearby.found.isEmpty()) {
                Text(
                    if (nearby.searching) "Looking for phones…" else "Tap Look to find friends' phones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            nearby.found.forEach { device -> FoundDeviceRow(device, onAction) }
        }
    }
}

@Composable
private fun FoundDeviceRow(device: NearbyDevice, onAction: (Action) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
        Text(
            device.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Button(onClick = { onAction(NearbyVM.ConnectAction(device)) }) { Text("Connect") }
    }
}

@Composable
private fun FriendCard(friend: ConnectedFriend, onAction: (Action) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                Text(
                    friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                IconButton(onClick = { onAction(NearbyVM.DisconnectAction(friend.endpointId)) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Disconnect")
                }
            }
            val playing = friend.nowPlaying
            Text(
                if (playing == null) "Nothing playing on their phone"
                else "${if (playing.isPlaying) "Playing" else "Paused"}: ${playing.title}" +
                        playing.artist.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            RemoteControls(friend, onAction)
            OutlinedButton(
                onClick = { onAction(NearbyVM.OpenFriendAction(friend.endpointId)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (friend.library.isEmpty() && !friend.libraryComplete) "Loading their songs…"
                    else "Their songs (${friend.library.size})"
                )
            }
        }
    }
}

/** controls their phone's player */
@Composable
private fun RemoteControls(friend: ConnectedFriend, onAction: (Action) -> Unit) {
    fun send(command: RemoteCommand) = onAction(NearbyVM.CommandAction(friend.endpointId, command))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { send(RemoteCommand.VOLUME_DOWN) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Their volume down")
        }
        IconButton(onClick = { send(RemoteCommand.PREVIOUS) }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous on their phone")
        }
        FilledTonalButton(onClick = { send(RemoteCommand.TOGGLE) }) {
            Icon(
                if (friend.nowPlaying?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play or pause on their phone"
            )
        }
        IconButton(onClick = { send(RemoteCommand.NEXT) }) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next on their phone")
        }
        IconButton(onClick = { send(RemoteCommand.VOLUME_UP) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Their volume up")
        }
    }
}

/** a friend's songs; tap one to play it on their phone */
@Composable
private fun FriendLibrary(friend: ConnectedFriend, query: String, onAction: (Action) -> Unit) {
    BackHandler { onAction(NearbyVM.OpenFriendAction(null)) }
    val songs = remember(friend.library, query) {
        val q = query.trim()
        if (q.isEmpty()) friend.library
        else friend.library.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(NearbyVM.OpenFriendAction(null)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${friend.name}'s songs",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Tap a song to play it on their phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { onAction(NearbyVM.FriendQueryAction(it)) },
            placeholder = { Text("Search their songs") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        RemoteControls(friend, onAction)
        if (songs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PhoneAndroid,
                title = if (!friend.libraryComplete) "Loading their songs…" else "No songs found",
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(songs, key = { it.id }) { song ->
                    val isPlaying = friend.nowPlaying?.title == song.title
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAction(NearbyVM.PlayOnFriendAction(friend.endpointId, song.id)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                song.artist.ifBlank { "Unknown artist" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Text(
                            song.durationMs.toClock(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** both phones show the same code: that's how you know you're connecting to the right person */
@Composable
private fun PairingDialog(pending: PendingConnection, onAction: (Action) -> Unit) {
    AlertDialog(
        // only the buttons close it, so a stray tap outside doesn't cancel the pairing
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.WifiTethering, contentDescription = null) },
        title = {
            Text(if (pending.incoming) "${pending.name} wants to connect" else "Connect to ${pending.name}?")
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Check that their phone shows the same code:",
                    textAlign = TextAlign.Center
                )
                Text(
                    pending.code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                if (pending.accepted) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Waiting for ${pending.name} to confirm…", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (pending.incoming) "They'll be able to see your songs and play music on this phone."
                    else "You'll see each other's songs and can play music on each other's phones.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAction(NearbyVM.AcceptAction) }, enabled = !pending.accepted) { Text("Codes match") }
        },
        dismissButton = { TextButton(onClick = { onAction(NearbyVM.RejectAction) }) { Text("Cancel") } }
    )
}
