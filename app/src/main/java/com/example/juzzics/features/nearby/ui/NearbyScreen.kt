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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.juzzics.features.nearby.data.ReceivedSong
import com.example.juzzics.features.nearby.domain.ConnectedFriend
import com.example.juzzics.features.nearby.domain.PartyRole
import com.example.juzzics.features.nearby.domain.PlayTarget
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.PendingConnection
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.domain.RemoteSong
import com.example.juzzics.features.nearby.domain.SongTransfer
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

        // saving a song a friend sent: Android 9 and older ask for storage access first
        var pendingSave by remember { mutableStateOf<Long?>(null) }
        val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            pendingSave?.let { id -> if (ok) onAction(NearbyVM.SaveReceivedAction(id)) }
            pendingSave = null
        }
        val saveReceived: (Long) -> Unit = { id ->
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingSave = id
                storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else onAction(NearbyVM.SaveReceivedAction(id))
        }

        // shout-outs need the microphone
        fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val shoutOutPermission = ::micGranted to { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

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

                SHOW_BLEND() -> BlendPage(
                    blend = BLEND(),
                    onBack = { onAction(NearbyVM.OpenBlendAction(false)) },
                    onPlayMix = { onAction(NearbyVM.PlayBlendAction(it)) },
                    onReshuffle = { onAction(NearbyVM.ReshuffleBlendAction) },
                    onPlayShared = { onAction(NearbyVM.PlaySharedAction(it)) },
                )

                openFriend != null && SEND_MODE() -> SendSongsPage(
                    friend = openFriend,
                    mySongs = MY_SONGS(),
                    toQueue = SEND_TO_QUEUE(),
                    query = !FRIEND_QUERY,
                    transfers = nearby.transfers,
                    onAction = onAction,
                )

                openFriend != null -> FriendLibrary(
                    friend = openFriend,
                    query = !FRIEND_QUERY,
                    target = PLAY_TARGET(),
                    transfers = nearby.transfers,
                    onAction = onAction,
                )

                else -> NearbyHome(
                    nearby = nearby,
                    received = RECEIVED(),
                    onSave = saveReceived,
                    micPermission = shoutOutPermission,
                    onAction = onAction,
                )
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
        MESSAGE()?.let { message ->
            AlertDialog(
                onDismissRequest = { onAction(NearbyVM.DismissMessageAction) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { onAction(NearbyVM.DismissMessageAction) }) { Text("OK") } }
            )
        }
    }
}

