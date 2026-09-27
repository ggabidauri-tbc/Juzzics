package com.example.juzzics.features.nearby.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.features.nearby.domain.ChatMessage
import com.example.juzzics.features.nearby.domain.GeoFix
import com.example.juzzics.features.nearby.domain.NearbyPanel
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** one tap, the things you actually say when you're out together */
private val QuickReplies = listOf(
    "On my way",
    "Wait for me",
    "Where are you?",
    "I'm at the meeting point",
    "Leaving in 10 min",
    "All good 👍",
)

/**
 * The group chat: everyone connected (and further, through their phones), no internet.
 * Quick replies for one tap, typed messages, and "I'm here" to put your position with it.
 * Kept for this session only.
 */
@Composable
fun ChatPanel(nearby: NearbyState, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    // on screen: nothing's unread, no notifications
    DisposableEffect(Unit) {
        onAction(NearbyVM.ChatOpenAction(true))
        onDispose { onAction(NearbyVM.ChatOpenAction(false)) }
    }
    val messages = nearby.chat
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    var text by rememberSaveable { mutableStateOf("") }
    var withLocation by rememberSaveable { mutableStateOf(false) }
    val canSend = nearby.friends.isNotEmpty()
    fun send(message: String) {
        onAction(NearbyVM.SendChatAction(message, withLocation))
        withLocation = false
    }

    // the keyboard: lift the input above it (this page doesn't reach the screen's bottom edge,
    // the bottom bar and the mini player are under it, so only the part that overlaps)
    val view = LocalView.current
    val density = LocalDensity.current
    var gapBelow by remember { mutableIntStateOf(0) }
    val keyboard = WindowInsets.ime.getBottom(density)
    val lift = with(density) { (keyboard - gapBelow).coerceAtLeast(0).toDp() }

    Column(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { gapBelow = (view.height - it.boundsInWindow().bottom).roundToInt().coerceAtLeast(0) }
            .padding(bottom = lift)
    ) {
        if (messages.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    if (canSend) "Say something to everyone" else "Connect to a friend first",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "Works without internet, also with friends of friends further away. " +
                            "For when it's too loud to talk, or too quiet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        me = nearby.radar.me,
                        onShowOnMap = {
                            onAction(NearbyVM.RadarModeAction(true))
                            onAction(NearbyVM.OpenPanelAction(NearbyPanel.RADAR))
                        }
                    )
                }
            }
        }

        // one-tap replies
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickReplies.forEach { reply ->
                SuggestionChip(
                    onClick = { send(reply) },
                    enabled = canSend,
                    label = { Text(reply) }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "I'm here": the next message carries your position
            IconToggleButton(checked = withLocation, onCheckedChange = { withLocation = it }, enabled = canSend) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = if (withLocation) "Sending your location with it" else "Send your location with it",
                    tint = if (withLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                placeholder = { Text(if (withLocation) "Message + where you are" else "Message everyone") },
                enabled = canSend,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) {
                        send(text)
                        text = ""
                    }
                }),
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick = {
                    send(text.ifBlank { "I'm here" })
                    text = ""
                },
                enabled = canSend && (text.isNotBlank() || withLocation),
                modifier = Modifier.padding(start = 8.dp)
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

/** yours on the right, theirs on the left with their name; a place chip when it has a position */
@Composable
private fun ChatBubble(message: ChatMessage, me: GeoFix?, onShowOnMap: () -> Unit) {
    val time = remember(message.atMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.atMs)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (message.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.fromMe) 18.dp else 4.dp,
                        bottomEnd = if (message.fromMe) 4.dp else 18.dp,
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val content = if (message.fromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            if (!message.fromMe) {
                Text(
                    message.from + if (message.relayed) " · via friends" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = content)
            val lat = message.lat
            val lon = message.lon
            if (lat != null && lon != null) {
                val distance = if (!message.fromMe && me != null) {
                    FloatArray(1).also { Location.distanceBetween(me.lat, me.lon, lat, lon, it) }[0]
                } else null
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .background(content.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onShowOnMap)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                    Text(
                        (if (message.fromMe) "Your location" else "Here") +
                                (distance?.let { " · ${if (it < 1_000f) "${it.roundToInt()} m" else "%.1f km".format(it / 1_000f)} away" } ?: "") +
                                " · map",
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp)
            )
        }
    }
}
