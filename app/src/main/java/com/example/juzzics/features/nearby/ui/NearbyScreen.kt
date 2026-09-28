package com.example.juzzics.features.nearby.ui

import com.example.juzzics.features.nearby.data.OpenRadarRequests
import androidx.compose.runtime.collectAsState

import com.example.juzzics.features.nearby.data.OfflineMapsState

import com.example.juzzics.common.base.viewModel.stateValue
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.LaunchedEffect
import com.example.juzzics.common.messages.AppMessages
import com.example.juzzics.common.messages.AppMessage
import com.example.juzzics.common.uiComponents.ScreenHeader
import com.example.juzzics.common.uiComponents.SectionHeader
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.Box
import com.example.juzzics.features.nearby.domain.NearbyPanel
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
 * Music with friends nearby, no internet needed. Alone: find friends. Connected: your friends
 * and things to do together (party, Car DJ, blend, sing, shout-out), each on its own page.
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

        // shout-outs and singing need the microphone
        fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val micPermission = ::micGranted to { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        // "open the friend radar" (a notification, a message's "Show")
        val radarRequests by OpenRadarRequests.count.collectAsState()
        LaunchedEffect(radarRequests) {
            if (radarRequests > OpenRadarRequests.handledByNearby) {
                OpenRadarRequests.handledByNearby = radarRequests
                onAction(NearbyVM.OpenFriendAction(null))
                onAction(NearbyVM.OpenFriendPageAction(null))
                onAction(NearbyVM.OpenBlendAction(false))
                onAction(NearbyVM.OpenPanelAction(if (OpenRadarRequests.chat) NearbyPanel.CHAT else NearbyPanel.RADAR))
            }
        }

        val nearby = NEARBY()
        val openFriend = OPEN_FRIEND()?.let { id -> nearby.friends.find { it.endpointId == id } }
        val friendPage = FRIEND_PAGE()?.let { id -> nearby.friends.find { it.endpointId == id } }
        val panel = PANEL()

        // problems show as a note at the bottom, not a dialog to tap away
        LaunchedEffect(nearby.error) {
            nearby.error?.let {
                AppMessages.show(AppMessage(it, long = true))
                onAction(NearbyVM.DismissErrorAction)
            }
        }

        when {
            !granted -> NearbyIntro(
                title = "Music with friends nearby",
                text = "Juzzics finds friends' phones over Bluetooth and Wi-Fi, no internet needed. " +
                        "Android calls that \"Nearby devices\".",
                button = "Allow nearby devices",
                onClick = { permissionLauncher.launch(nearbyPermissions()) }
            )

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

            friendPage != null -> FriendPage(friendPage, nearby.transfers, onAction)

            panel != null -> PanelPage(
                panel = panel,
                nearby = nearby,
                heading = { HEADING.stateValue() },
                radarAsMap = RADAR_AS_MAP(),
                offlineMaps = OFFLINE_MAPS(),
                received = RECEIVED(),
                onSave = saveReceived,
                micPermission = micPermission,
                onAction = onAction,
            )

            else -> NearbyHome(nearby = nearby, received = RECEIVED(), onAction = onAction)
        }

        nearby.pending?.let { PairingDialog(it, onAction) }
        if (SHOW_SETTINGS()) NearbySettingsSheet(nearby, onAction)
    }
}

