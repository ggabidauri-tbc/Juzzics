package com.example.juzzics.features.nearby.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.features.nearby.data.OfflineMapsState
import com.example.juzzics.features.nearby.domain.GeoFix
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.RadarState
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** how long location sharing runs before it stops by itself */
private enum class ShareDuration(val label: String, val ms: Long?) {
    ONE_HOUR("1 hour", 60 * 60 * 1000L),
    THREE_HOURS("3 hours", 3 * 60 * 60 * 1000L),
    UNTIL_STOPPED("Until I stop", null),
}

/** someone on the radar: how far, which way (degrees from north), how old the position is */
private data class RadarFriend(
    val personId: String,
    val name: String,
    val distanceM: Float?,
    val bearing: Float?,
    val accuracyM: Float?,
    val ageSeconds: Long?,
    /**
     * closer than the two positions are exact (e.g. two rooms apart, ±15 m each): the
     * direction is a rough guess (shown faded)
     */
    val tooCloseForDirection: Boolean = false,
    /** out of this phone's range: the position came through friends' phones */
    val relayed: Boolean = false,
)

/** a meeting point, from here */
private data class RadarPin(
    val personId: String,
    val setBy: String,
    val mine: Boolean,
    val lat: Double,
    val lon: Double,
    val distanceM: Float?,
    val bearing: Float?,
)

