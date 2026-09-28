package com.example.juzzics.features.nearby.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.features.nearby.domain.BumpState
import com.example.juzzics.features.nearby.ui.vm.NearbyVM

/**
 * "Bump to connect": both friends open this and tap their phones together. The phones feel the
 * same jolt at the same moment and connect, no codes. A big button does the same for phones
 * without a motion sensor (both press it at once).
 */
@Composable
fun BumpPanel(bump: BumpState?, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        onAction(NearbyVM.BumpModeAction(true))
        onDispose { onAction(NearbyVM.BumpModeAction(false)) }
    }
    val ready = (bump?.ready ?: 0) > 0
    val connected = bump?.connectedTo

    // the two phones lean in, as if about to touch
    val swing = rememberInfiniteTransition(label = "bump")
    val lean by swing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "lean"
    )

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Your friend opens Bump to connect too. Then tap your phones together, gently.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.weight(1f))

        if (connected != null) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Text(
                "Connected to $connected",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Bump another phone to add more friends",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            rotationZ = 12f * lean
                            translationX = 18.dp.toPx() * lean
                        }
                )
                Icon(
                    Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            rotationZ = -12f * lean
                            translationX = -18.dp.toPx() * lean
                        }
                )
            }
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!ready) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    when {
                        !ready -> "Looking for a phone in Bump to connect…"
                        bump?.noSensor == true -> "Ready! Both press the button below at the same time"
                        else -> "Ready! Tap your phones together"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = if (ready) 0.dp else 10.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))
        // the same, by hand: both press at once
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = ready) { onAction(NearbyVM.TapBumpAction) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Tap\ntogether",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "No bump felt? Both press this at the same moment",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(40.dp))
    }
}