@Composable
private fun NearbyHome(
    nearby: NearbyState,
    received: List<ReceivedSong>,
    onSave: (Long) -> Unit,
    /** has it / ask for it */
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onAction: (Action) -> Unit,
) {
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
        item { ShareAppCard(onMessage = { onAction(NearbyVM.ShowMessageAction(it)) }) }

        if (nearby.transfers.isNotEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 8.dp)) { Transfers(nearby.transfers) } } }
        }

        if (nearby.friends.isNotEmpty()) {
            item {
                ShoutOutCard(
                    recording = nearby.recordingShoutOut,
                    playingFrom = nearby.shoutOutFrom,
                    hasPermission = micPermission.first,
                    askPermission = micPermission.second,
                    onStart = { onAction(NearbyVM.StartShoutOutAction) },
                    onStop = { send -> onAction(NearbyVM.StopShoutOutAction(send)) },
                )
            }
        }

        if (nearby.friends.isNotEmpty() || nearby.singer != null) {
            item {
                SingCard(
                    friends = nearby.friends.map { it.endpointId to it.name },
                    singingTo = nearby.singingTo,
                    singer = nearby.singer,
                    micGain = nearby.micGain,
                    hasPermission = micPermission.first,
                    askPermission = micPermission.second,
                    onStart = { onAction(NearbyVM.StartSingingAction(it)) },
                    onStop = { onAction(NearbyVM.StopSingingAction) },
                    onStopSinger = { onAction(NearbyVM.StopSingerAction) },
                    onGain = { onAction(NearbyVM.MicGainAction(it)) },
                )
            }
        }

        if (nearby.friends.isNotEmpty() || nearby.carDj) {
            item { CarDjCard(on = nearby.carDj, queue = nearby.djQueue, onToggle = { onAction(NearbyVM.SetCarDjAction(it)) }) }
        }

        if (nearby.friends.any { it.library.isNotEmpty() }) {
            item {
                BlendCard(
                    friendNames = nearby.friends.map { it.name },
                    onOpen = { onAction(NearbyVM.OpenBlendAction(true)) },
                )
            }
        }

        if (nearby.friends.isNotEmpty() || nearby.party.role != PartyRole.NONE) {
            item {
                PartyCard(
                    party = nearby.party,
                    hasFriends = nearby.friends.isNotEmpty(),
                    onStart = { onAction(NearbyVM.StartPartyAction) },
                    onEnd = { onAction(NearbyVM.EndPartyAction) },
                )
            }
        }

        if (nearby.friends.isNotEmpty()) {
            item { Text("Connected", style = MaterialTheme.typography.titleLarge) }
            items(nearby.friends, key = { it.endpointId }) { friend -> FriendCard(friend, onAction) }
        }

        item { FindFriendsCard(nearby, onAction) }

        if (received.isNotEmpty()) {
            item {
                ReceivedSongsCard(
                    songs = received,
                    onPlay = { onAction(NearbyVM.PlayReceivedAction(it)) },
                    onSave = onSave,
                )
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Friends can save my songs", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (nearby.letFriendsSave) "Songs you send can be kept on their phone"
                        else "Songs you send can only be listened to",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = nearby.letFriendsSave, onCheckedChange = { onAction(NearbyVM.LetFriendsSaveAction(it)) })
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
            if (friend.djOpen) {
                Text(
                    "Car DJ: add songs to their queue",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                UpNext(friend.upNext, emptyText = "Their queue is empty", max = 4)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAction(NearbyVM.OpenFriendAction(friend.endpointId)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (friend.library.isEmpty() && !friend.libraryComplete) "Loading songs…"
                        else "Their songs (${friend.library.size})",
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = { onAction(NearbyVM.OpenSendAction(friend.endpointId, toQueue = friend.djOpen)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        if (friend.djOpen) "Queue my songs" else "Send my song",
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
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

/** a friend's songs: tap one to play it on their phone, or to hear it on this one */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendLibrary(
    friend: ConnectedFriend,
    query: String,
    target: PlayTarget,
    transfers: List<SongTransfer>,
    onAction: (Action) -> Unit,
) {
    BackHandler { onAction(NearbyVM.OpenFriendAction(null)) }
    // "their queue" only while their phone is the Car DJ
    val playOn = if (target == PlayTarget.THEIR_QUEUE && !friend.djOpen) PlayTarget.THEIR_PHONE else target
    val songs = remember(friend.library, query) { friend.library.matching(query) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "${friend.name}'s songs",
            subtitle = when (playOn) {
                PlayTarget.THEIR_PHONE -> "Tap a song to play it on their phone now"
                PlayTarget.THEIR_QUEUE -> "Tap a song to add it to their queue"
                PlayTarget.MY_PHONE -> "Tap a song to hear it on your phone (it streams over)"
            },
            onBack = { onAction(NearbyVM.OpenFriendAction(null)) }
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Play on", style = MaterialTheme.typography.labelLarge)
            FilterChip(
                selected = playOn == PlayTarget.THEIR_PHONE,
                onClick = { onAction(NearbyVM.PlayTargetAction(PlayTarget.THEIR_PHONE)) },
                label = { Text("Their phone") },
                leadingIcon = { Icon(Icons.Filled.Speaker, contentDescription = null, Modifier.size(18.dp)) }
            )
            if (friend.djOpen) {
                FilterChip(
                    selected = playOn == PlayTarget.THEIR_QUEUE,
                    onClick = { onAction(NearbyVM.PlayTargetAction(PlayTarget.THEIR_QUEUE)) },
                    label = { Text("Their queue") },
                )
            }
            FilterChip(
                selected = playOn == PlayTarget.MY_PHONE,
                onClick = { onAction(NearbyVM.PlayTargetAction(PlayTarget.MY_PHONE)) },
                label = { Text("My phone") },
                leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null, Modifier.size(18.dp)) }
            )
        }
        SearchField(query, "Search their songs", onAction)
        if (playOn != PlayTarget.MY_PHONE) RemoteControls(friend, onAction)
        Transfers(transfers)
        if (songs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PhoneAndroid,
                title = if (!friend.libraryComplete) "Loading their songs…" else "No songs found",
            )
        } else {
            SongList(
                songs = songs,
                isHighlighted = { playOn != PlayTarget.MY_PHONE && friend.nowPlaying?.title == it.title },
                modifier = Modifier.weight(1f),
                onClick = { song ->
                    onAction(
                        when (playOn) {
                            PlayTarget.THEIR_PHONE -> NearbyVM.PlayOnFriendAction(friend.endpointId, song.id)
                            PlayTarget.THEIR_QUEUE -> NearbyVM.QueueOnFriendAction(friend.endpointId, song.id)
                            PlayTarget.MY_PHONE -> NearbyVM.ListenHereAction(friend.endpointId, song)
                        }
                    )
                }
            )
        }
    }
}

/** this phone's songs: tap one to send it to the friend and play it there */
@Composable
private fun SendSongsPage(
    friend: ConnectedFriend,
    mySongs: List<RemoteSong>?,
    toQueue: Boolean,
    query: String,
    transfers: List<SongTransfer>,
    onAction: (Action) -> Unit,
) {
    BackHandler { onAction(NearbyVM.OpenFriendAction(null)) }
    val songs = remember(mySongs, query) { mySongs.orEmpty().matching(query) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = if (toQueue) "Add to ${friend.name}'s queue" else "Send to ${friend.name}",
            subtitle = if (toQueue) "Tap your songs to queue them on their phone (they take turns with others')"
            else "Tap one of your songs to play it on their phone",
            onBack = { onAction(NearbyVM.OpenFriendAction(null)) }
        )
        SearchField(query, "Search your songs", onAction)
        Transfers(transfers)
        if (songs.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Send,
                title = if (mySongs == null) "Loading your songs…" else "No songs found",
            )
        } else {
            SongList(
                songs = songs,
                isHighlighted = { false },
                modifier = Modifier.weight(1f),
                onClick = { song -> onAction(NearbyVM.SendToFriendAction(friend.endpointId, song.id)) }
            )
        }
    }
}

private fun List<RemoteSong>.matching(query: String): List<RemoteSong> {
    val q = query.trim()
    return if (q.isEmpty()) this
    else filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onAction: (Action) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onAction(NearbyVM.FriendQueryAction(it)) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SongList(
    songs: List<RemoteSong>,
    isHighlighted: (RemoteSong) -> Boolean,
    onClick: (RemoteSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(songs, key = { it.id }) { song ->
            val highlighted = isHighlighted(song)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick(song) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                        color = if (highlighted) MaterialTheme.colorScheme.primary
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

/** songs on their way between the phones, with progress */
@Composable
private fun Transfers(transfers: List<SongTransfer>) {
    transfers.forEach { transfer ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                if (transfer.incoming) "Getting \"${transfer.title}\" from ${transfer.friendName}…"
                else "Sending \"${transfer.title}\" to ${transfer.friendName}…",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val progress = transfer.progress
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
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