/**
 * Friend radar and map: where everyone is, without internet. You in the middle, friends around
 * you (also friends of friends, through their phones), meeting points, "come to me", and a
 * walkie-talkie to talk to everyone.
 *
 * @param heading where the phone points (degrees from north), read while drawing so the radar
 * turns smoothly without recomposing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRadarPanel(
    nearby: NearbyState,
    heading: () -> Float?,
    /** map (true) or radar (false) */
    asMap: Boolean,
    offlineMaps: OfflineMapsState,
    /** walkie-talkie: is the mic allowed, and ask for it */
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun precise() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    var hasPrecise by remember { mutableStateOf(precise()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPrecise = precise()
        // start GPS now that it's allowed
        onAction(NearbyVM.RadarVisibleAction(false))
        onAction(NearbyVM.RadarVisibleAction(true))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPrecise = precise() }

    // "come to me" and the sharing notice are notifications: ask once (Android 13+)
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasPrecise) {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (hasPrecise && needed && !askedNotifications) {
            askedNotifications = true
            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // GPS + compass run while this is on screen
    DisposableEffect(Unit) {
        onAction(NearbyVM.RadarVisibleAction(true))
        onDispose { onAction(NearbyVM.RadarVisibleAction(false)) }
    }
    // "12 s ago" keeps counting
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }

    val radar = nearby.radar
    val me = radar.me
    val friends = radarFriends(nearby, now)
    val pins = radar.pins.filterKeys { it !in radar.hiddenPins }.map { (id, pin) ->
        val (distance, bearing) = if (me != null) distanceAndBearing(me.lat, me.lon, pin.lat, pin.lon) else null to null
        RadarPin(id, pin.setBy, pin.mine, pin.lat, pin.lon, distance, bearing)
    }
    // "Show on map" from the "come to me" card: the map opens there
    var focus by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    Column(modifier) {
        // Radar | Map
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = !asMap,
                onClick = { onAction(NearbyVM.RadarModeAction(false)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Radar") }
            )
            SegmentedButton(
                selected = asMap,
                onClick = { onAction(NearbyVM.RadarModeAction(true)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Map") }
            )
        }

        // things to act on first: permissions, someone calling you over
        val top: @Composable () -> Unit = {
            if (!hasPrecise) {
                Notice(
                    text = "Needs your precise location (GPS). It works without internet.",
                    button = "Allow precise location",
                    onClick = {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                )
            } else if (radar.locationOff) {
                Notice(
                    text = "Location is turned off on this phone. Turn it on to see where everyone is.",
                    button = "Open location settings",
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
                )
            }
            if (nearby.friends.isEmpty() && nearby.reconnecting.isEmpty()) {
                Hint("Connect to a friend first (Nearby > Find friends), then you see each other here.")
            }
            if (nearby.reconnecting.isNotEmpty()) {
                Hint("Out of range: ${nearby.reconnecting.joinToString()}. They reconnect by themselves when back.")
            }
            radar.comeToMe?.let { call ->
                val (distance, bearing) = if (me != null) distanceAndBearing(me.lat, me.lon, call.lat, call.lon) else null to null
                ComeToMeCard(
                    from = call.from,
                    distanceM = distance,
                    bearing = bearing,
                    ageSeconds = (now - call.atElapsedMs) / 1000,
                    heading = heading,
                    onShow = if (asMap) null else ({
                        focus = call.lat to call.lon
                        onAction(NearbyVM.RadarModeAction(true))
                    }),
                    onDismiss = { onAction(NearbyVM.DismissComeToMeAction) }
                )
            }
        }

        if (asMap) {
            MapMode(
                nearby = nearby,
                pins = pins,
                now = now,
                hasPrecise = hasPrecise,
                offlineMaps = offlineMaps,
                micPermission = micPermission,
                heading = heading,
                focus = focus,
                onFocused = { focus = null },
                top = top,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                top()
                RadarMode(
                    friends = friends,
                    pins = pins,
                    me = me,
                    nearby = nearby,
                    hasPrecise = hasPrecise,
                    heading = heading,
                    micPermission = micPermission,
                    onAction = onAction,
                )
            }
        }
    }
}

/** everyone sharing where they are (connected or through friends), then connected friends who aren't */
private fun radarFriends(nearby: NearbyState, now: Long): List<RadarFriend> {
    val radar = nearby.radar
    val me = radar.me
    val sharing = radar.people.map { (id, person) ->
        val fix = person.fix
        val (distance, bearing) = if (me != null) distanceAndBearing(me.lat, me.lon, fix.lat, fix.lon) else null to null
        val uncertainty = (me?.accuracyM ?: 0f) + fix.accuracyM
        RadarFriend(
            personId = id,
            name = person.name,
            distanceM = distance,
            bearing = bearing,
            accuracyM = fix.accuracyM,
            ageSeconds = (now - fix.atElapsedMs) / 1000,
            tooCloseForDirection = distance != null && distance < maxOf(uncertainty, MIN_DIRECTION_M),
            relayed = person.relayed,
        )
    }
    val notSharing = nearby.friends
        .filter { (it.phoneId ?: it.endpointId) !in radar.people }
        .map { RadarFriend(it.phoneId ?: it.endpointId, it.name, null, null, null, null) }
    return sharing + notSharing
}

@Composable
private fun RadarMode(
    friends: List<RadarFriend>,
    pins: List<RadarPin>,
    me: GeoFix?,
    nearby: NearbyState,
    hasPrecise: Boolean,
    heading: () -> Float?,
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onAction: (Action) -> Unit,
) {
    val radar = nearby.radar
    // only this flips the text below (the heading itself changes many times a second)
    val hasCompass by remember { derivedStateOf { heading() != null } }

    RadarView(friends = friends, pins = pins, hasMe = me != null, heading = heading)

    if (me != null && me.accuracyM > ROUGH_POSITION_M) {
        Hint("Your position is rough (±${me.accuracyM.roundToInt()} m), probably indoors. Distances and directions get exact outside.")
    }
    if (radar.compassUnreliable) {
        Hint("The compass needs calibrating: move the phone in a figure 8 a few times.")
    }

    // this phone's position
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            when {
                me == null -> "Finding your position… (outside, away from buildings is quickest)"
                else -> "Your position: ±${me.accuracyM.roundToInt()} m" +
                        if (!hasCompass) " · no compass, north is up" else ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    GroupActions(
        nearby = nearby,
        micPermission = micPermission,
        onMeetHere = me?.let { { onAction(NearbyVM.SetMeetingPointAction(it.lat, it.lon)) } },
        onAction = onAction,
    )

    pins.forEach { pin -> PinRow(pin, heading, onAction) }
    HiddenPinsButton(radar, onAction)

    SharingCard(radar = radar, hasPrecise = hasPrecise, onAction = onAction)

    // everyone, closest first
    friends.sortedBy { it.distanceM ?: Float.MAX_VALUE }.forEach { friend ->
        FriendDistanceRow(friend, heading)
    }
}

/**
 * Hold to talk (walkie-talkie to everyone), "come to me", and in radar mode "meet at my spot".
 * [onMeetHere] null: no "meet here" button (the map uses a long-press instead)
 */
@Composable
private fun GroupActions(
    nearby: NearbyState,
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onMeetHere: (() -> Unit)?,
    onAction: (Action) -> Unit,
) {
    val hasFriends = nearby.friends.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nearby.talker?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "$name is talking…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TalkButton(
                talking = nearby.talking,
                enabled = hasFriends && nearby.singingTo == null,
                micPermission = micPermission,
                onTalk = { on -> onAction(NearbyVM.TalkAction(on)) },
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = { onAction(NearbyVM.CallOverAction) },
                enabled = hasFriends,
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Come to me", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (onMeetHere != null) {
            TextButton(onClick = onMeetHere, enabled = hasFriends) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Set a meeting point where I am", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** press and hold: your voice goes live to everyone connected; let go to stop */
@Composable
private fun TalkButton(
    talking: Boolean,
    enabled: Boolean,
    micPermission: Pair<() -> Boolean, () -> Unit>,
    onTalk: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTalk by rememberUpdatedState(onTalk)
    val currentPermission by rememberUpdatedState(micPermission)
    val background by animateColorAsState(
        when {
            talking -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "talk"
    )
    val content = when {
        talking -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!currentEnabled) return@detectTapGestures
                    if (!currentPermission.first()) {
                        currentPermission.second()
                        return@detectTapGestures
                    }
                    currentOnTalk(true)
                    // also when the button goes away while held: the mic never stays on
                    try {
                        tryAwaitRelease()
                    } finally {
                        currentOnTalk(false)
                    }
                })
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = content)
        Text(
            if (talking) "Talking… let go to stop" else "Hold to talk",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** "Nika asks you to come to them · 180 m ↗" */
@Composable
private fun ComeToMeCard(
    from: String,
    distanceM: Float?,
    bearing: Float?,
    ageSeconds: Long,
    heading: () -> Float?,
    /** null: already on the map */
    onShow: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (bearing != null) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.graphicsLayer { rotationZ = bearing - (heading() ?: 0f) }
                    )
                } else {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    "$from asks you to come to them",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    (distanceM?.let { "${formatDistance(it)} away · " } ?: "") +
                            if (ageSeconds < 5) "just now" else "${ago(ageSeconds)} ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
            if (onShow != null) TextButton(onClick = onShow) { Text("Show on map") }
        }
    }
}

/** a meeting point: who set it, how far, which way; clear yours / hide theirs */
@Composable
private fun PinRow(pin: RadarPin, heading: () -> Float?, onAction: (Action) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(PinColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val bearing = pin.bearing
            val close = (pin.distanceM ?: 0f) < MIN_DIRECTION_M
            if (bearing != null && !close) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = bearing - (heading() ?: 0f) }
                )
            } else {
                Icon(Icons.Filled.Place, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                if (pin.mine) "Your meeting point" else "Meeting point · set by ${pin.setBy}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pin.distanceM?.let { if (it < MIN_DIRECTION_M) "You're there" else "${formatDistance(it)} away" }
                    ?: "Waiting for your position…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        TextButton(onClick = {
            onAction(if (pin.mine) NearbyVM.ClearMeetingPointAction else NearbyVM.HideMeetingPointAction(pin.personId))
        }) { Text(if (pin.mine) "Remove" else "Hide") }
    }
}

