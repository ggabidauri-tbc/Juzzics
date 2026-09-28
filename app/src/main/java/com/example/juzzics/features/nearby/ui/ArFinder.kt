package com.example.juzzics.features.nearby.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.juzzics.features.nearby.domain.NearbyState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** someone (or a meeting point) to show over the camera: where, how far, in which color */
private data class ArTarget(val label: String, val bearing: Float, val distanceM: Float, val color: Color, val faded: Boolean)

/**
 * AR friend finder: hold the phone up, the camera shows the world, and name tags float where
 * friends (and meeting points) are. Built from the radar's GPS positions and the phone's
 * orientation (which way the camera points, tilt and all). Friends out of view get arrows at
 * the edge. North is found with the compass, so it's as good as the compass and GPS are:
 * great across a field or a square, rough up close or indoors.
 */
@Composable
fun ArFinder(nearby: NearbyState, onClose: () -> Unit) {
    val context = LocalContext.current
    fun cameraAllowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(cameraAllowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    // upright only: the math below assumes the phone is held in portrait
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (activity != null && before != null) activity.requestedOrientation = before }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (allowed) {
                CameraBackground()
                ArOverlay(nearby)
            } else {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "The AR finder shows your friends over the camera picture. Nothing is recorded or sent.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Allow the camera")
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}