/** a centered explanation with one button (no permission yet) */
@Composable
private fun NearbyIntro(title: String, text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun NearbyHome(
    nearby: NearbyState,
    received: List<ReceivedSong>,
    onAction: (Action) -> Unit,
) {
    val connected = nearby.friends.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader(
                title = "Nearby",
                subtitle = if (connected) "Connected to ${nearby.friends.joinToString { it.name }}"
                else "Music with friends, no internet needed",
                actions = {
                    IconButton(onClick = { onAction(NearbyVM.ShowSettingsAction(true)) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Nearby settings")
                    }
                }
            )
        }

        if (nearby.reconnecting.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Looking for ${nearby.reconnecting.joinToString()}. They reconnect by themselves when back in range.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        if (nearby.transfers.isNotEmpty()) {
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) { Column(Modifier.padding(vertical = 8.dp)) { Transfers(nearby.transfers) } }
            }
        }

        if (!connected) {
            item { FindFriendsHero(nearby, onAction) }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(nearby.friends, key = { it.endpointId }) { friend ->
                        FriendTile(friend, onClick = { onAction(NearbyVM.OpenFriendPageAction(friend.endpointId)) })
                    }
                    item { AddFriendTile(searching = nearby.searching, onClick = { onAction(NearbyVM.FindFriendsAction) }) }
                    item { BumpTile(onClick = { onAction(NearbyVM.OpenPanelAction(NearbyPanel.BUMP)) }) }
                }
            }
            if (nearby.searching || nearby.found.isNotEmpty()) {
                item { FoundDevices(nearby, onAction, Modifier.padding(horizontal = 16.dp)) }
            }
            item { SectionHeader("Together") }
            item { TogetherGrid(nearby, received, onAction) }
        }

        item {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
                ShareAppCard(onMessage = { AppMessages.show(AppMessage(it, long = true)) })
            }
        }
        if (!connected && received.isNotEmpty()) {
            item {
                TogetherRow(
                    icon = Icons.Filled.Inbox,
                    title = "Songs friends sent",
                    subtitle = "${received.size} kept on this phone",
                    onClick = { onAction(NearbyVM.OpenPanelAction(NearbyPanel.RECEIVED)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/** alone: one clear step, "Find friends" (visible + looking at once), and who's around */
@Composable
private fun FindFriendsHero(nearby: NearbyState, onAction: (Action) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            "Play music together",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Browse each other's songs, play on each other's phones, party, sing along. " +
                    "Your friend opens Nearby too and taps Find friends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        if (nearby.searching) {
            OutlinedButton(onClick = { onAction(NearbyVM.SearchAction(false)) }) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Looking… tap to stop", modifier = Modifier.padding(start = 10.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(NearbyVM.FindFriendsAction) }) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Find friends", modifier = Modifier.padding(start = 8.dp))
                }
                // standing next to each other: quicker, no codes
                OutlinedButton(onClick = { onAction(NearbyVM.OpenPanelAction(NearbyPanel.BUMP)) }) {
                    Icon(Icons.Filled.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Bump", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (nearby.found.isNotEmpty() || nearby.searching) {
            FoundDevices(nearby, onAction, Modifier.padding(top = 16.dp))
        }
    }
}

/** phones found nearby, each with Connect */
@Composable
private fun FoundDevices(nearby: NearbyState, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (nearby.found.isEmpty()) {
            Text(
                "Looking for phones with Juzzics open…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        nearby.found.forEach { device -> FoundDeviceRow(device, onAction) }
    }
}

@Composable
private fun FoundDeviceRow(device: NearbyDevice, onAction: (Action) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(device.name, Modifier.size(40.dp))
        Text(
            device.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Button(onClick = { onAction(NearbyVM.ConnectAction(device)) }) { Text("Connect") }
    }
}

/** a round badge with the first letter of a name */
@Composable
private fun Avatar(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/** a connected friend: name and what's playing on their phone; opens their page */
@Composable
private fun FriendTile(friend: ConnectedFriend, onClick: () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Avatar(friend.name, Modifier.size(44.dp))
        Text(
            friend.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        val playing = friend.nowPlaying
        Text(
            when {
                playing == null -> "Not playing"
                playing.isPlaying -> "▶ ${playing.title}"
                else -> "Paused"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (friend.djOpen) {
            Text("Car DJ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AddFriendTile(searching: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .clickable(enabled = !searching, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (searching) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.PersonAdd, contentDescription = null)
        }
        Text(
            if (searching) "Looking…" else "Add",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** "Bump": add a friend standing next to you by tapping phones together */
@Composable
private fun BumpTile(onClick: () -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Vibration, contentDescription = null)
        }
        Text("Bump", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
    }
}

/** the things to do together, as tiles; active ones say so */
@Composable
private fun TogetherGrid(nearby: NearbyState, received: List<ReceivedSong>, onAction: (Action) -> Unit) {
    val party = nearby.party
    val tiles = buildList {
        add(
            TogetherTile(
                Icons.Filled.Celebration, "Party",
                when (party.role) {
                    PartyRole.HOST -> "On · ${party.guestNames.size} following"
                    PartyRole.GUEST -> "In ${party.hostName}'s party"
                    PartyRole.NONE -> "All phones play as one"
                },
                active = party.role != PartyRole.NONE,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.PARTY)) }
        )
        add(
            TogetherTile(
                Icons.Filled.DirectionsCar, "Car DJ",
                if (nearby.carDj) "On · ${nearby.djQueue.size} up next" else "Everyone adds songs",
                active = nearby.carDj,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.CAR_DJ)) }
        )
        add(
            TogetherTile(Icons.Filled.Groups, "Blend & match", "Your mix, your taste match") {
                onAction(NearbyVM.OpenBlendAction(true))
            }
        )
        add(
            TogetherTile(
                Icons.Filled.MicExternalOn, "Sing along",
                when {
                    nearby.singingTo != null -> "You're live"
                    nearby.singer != null -> "${nearby.singer} is singing"
                    else -> "Your phone as a mic"
                },
                active = nearby.singingTo != null || nearby.singer != null,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.SING)) }
        )
        add(
            TogetherTile(
                Icons.Filled.Mic, "Shout-out",
                nearby.shoutOutFrom?.let { "$it is talking" } ?: "Hold to talk to everyone",
                active = nearby.shoutOutFrom != null,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.SHOUT_OUT)) }
        )
        add(
            TogetherTile(
                Icons.Filled.Explore, "Friend radar",
                when {
                    nearby.radar.people.isNotEmpty() -> "${nearby.radar.people.size} sharing where they are"
                    nearby.radar.sharing -> "You're sharing where you are"
                    else -> "Who's where, no internet"
                },
                active = nearby.radar.sharing,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.RADAR)) }
        )
        add(
            TogetherTile(
                Icons.Filled.Forum, "Group chat",
                when {
                    nearby.unreadChat > 0 -> "${nearby.unreadChat} new"
                    nearby.chat.isNotEmpty() -> nearby.chat.last().let {
                        "${it.from}: " + if (it.photoPath != null && it.text.isBlank()) "📷 Photo" else it.text
                    }
                    else -> "Messages and photos, no internet"
                },
                active = nearby.unreadChat > 0,
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.CHAT)) }
        )
        add(
            TogetherTile(
                Icons.Filled.Inbox, "Received",
                if (received.isEmpty()) "Songs friends send you" else "${received.size} songs",
            ) { onAction(NearbyVM.OpenPanelAction(NearbyPanel.RECEIVED)) }
        )
    }
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> TogetherTileView(tile, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private class TogetherTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun TogetherTileView(tile: TogetherTile, modifier: Modifier = Modifier) {
    val container = if (tile.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (tile.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .background(container, RoundedCornerShape(20.dp))
            .clickable(onClick = tile.onClick)
            .padding(16.dp)
    ) {
        Icon(tile.icon, contentDescription = null, tint = if (tile.active) content else MaterialTheme.colorScheme.primary)
        Text(tile.title, style = MaterialTheme.typography.titleSmall, color = content, modifier = Modifier.padding(top = 12.dp))
        Text(
            tile.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** one tappable row (icon, title, subtitle, arrow) */
@Composable
private fun TogetherRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** a "Together" activity on its own page */
@Composable
private fun PanelPage(
    panel: NearbyPanel,
    nearby: NearbyState,
    /** friend radar: where the phone points */
    heading: () -> Float?,
    radarAsMap: Boolean,
    offlineMaps: OfflineMapsState,
    received: List<ReceivedSong>,
    onSave: (Long) -> Unit,
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onAction: (Action) -> Unit,
) {
    BackHandler { onAction(NearbyVM.OpenPanelAction(null)) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = panel.title,
            subtitle = when (panel) {
                NearbyPanel.PARTY -> "Every connected phone plays the same song at the same moment"
                NearbyPanel.CAR_DJ -> "The phone in the car takes everyone's songs, in turns"
                NearbyPanel.SING -> "Your voice, live, on the phone playing the music"
                NearbyPanel.SHOUT_OUT -> "A quick voice message, played over the music"
                NearbyPanel.RECEIVED -> "The last songs friends sent you"
                NearbyPanel.RADAR -> "Where everyone is, even without internet (GPS)"
                NearbyPanel.CHAT -> "Messages and photos to everyone, no internet needed"
                NearbyPanel.BUMP -> "Tap two phones together to connect, no codes"
            },
            onBack = { onAction(NearbyVM.OpenPanelAction(null)) }
        )
        // the shout-out button sits low, under the thumb
        if (panel == NearbyPanel.SHOUT_OUT) {
            ShoutOutCard(
                recording = nearby.recordingShoutOut,
                playingFrom = nearby.shoutOutFrom,
                hasPermission = micPermission.first,
                askPermission = micPermission.second,
                onStart = { onAction(NearbyVM.StartShoutOutAction) },
                onStop = { send -> onAction(NearbyVM.StopShoutOutAction(send)) },
                hasFriends = nearby.friends.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        } else if (panel == NearbyPanel.BUMP) {
            BumpPanel(bump = nearby.bump, onAction = onAction, modifier = Modifier.weight(1f))
        } else if (panel == NearbyPanel.CHAT) {
            ChatPanel(nearby = nearby, onAction = onAction, modifier = Modifier.weight(1f))
        } else if (panel == NearbyPanel.SING) {
            // the big button sits low, under the thumb
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
                modifier = Modifier.weight(1f),
            )
        } else if (panel == NearbyPanel.RADAR) {
            // lays itself out (a map can't sit in a scrolling list: they'd fight over drags)
            FriendRadarPanel(
                nearby = nearby,
                heading = heading,
                asMap = radarAsMap,
                offlineMaps = offlineMaps,
                micPermission = micPermission,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        } else LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                when (panel) {
                    NearbyPanel.PARTY -> PartyCard(
                        party = nearby.party,
                        hasFriends = nearby.friends.isNotEmpty(),
                        onStart = { onAction(NearbyVM.StartPartyAction) },
                        onEnd = { onAction(NearbyVM.EndPartyAction) },
                    )
                    NearbyPanel.CAR_DJ -> CarDjCard(
                        on = nearby.carDj,
                        queue = nearby.djQueue,
                        onToggle = { onAction(NearbyVM.SetCarDjAction(it)) }
                    )
                    NearbyPanel.SING -> Unit
                    NearbyPanel.CHAT -> Unit
                    NearbyPanel.BUMP -> Unit
                    NearbyPanel.SHOUT_OUT -> Unit
                    NearbyPanel.RADAR -> Unit
                    NearbyPanel.RECEIVED -> if (received.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Inbox,
                            title = "Nothing yet",
                            message = "When a friend sends you a song, it shows up here for a while"
                        )
                    } else {
                        ReceivedSongsCard(
                            songs = received,
                            onPlay = { onAction(NearbyVM.PlayReceivedAction(it)) },
                            onSave = onSave,
                        )
                    }
                }
            }
            if (panel != NearbyPanel.RECEIVED && panel != NearbyPanel.RADAR && nearby.friends.isEmpty()) {
                item {
                    Text(
                        "Connect to a friend first (Nearby > Find friends).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** a connected friend: what they play, remote control, their songs, send them yours */
@Composable
private fun FriendPage(friend: ConnectedFriend, transfers: List<SongTransfer>, onAction: (Action) -> Unit) {
    BackHandler { onAction(NearbyVM.OpenFriendPageAction(null)) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = friend.name,
            subtitle = if (friend.djOpen) "Their phone is the Car DJ" else "Connected",
            onBack = { onAction(NearbyVM.OpenFriendPageAction(null)) }
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text("On their phone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val playing = friend.nowPlaying
                    Text(
                        playing?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (playing != null && playing.artist.isNotBlank()) {
                        Text(playing.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RemoteControls(friend, onAction)
                }
            }
            if (friend.djOpen) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) { UpNext(friend.upNext, emptyText = "Their queue is empty: add a song!", max = 6) }
                }
            }
            item {
                TogetherRow(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Their songs",
                    subtitle = if (friend.library.isEmpty() && !friend.libraryComplete) "Loading…"
                    else "${friend.library.size} songs · play there, or here",
                    onClick = { onAction(NearbyVM.OpenFriendAction(friend.endpointId)) }
                )
            }
            item {
                TogetherRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = if (friend.djOpen) "Add your songs to their queue" else "Send them one of your songs",
                    subtitle = if (friend.djOpen) "Your songs take turns with everyone's" else "It plays on their phone",
                    onClick = { onAction(NearbyVM.OpenSendAction(friend.endpointId, toQueue = friend.djOpen)) }
                )
            }
            if (transfers.any { it.endpointId == friend.endpointId }) {
                item { Transfers(transfers.filter { it.endpointId == friend.endpointId }) }
            }
            item {
                OutlinedButton(
                    onClick = { onAction(NearbyVM.DisconnectAction(friend.endpointId)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disconnect") }
            }
        }
    }
}

/** your name, visibility, what friends may do */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbySettingsSheet(nearby: NearbyState, onAction: (Action) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAction(NearbyVM.ShowSettingsAction(false)) }) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Nearby settings", style = MaterialTheme.typography.titleLarge)
            var name by remember(nearby.deviceName) { mutableStateOf(nearby.deviceName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name (friends see it)") },
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
            SettingSwitch(
                title = "Visible to friends",
                subtitle = if (nearby.sharing) "Friends nearby can find this phone" else "Friends can't find you (you can still find them)",
                checked = nearby.sharing,
                onChange = { onAction(NearbyVM.SetSharingAction(it)) }
            )
            SettingSwitch(
                title = "Friends can save my songs",
                subtitle = if (nearby.letFriendsSave) "Songs you send can be kept on their phone"
                else "Songs you send can only be listened to",
                checked = nearby.letFriendsSave,
                onChange = { onAction(NearbyVM.LetFriendsSaveAction(it)) }
            )
            if (nearby.rememberedPhones > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Remembered phones: ${nearby.rememberedPhones}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "They reconnect by themselves, no codes. Forget them to compare codes again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onAction(NearbyVM.ForgetRememberedAction) }) { Text("Forget") }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
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