/** "Show 2 hidden meeting points" */
@Composable
private fun HiddenPinsButton(radar: RadarState, onAction: (Action) -> Unit) {
    val hidden = radar.hiddenPins.count { it in radar.pins }
    if (hidden == 0) return
    TextButton(onClick = { onAction(NearbyVM.ShowHiddenPinsAction) }) {
        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            if (hidden == 1) "Show the hidden meeting point" else "Show $hidden hidden meeting points",
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** the map, the group buttons and sharing under it; offline areas in a sheet */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapMode(
    nearby: NearbyState,
    pins: List<RadarPin>,
    now: Long,
    hasPrecise: Boolean,
    offlineMaps: OfflineMapsState,
    micPermission: Pair<() -> Boolean, () -> Unit>,
    heading: () -> Float?,
    /** a place to show once the map is ready */
    focus: Pair<Double, Double>?,
    onFocused: () -> Unit,
    top: @Composable () -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier,
) {
    val radar = nearby.radar
    val handle = rememberMapHandle()
    LaunchedEffect(Unit) { onAction(NearbyVM.RefreshMapAreasAction) }
    LaunchedEffect(focus, handle.map, handle.failed) {
        val (lat, lon) = focus ?: return@LaunchedEffect
        if (handle.map != null || handle.failed) {
            handle.centerOnPoint(lat, lon)
            onFocused()
        }
    }

    // everyone gets a color: you in the app color, others in turns (same color on map and trails)
    val meColor = MaterialTheme.colorScheme.primary
    val palette = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF7FB2F0),
        Color(0xFFE08BC0),
        Color(0xFFB7A6F5),
    )
    val ids = (radar.people.keys + radar.trails.keys - RadarState.ME).sorted()
    fun colorOf(id: String) = if (id == RadarState.ME) meColor else palette[ids.indexOf(id).coerceAtLeast(0) % palette.size]
    val people = buildList {
        radar.me?.let { add(MapPerson(RadarState.ME, "You", it.lat, it.lon, meColor, stale = false, isMe = true)) }
        radar.people.forEach { (id, person) ->
            add(
                MapPerson(
                    key = id,
                    name = person.name,
                    lat = person.fix.lat,
                    lon = person.fix.lon,
                    color = colorOf(id),
                    stale = now - person.fix.atElapsedMs > STALE_MS,
                    isMe = false,
                )
            )
        }
    }
    val trails = radar.trails.map { (key, points) -> MapTrail(key, colorOf(key), points) }
    val mapPins = pins.map { MapPin(it.personId, if (it.mine) "Meet here" else "Meet · ${it.setBy}", it.lat, it.lon) }

    var pinAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showOffline by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        top()
        FriendMap(
            people = people,
            trails = trails,
            pins = mapPins,
            handle = handle,
            onLongPress = { lat, lon -> if (nearby.friends.isNotEmpty()) pinAt = lat to lon },
            onOfflineMaps = { showOffline = true },
            downloading = offlineMaps.downloadProgress != null,
            heading = heading,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        pins.forEach { pin -> PinRow(pin, heading = { null }, onAction = onAction) }
        HiddenPinsButton(radar, onAction)
        GroupActions(nearby = nearby, micPermission = micPermission, onMeetHere = null, onAction = onAction)
        if (nearby.friends.isNotEmpty() && pins.none { it.mine }) {
            Hint("Long-press the map to set a meeting point for everyone.")
        }
        SharingCard(radar = radar, hasPrecise = hasPrecise, onAction = onAction)
    }

    pinAt?.let { (lat, lon) ->
        AlertDialog(
            onDismissRequest = { pinAt = null },
            icon = { Icon(Icons.Filled.Place, contentDescription = null) },
            title = { Text("Meet here?") },
            text = {
                Text(
                    "Everyone connected gets a buzz and sees this spot on their radar and map, with an arrow and the distance." +
                            if (pins.any { it.mine }) " It replaces your current meeting point." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAction(NearbyVM.SetMeetingPointAction(lat, lon))
                    pinAt = null
                }) { Text("Share it") }
            },
            dismissButton = { TextButton(onClick = { pinAt = null }) { Text("Cancel") } }
        )
    }
    if (showOffline) {
        OfflineMapsSheet(
            offlineMaps = offlineMaps,
            handle = handle,
            hasTrails = radar.trails.values.any { it.isNotEmpty() },
            onAction = onAction,
            onDismiss = { showOffline = false }
        )
    }
}