/** the back camera's live picture, filling the screen */
@Composable
private fun CameraBackground() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** the tags and edge arrows, following the phone's orientation */
@Composable
private fun ArOverlay(nearby: NearbyState) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val palette = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF7FB2F0),
        Color(0xFFE08BC0),
        Color(0xFFB7A6F5),
    )

    // which way the phone is turned: a rotation matrix (phone -> world: east, magnetic north, up),
    // smoothed so tags glide instead of shaking
    var rotation by remember { mutableStateOf<FloatArray?>(null) }
    var unreliable by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val smoothed = FloatArray(4)
        var started = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val quaternion = FloatArray(4)
                SensorManager.getQuaternionFromVector(quaternion, event.values)
                // the same turn can be written with all signs flipped: stay on one side
                if (started && quaternion.indices.sumOf { (quaternion[it] * smoothed[it]).toDouble() } < 0) {
                    for (i in 0..3) quaternion[i] = -quaternion[i]
                }
                for (i in 0..3) smoothed[i] = if (started) smoothed[i] * 0.8f + quaternion[i] * 0.2f else quaternion[i]
                started = true
                // back to the rotation-vector form (x, y, z, w) the matrix helper takes
                val vector = floatArrayOf(smoothed[1], smoothed[2], smoothed[3], smoothed[0])
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, vector)
                rotation = matrix
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                unreliable = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        sensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensors.unregisterListener(listener) }
    }
    // how many screen pixels one unit of "sideways per forward" is (from the camera's lens)
    val lens = remember { cameraLens(context) }

    val me = nearby.radar.me
    val declination = remember(me?.lat?.roundToInt(), me?.lon?.roundToInt()) {
        me?.let { GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), 0f, System.currentTimeMillis()).declination } ?: 0f
    }
    val now = SystemClock.elapsedRealtime()
    val ids = nearby.radar.people.keys.sorted()
    val targets = buildList {
        if (me == null) return@buildList
        nearby.radar.people.forEach { (id, person) ->
            val (distance, bearing) = distanceAndBearingTo(me.lat, me.lon, person.fix.lat, person.fix.lon)
            add(
                ArTarget(
                    label = person.name,
                    bearing = bearing,
                    distanceM = distance,
                    color = palette[ids.indexOf(id).coerceAtLeast(0) % palette.size],
                    faded = now - person.fix.atElapsedMs > 60_000,
                )
            )
        }
        nearby.radar.pins.filterKeys { it !in nearby.radar.hiddenPins }.forEach { (_, pin) ->
            val (distance, bearing) = distanceAndBearingTo(me.lat, me.lon, pin.lat, pin.lon)
            add(ArTarget(if (pin.mine) "Meeting point" else "Meet · ${pin.setBy}", bearing, distance, PinColor, faded = false))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val matrix = rotation ?: return@Canvas
            val focal = maxOf(size.height * lens.first, size.width * lens.second)
            // nearest last, so it's drawn on top
            targets.sortedByDescending { it.distanceM }.forEach { target ->
                // the direction to them in the world (east, magnetic north, level), then in the phone's own axes
                val angle = Math.toRadians((target.bearing - declination).toDouble())
                val east = sin(angle).toFloat()
                val north = cos(angle).toFloat()
                val x = matrix[0] * east + matrix[3] * north
                val y = matrix[1] * east + matrix[4] * north
                val z = matrix[2] * east + matrix[5] * north
                // the camera looks out of the back of the phone (-z)
                val depth = -z
                val onScreen = if (depth > 0.05f) Offset(center.x + focal * x / depth, center.y - focal * y / depth) else null
                val visible = onScreen != null && onScreen.x in 0f..size.width && onScreen.y in 0f..size.height
                if (visible) drawTag(textMeasurer, onScreen!!, target)
                else drawEdgeArrow(textMeasurer, x, -y, target)
            }
        }

        // hints at the bottom
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hint = when {
                me == null -> "Finding your position… (outside is quickest)"
                targets.isEmpty() -> "Nobody's sharing their location yet"
                unreliable || nearby.radar.compassUnreliable -> "The compass is unsure: move the phone in a figure 8 a few times"
                me.accuracyM > 25 -> "Your position is rough (±${me.accuracyM.roundToInt()} m): tags may be a bit off"
                else -> "Hold the phone up and turn around: your friends' tags float where they are"
            }
            Text(hint, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** a floating tag: a dot on the spot, the name and distance above it; smaller when far */
private fun DrawScope.drawTag(textMeasurer: TextMeasurer, at: Offset, target: ArTarget) {
    val scale = when {
        target.distanceM < 50f -> 1f
        target.distanceM > 1_000f -> 0.75f
        else -> 1f - 0.25f * (target.distanceM - 50f) / 950f
    }
    val alpha = if (target.faded) 0.55f else 1f
    val name = textMeasurer.measure(
        target.label,
        TextStyle(color = Color.White.copy(alpha = alpha), fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1
    )
    val distance = textMeasurer.measure(
        arDistance(target.distanceM) + if (target.faded) " · a while ago" else "",
        TextStyle(color = Color.White.copy(alpha = 0.8f * alpha), fontSize = (12 * scale).sp),
        maxLines = 1
    )
    val padding = 10.dp.toPx() * scale
    val width = maxOf(name.size.width, distance.size.width) + padding * 2 + 14.dp.toPx() * scale
    val height = name.size.height + distance.size.height + padding * 1.4f
    val topLeft = Offset(at.x - width / 2f, at.y - height - 14.dp.toPx() * scale)
    // the bubble, a stem, and the dot where they are
    drawLine(target.color.copy(alpha = alpha), Offset(at.x, topLeft.y + height), at, strokeWidth = 2.dp.toPx())
    drawCircle(target.color.copy(alpha = alpha), radius = 6.dp.toPx() * scale, center = at)
    drawCircle(Color.White.copy(alpha = alpha), radius = 6.dp.toPx() * scale, center = at, style = Stroke(width = 2.dp.toPx()))
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f * alpha),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(12.dp.toPx() * scale)
    )
    drawCircle(target.color.copy(alpha = alpha), radius = 5.dp.toPx() * scale, center = Offset(topLeft.x + padding + 5.dp.toPx() * scale, topLeft.y + padding + name.size.height / 2f))
    val textX = topLeft.x + padding + 14.dp.toPx() * scale
    drawText(name, topLeft = Offset(textX, topLeft.y + padding * 0.7f))
    drawText(distance, topLeft = Offset(textX, topLeft.y + padding * 0.7f + name.size.height))
}

/** out of view: an arrow at the screen's edge pointing the way to turn, with the name */
private fun DrawScope.drawEdgeArrow(textMeasurer: TextMeasurer, dx: Float, dy: Float, target: ArTarget) {
    if (abs(dx) < 1e-4f && abs(dy) < 1e-4f) return
    val margin = 40.dp.toPx()
    val scale = minOf(
        if (dx != 0f) (size.width / 2f - margin) / abs(dx) else Float.MAX_VALUE,
        if (dy != 0f) (size.height / 2f - margin * 2) / abs(dy) else Float.MAX_VALUE,
    )
    val edge = Offset(center.x + dx * scale, center.y + dy * scale)
    val angle = atan2(dy, dx)
    val tip = 16.dp.toPx()
    val arrow = Path().apply {
        moveTo(edge.x + tip * cos(angle), edge.y + tip * sin(angle))
        lineTo(edge.x + tip * 0.8f * cos(angle + 2.4f), edge.y + tip * 0.8f * sin(angle + 2.4f))
        lineTo(edge.x + tip * 0.8f * cos(angle - 2.4f), edge.y + tip * 0.8f * sin(angle - 2.4f))
        close()
    }
    drawPath(arrow, target.color)
    drawPath(arrow, Color.White, style = Stroke(width = 1.5.dp.toPx()))
    val label = textMeasurer.measure(
        "${target.label} · ${arDistance(target.distanceM)}",
        TextStyle(color = Color.White, fontSize = 12.sp),
        maxLines = 1
    )
    val inward = Offset(-cos(angle) * 34.dp.toPx(), -sin(angle) * 34.dp.toPx())
    val x = (edge.x + inward.x - label.size.width / 2f).coerceIn(4.dp.toPx(), size.width - label.size.width - 4.dp.toPx())
    val y = (edge.y + inward.y - label.size.height / 2f).coerceIn(4.dp.toPx(), size.height - label.size.height - 4.dp.toPx())
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(x - 6.dp.toPx(), y - 2.dp.toPx()),
        size = Size(label.size.width + 12.dp.toPx(), label.size.height + 4.dp.toPx()),
        cornerRadius = CornerRadius(8.dp.toPx())
    )
    drawText(label, topLeft = Offset(x, y))
}

/**
 * the back camera's focal length relative to its sensor, (to the sensor's long side, to its
 * short side): in portrait the long side runs up the screen. Typical values if unknown.
 */
private fun cameraLens(context: Context): Pair<Float, Float> = runCatching {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = manager.cameraIdList.first {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    val characteristics = manager.getCameraCharacteristics(id)
    val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!.first()
    val sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
    val long = maxOf(sensor.width, sensor.height)
    val short = minOf(sensor.width, sensor.height)
    (focal / long) to (focal / short)
}.getOrDefault(0.77f to 1.02f)

private fun distanceAndBearingTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Pair<Float, Float> {
    val results = FloatArray(2)
    Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
    return results[0] to (results[1] + 360f) % 360f
}

private fun arDistance(meters: Float): String = when {
    meters < 1_000f -> "${meters.roundToInt()} m"
    meters < 10_000f -> "%.1f km".format(meters / 1_000f)
    else -> "${(meters / 1_000f).roundToInt()} km"
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