/** save what's on screen for offline use, the saved areas, clearing trails */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMapsSheet(
    offlineMaps: OfflineMapsState,
    handle: MapHandle,
    hasTrails: Boolean,
    onAction: (Action) -> Unit,
    onDismiss: () -> Unit,
) {
    var askName by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 32.dp)) {
            Text("Offline maps", style = MaterialTheme.typography.titleLarge)
            Text(
                "Save the area on screen while you have internet. Then the map works there without signal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp, end = 8.dp)
            )
            val progress = offlineMaps.downloadProgress
            if (progress != null) {
                Text("Saving… ${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 8.dp)
                )
                TextButton(onClick = { onAction(NearbyVM.CancelMapDownloadAction) }) { Text("Cancel") }
            } else {
                Button(
                    onClick = { askName = true },
                    enabled = !handle.failed && handle.map != null
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Save the area on screen", modifier = Modifier.padding(start = 6.dp))
                }
                if (handle.failed) {
                    Text(
                        "Needs internet to save an area.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (offlineMaps.areas.isNotEmpty()) {
                Text(
                    "Saved areas",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                )
                offlineMaps.areas.forEach { area ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = area.bounds != null && !handle.failed) {
                                area.bounds?.let { handle.showArea(it) }
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(area.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatSize(area.sizeBytes) + if (!area.complete) " · not fully saved" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onAction(NearbyVM.DeleteMapAreaAction(area.id)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${area.name}")
                        }
                    }
                }
            }

            if (hasTrails) {
                TextButton(
                    onClick = {
                        onAction(NearbyVM.ClearTrailsAction)
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Clear everyone's trails") }
            }
        }
    }
    if (askName) {
        SaveAreaDialog(
            onSave = { name ->
                askName = false
                handle.visibleBounds()?.let { onAction(NearbyVM.SaveMapAreaAction(it, name)) }
            },
            onDismiss = { askName = false }
        )
    }
}

@Composable
private fun SaveAreaDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val default = remember { "Area · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) }
    var name by remember { mutableStateOf(default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this area") },
        text = {
            Column {
                Text(
                    "What's on screen gets saved, down to street and trail level. Zoom out a bit to save more; " +
                            "a town takes a few MB, a whole region more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { default }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** sharing: for how long, then on / off */
@Composable
private fun SharingCard(radar: RadarState, hasPrecise: Boolean, onAction: (Action) -> Unit) {
    var duration by remember { mutableStateOf(ShareDuration.ONE_HOUR) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share my location", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (radar.sharing) {
                        "Friends see where you are, also with the screen off" +
                                (radar.sharingUntilMs?.let { " · until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }
                                    ?: " · until you stop it")
                    } else "Only friends see it (also through each other's phones), nothing goes online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = radar.sharing,
                onCheckedChange = { on -> onAction(NearbyVM.LocationSharingAction(on, duration.ms)) },
                enabled = hasPrecise
            )
        }
        if (!radar.sharing) {
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareDuration.entries.forEach { option ->
                    FilterChip(
                        selected = option == duration,
                        onClick = { duration = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) { Text(button) }
    }
}

/** "Nika · 180 m ↗ · ±10 m · 5 s ago" with an arrow pointing to them */
@Composable
private fun FriendDistanceRow(friend: RadarFriend, heading: () -> Float?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val bearing = friend.bearing
            if (bearing != null) {
                // points to the friend, relative to where the phone faces (faded: a rough guess)
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (friend.tooCloseForDirection) 0.45f else 1f),
                    modifier = Modifier.graphicsLayer { rotationZ = bearing - (heading() ?: 0f) }
                )
            } else {
                Text(
                    friend.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(friend.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val age = friend.ageSeconds
            val seen = when {
                age == null -> ""
                age < 5 -> "now"
                age > STALE_MS / 1000 -> "last seen ${ago(age)} ago"
                else -> "${ago(age)} ago"
            }
            Text(
                when {
                    age == null -> "Not sharing their location"
                    friend.distanceM == null -> "Waiting for your position…"
                    else -> (if (friend.relayed) "Through friends' phones · " else "") +
                            "±${friend.accuracyM?.roundToInt() ?: 0} m · " +
                            (if (friend.tooCloseForDirection) "direction rough · " else "") + seen
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        friend.distanceM?.let {
            Text(
                formatDistance(it),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Rings at nice distances, you in the middle, friends as dots with their names, meeting points
 * as diamonds. Turns with [heading] (read while drawing, smooth); an "N" shows where north is.
 */
@Composable
private fun RadarView(friends: List<RadarFriend>, pins: List<RadarPin>, hasMe: Boolean, heading: () -> Float?) {
    val textMeasurer = rememberTextMeasurer()
    val ringColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val meColor = MaterialTheme.colorScheme.primary
    val friendColor = MaterialTheme.colorScheme.secondary
    val nameColor = MaterialTheme.colorScheme.onSurface
    // everyone at their spot, even very close (then it's a best guess, with a halo for how rough)
    val placed = friends.filter { it.distanceM != null && it.bearing != null }
    val placedPins = pins.filter { it.distanceM != null && it.bearing != null }
    // the rings fit the farthest one (not how rough the positions are): 15 m when everyone's close
    val range = niceRange(
        maxOf(
            placed.maxOfOrNull { it.distanceM ?: 0f } ?: 0f,
            placedPins.maxOfOrNull { it.distanceM ?: 0f } ?: 0f,
        )
    )

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
    ) {
        val radius = size.minDimension / 2f * 0.86f
        val facing = heading() ?: 0f
        fun position(distance: Float, bearing: Float): Offset {
            val angle = Math.toRadians((bearing - facing).toDouble())
            val r = radius * (distance / range).coerceIn(0f, 1f)
            return Offset(center.x + r * sin(angle).toFloat(), center.y - r * cos(angle).toFloat())
        }

        for (ring in 1..3) {
            val r = radius * ring / 3f
            drawCircle(ringColor.copy(alpha = 0.5f), radius = r, style = Stroke(width = 1.dp.toPx()))
            drawText(
                textMeasurer,
                formatDistance(range * ring / 3f),
                topLeft = Offset(center.x + 6.dp.toPx(), center.y - r + 2.dp.toPx()),
                style = TextStyle(color = labelColor, fontSize = 10.sp)
            )
        }

        // north on the edge
        val north = Math.toRadians((-facing).toDouble())
        val northPos = Offset(center.x + (radius + 10.dp.toPx()) * sin(north).toFloat(), center.y - (radius + 10.dp.toPx()) * cos(north).toFloat())
        val n = textMeasurer.measure("N", TextStyle(color = meColor, fontSize = 13.sp))
        drawText(n, topLeft = Offset(northPos.x - n.size.width / 2f, northPos.y - n.size.height / 2f))

        // meeting points: diamonds
        placedPins.forEach { pin ->
            val pos = position(pin.distanceM ?: 0f, pin.bearing ?: 0f)
            val s = 9.dp.toPx()
            val diamond = Path().apply {
                moveTo(pos.x, pos.y - s)
                lineTo(pos.x + s, pos.y)
                lineTo(pos.x, pos.y + s)
                lineTo(pos.x - s, pos.y)
                close()
            }
            drawPath(diamond, PinColor)
            val label = textMeasurer.measure(
                if (pin.mine) "Meet" else "Meet · ${pin.setBy}",
                TextStyle(color = nameColor, fontSize = 11.sp, textAlign = TextAlign.Center),
                maxLines = 1
            )
            drawText(label, topLeft = Offset(pos.x - label.size.width / 2f, pos.y + 11.dp.toPx()))
        }

        // friends
        placed.forEach { friend ->
            val pos = position(friend.distanceM ?: 0f, friend.bearing ?: 0f)
            val stale = (friend.ageSeconds ?: 0) > STALE_MS / 1000
            // how rough their position is: a faint halo that size (on the radar's scale)
            friend.accuracyM?.let { accuracy ->
                val halo = (radius * accuracy / range).coerceAtMost(radius)
                if (halo > 16.dp.toPx()) {
                    drawCircle(friendColor.copy(alpha = 0.08f), radius = halo, center = pos)
                    drawCircle(friendColor.copy(alpha = 0.25f), radius = halo, center = pos, style = Stroke(width = 1.dp.toPx()))
                }
            }
            drawCircle(friendColor.copy(alpha = if (stale) 0.35f else 0.25f), radius = 14.dp.toPx(), center = pos)
            drawCircle(friendColor.copy(alpha = if (stale) 0.5f else 1f), radius = 6.dp.toPx(), center = pos)
            if (friend.relayed) {
                drawCircle(friendColor.copy(alpha = 0.6f), radius = 10.dp.toPx(), center = pos, style = Stroke(width = 1.dp.toPx()))
            }
            val label = textMeasurer.measure(
                friend.name,
                TextStyle(color = nameColor.copy(alpha = if (stale) 0.5f else 1f), fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 1
            )
            drawText(label, topLeft = Offset(pos.x - label.size.width / 2f, pos.y + 10.dp.toPx()))
        }

        // you: a small arrow pointing up (the way you face)
        if (hasMe) {
            val s = 10.dp.toPx()
            val arrow = Path().apply {
                moveTo(center.x, center.y - s)
                lineTo(center.x + s * 0.7f, center.y + s * 0.7f)
                lineTo(center.x, center.y + s * 0.3f)
                lineTo(center.x - s * 0.7f, center.y + s * 0.7f)
                close()
            }
            drawPath(arrow, meColor)
        } else {
            drawCircle(meColor.copy(alpha = 0.4f), radius = 6.dp.toPx(), center = center)
        }
    }
}

/** meeting points: gold, the same on the radar and the map */
internal val PinColor = Color(0xFFF2C66D)

/** a position older than this is drawn faded ("last seen") */
private const val STALE_MS = 60_000L

/** under this, a direction is never shown (GPS can't tell that finely) */
private const val MIN_DIRECTION_M = 15f

/** your own position is rougher than this: say so */
private const val ROUGH_POSITION_M = 25f

/** meters and degrees from north, from one point to another */
private fun distanceAndBearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Pair<Float, Float> {
    val results = FloatArray(2)
    Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
    return results[0] to (results[1] + 360f) % 360f
}

/**
 * the outer ring: a round distance a bit beyond the farthest friend. Each divides by 3 into
 * round rings (15 m -> 5 / 10 / 15 m), so a friend in the next room isn't lost in a 100 m radar.
 */
private fun niceRange(farthest: Float): Float {
    val steps = listOf(
        15f, 30f, 60f, 90f, 150f, 300f, 600f, 900f, 1_500f, 3_000f, 6_000f, 9_000f,
        15_000f, 30_000f, 60_000f, 90_000f, 150_000f
    )
    return steps.firstOrNull { it >= farthest * 1.15f } ?: steps.last()
}

private fun formatDistance(meters: Float): String = when {
    meters < 100f -> "${meters.roundToInt()} m"
    meters < 1_000f -> "${(meters / 5f).roundToInt() * 5} m"
    meters < 10_000f -> "%.1f km".format(meters / 1_000f)
    else -> "${(meters / 1_000f).roundToInt()} km"
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "Size unknown"
    bytes < 1_000_000L -> "${bytes / 1_000} KB"
    else -> "%.1f MB".format(bytes / 1_000_000f)
}

private fun ago(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3_600 -> "${seconds / 60} min"
    else -> "${seconds / 3_600} h"
}
