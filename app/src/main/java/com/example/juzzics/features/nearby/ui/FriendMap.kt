package com.example.juzzics.features.nearby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.juzzics.features.nearby.data.OfflineMaps
import com.example.juzzics.features.nearby.domain.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** someone on the map */
internal data class MapPerson(
    val key: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val color: Color,
    /** position older than a minute: drawn faded */
    val stale: Boolean,
    val isMe: Boolean,
)

/** where someone walked, oldest point first */
internal data class MapTrail(val key: String, val color: Color, val points: List<GeoPoint>)

/** a meeting point */
internal data class MapPin(val key: String, val label: String, val lat: Double, val lon: Double)

/**
 * What the map screen needs from the map: what's on screen (to save it for offline) and
 * whether the real map failed (no internet and no saved area here).
 */
@Stable
internal class MapHandle {
    var map: MapLibreMap? by mutableStateOf(null)
    /** the real map couldn't load: the plain trail view shows instead */
    var failed by mutableStateOf(false)
    /** bumped by "Try again" to make a fresh map */
    var attempt by mutableIntStateOf(0)
    /** the plain trail view's camera */
    val trailView = TrailView()
    /** the map already went somewhere (your position or a place asked for): don't jump to you again */
    var placed = false
    /** bumped while the map moves: the arrows on top follow */
    var cameraTick by mutableIntStateOf(0)

    /** where a place is on screen (pixels), on the real map or the plain trail view */
    fun project(lat: Double, lon: Double): Offset? {
        if (failed) return trailView.project(lat, lon)
        val map = map ?: return null
        val point = map.projection.toScreenLocation(LatLng(lat, lon))
        return Offset(point.x, point.y)
    }

    /** the part of the map on screen, to save for offline */
    fun visibleBounds(): LatLngBounds? = map?.projection?.visibleRegion?.latLngBounds

    fun showArea(bounds: LatLngBounds) {
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48))
    }

    fun centerOn(person: MapPerson) {
        val map = map
        if (map != null && !failed) {
            val zoom = maxOf(map.cameraPosition.zoom, 16.0)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(person.lat, person.lon), zoom))
        } else trailView.followMe()
    }

    /** shows a place, e.g. where a friend called you over from */
    fun centerOnPoint(lat: Double, lon: Double) {
        placed = true
        val map = map
        if (map != null && !failed) {
            val zoom = maxOf(map.cameraPosition.zoom, 16.0)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
        } else trailView.fitAll()
    }

    fun showEveryone(people: List<MapPerson>, trails: List<MapTrail>) {
        val map = map
        if (map != null && !failed) {
            val points = people.map { LatLng(it.lat, it.lon) }
                .ifEmpty { trails.flatMap { trail -> trail.points.map { LatLng(it.lat, it.lon) } } }
            when {
                points.isEmpty() -> Unit
                // one spot (or everyone at the same one): a bounds box would be empty
                points.distinct().size == 1 ->
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 16.0))
                else -> {
                    val bounds = LatLngBounds.Builder().apply { points.forEach { include(it) } }.build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 96))
                }
            }
        } else trailView.fitAll()
    }
}

@Composable
internal fun rememberMapHandle() = remember { MapHandle() }

/**
 * A real map (OpenFreeMap, works offline in saved areas) with everyone's position and trail.
 * Without internet and without a saved area the plain trail view shows instead: the same
 * people and trails on a grid, which needs nothing but GPS.
 */
@Composable
internal fun FriendMap(
    people: List<MapPerson>,
    trails: List<MapTrail>,
    pins: List<MapPin>,
    handle: MapHandle,
    /** a long press on the map: a meeting point there? */
    onLongPress: (lat: Double, lon: Double) -> Unit,
    onOfflineMaps: () -> Unit,
    /** an offline area is downloading (the button shows it) */
    downloading: Boolean,
    /** where the phone points (degrees from north): your dot shows it as a beam */
    heading: () -> Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (handle.failed) {
            TrailMap(people, trails, pins, handle.trailView, onLongPress, Modifier.fillMaxSize())
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "No map here offline · showing trails",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp)
                )
                TextButton(onClick = {
                    handle.failed = false
                    handle.attempt++
                }) { Text("Retry") }
            }
        } else {
            key(handle.attempt) { LiveMap(people, trails, pins, handle, onLongPress, Modifier.fillMaxSize()) }
        }
        MapArrows(people, pins, handle, heading, Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (handle.failed) 64.dp else 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val me = people.firstOrNull { it.isMe }
            if (me != null) {
                SmallFloatingActionButton(
                    onClick = { handle.centerOn(me) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Filled.MyLocation, contentDescription = "Center on me") }
            }
            SmallFloatingActionButton(
                onClick = { handle.showEveryone(people, trails) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Icon(Icons.Filled.ZoomOutMap, contentDescription = "Show everyone") }
            SmallFloatingActionButton(
                onClick = onOfflineMaps,
                containerColor = if (downloading) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (downloading) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            ) { Icon(Icons.Filled.DownloadForOffline, contentDescription = "Offline maps") }
        }
    }
}

/** MapLibre map view, with sources for people and trails that follow the data */
@Composable
private fun LiveMap(
    people: List<MapPerson>,
    trails: List<MapTrail>,
    pins: List<MapPin>,
    handle: MapHandle,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        // must run before the first MapView is made
        MapLibre.getInstance(context)
        // a TextureView, so the rounded corners and the buttons on top work (a SurfaceView ignores both)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }
    var style by remember { mutableStateOf<Style?>(null) }
    val haloColor = MaterialTheme.colorScheme.surface.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        // replays ON_CREATE..the current state right away
        lifecycle.addObserver(observer)

        mapView.addOnDidFailLoadingMapListener { handle.failed = true }
        mapView.getMapAsync { map ->
            map.uiSettings.setRotateGesturesEnabled(false)
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setCompassEnabled(false)
            map.uiSettings.setLogoEnabled(false)
            // the OpenStreetMap / OpenFreeMap credit stays (the "i" in the corner)
            map.uiSettings.setAttributionEnabled(true)
            map.addOnCameraMoveListener { handle.cameraTick++ }
            map.addOnMapLongClickListener { point ->
                currentOnLongPress(point.latitude, point.longitude)
                true
            }
            map.setStyle(OfflineMaps.STYLE_URL) { loaded ->
                addPeopleAndTrails(loaded, textColor, haloColor)
                style = loaded
            }
            handle.map = map
        }

        onDispose {
            lifecycle.removeObserver(observer)
            handle.map = null
            style = null
            if (!destroyed) {
                val state = lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // data -> map
    LaunchedEffect(style, people) {
        style?.getSourceAs<GeoJsonSource>(PEOPLE_SOURCE)?.setGeoJson(peopleFeatures(people))
    }
    LaunchedEffect(style, trails) {
        style?.getSourceAs<GeoJsonSource>(TRAILS_SOURCE)?.setGeoJson(trailFeatures(trails))
    }
    LaunchedEffect(style, pins) {
        style?.getSourceAs<GeoJsonSource>(PINS_SOURCE)?.setGeoJson(pinFeatures(pins))
    }
    // the first time we know where you are, go there
    val map = handle.map
    LaunchedEffect(map, people.any { it.isMe }) {
        if (!handle.placed && map != null) {
            val me = people.firstOrNull { it.isMe } ?: return@LaunchedEffect
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(me.lat, me.lon), 16.0))
            handle.placed = true
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * On top of the map: a beam from your dot the way the phone points, and for everyone (and every
 * meeting point) off the screen an arrow at the edge pointing to them, with how far.
 */
@Composable
private fun MapArrows(
    people: List<MapPerson>,
    pins: List<MapPin>,
    handle: MapHandle,
    heading: () -> Float?,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        // read so this redraws when the map / trail view moves
        handle.cameraTick
        handle.trailView.mode
        handle.trailView.origin
        handle.trailView.scale
        val me = people.firstOrNull { it.isMe }
        val mePos = me?.let { handle.project(it.lat, it.lon) }

        // your heading: a soft beam (north is up on this map)
        val facing = heading()
        if (me != null && mePos != null && facing != null) {
            val length = 44.dp.toPx()
            val spread = Math.toRadians(28.0)
            val angle = Math.toRadians(facing.toDouble())
            val beam = Path().apply {
                moveTo(mePos.x, mePos.y)
                lineTo(mePos.x + length * sin(angle - spread).toFloat(), mePos.y - length * cos(angle - spread).toFloat())
                lineTo(mePos.x + length * sin(angle + spread).toFloat(), mePos.y - length * cos(angle + spread).toFloat())
                close()
            }
            drawPath(beam, me.color.copy(alpha = 0.35f))
        }

        // off screen: arrows at the edge
        val margin = 26.dp.toPx()
        val targets = people.filterNot { it.isMe }.map { Triple(it.lat to it.lon, it.color, it.name) } +
                pins.map { Triple(it.lat to it.lon, PinColor, it.label) }
        targets.forEach { (place, color, name) ->
            val pos = handle.project(place.first, place.second) ?: return@forEach
            val inside = pos.x in 0f..size.width && pos.y in 0f..size.height
            if (inside) return@forEach
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            if (dx == 0f && dy == 0f) return@forEach
            val scale = minOf(
                if (dx != 0f) (size.width / 2f - margin) / kotlin.math.abs(dx) else Float.MAX_VALUE,
                if (dy != 0f) (size.height / 2f - margin) / kotlin.math.abs(dy) else Float.MAX_VALUE,
            )
            val edge = Offset(center.x + dx * scale, center.y + dy * scale)
            val angle = kotlin.math.atan2(dy, dx)
            val tip = 12.dp.toPx()
            val arrow = Path().apply {
                moveTo(edge.x + tip * cos(angle), edge.y + tip * sin(angle))
                lineTo(edge.x + tip * 0.8f * cos(angle + 2.4f), edge.y + tip * 0.8f * sin(angle + 2.4f))
                lineTo(edge.x + tip * 0.8f * cos(angle - 2.4f), edge.y + tip * 0.8f * sin(angle - 2.4f))
                close()
            }
            drawPath(arrow, color)
            drawPath(arrow, labelColor.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))
            val distance = me?.let {
                FloatArray(1).also { r -> android.location.Location.distanceBetween(it.lat, it.lon, place.first, place.second, r) }[0]
            }
            val text = name + (distance?.let { " · " + mapDistance(it) } ?: "")
            val label = textMeasurer.measure(text, TextStyle(color = labelColor, fontSize = 11.sp), maxLines = 1)
            // the label sits just inside the arrow, kept on screen
            val inward = Offset(-cos(angle) * 22.dp.toPx(), -sin(angle) * 22.dp.toPx())
            val x = (edge.x + inward.x - label.size.width / 2f).coerceIn(4.dp.toPx(), size.width - label.size.width - 4.dp.toPx())
            val y = (edge.y + inward.y - label.size.height / 2f).coerceIn(4.dp.toPx(), size.height - label.size.height - 4.dp.toPx())
            drawRoundRect(
                chipColor.copy(alpha = 0.85f),
                topLeft = Offset(x - 4.dp.toPx(), y - 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(label.size.width + 8.dp.toPx(), label.size.height + 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
            drawText(label, topLeft = Offset(x, y))
        }
    }
}

private fun mapDistance(meters: Float): String = when {
    meters < 1_000f -> "${meters.roundToInt()} m"
    meters < 10_000f -> "%.1f km".format(meters / 1_000f)
    else -> "${(meters / 1_000f).roundToInt()} km"
}

private const val PEOPLE_SOURCE = "juzzics-people"
private const val TRAILS_SOURCE = "juzzics-trails"
private const val PINS_SOURCE = "juzzics-pins"

private fun addPeopleAndTrails(style: Style, textColor: Int, haloColor: Int) {
    val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
    style.addSource(GeoJsonSource(TRAILS_SOURCE, empty))
    style.addSource(GeoJsonSource(PEOPLE_SOURCE, empty))
    style.addSource(GeoJsonSource(PINS_SOURCE, empty))
    style.addLayer(
        LineLayer("juzzics-trail-lines", TRAILS_SOURCE).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // meeting points: a big gold ring under the people
    style.addLayer(
        CircleLayer("juzzics-pin-dots", PINS_SOURCE).withProperties(
            PropertyFactory.circleColor(PinColor.toArgb()),
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleStrokeColor(textColor),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    style.addLayer(
        SymbolLayer("juzzics-pin-names", PINS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textOffset(arrayOf(0f, -1.4f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
            PropertyFactory.textColor(textColor),
            PropertyFactory.textHaloColor(haloColor),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
        )
    )
    style.addLayer(
        CircleLayer("juzzics-people-dots", PEOPLE_SOURCE).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor(haloColor),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleOpacity(Expression.get("opacity")),
        )
    )
    style.addLayer(
        SymbolLayer("juzzics-people-names", PEOPLE_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textColor(textColor),
            PropertyFactory.textHaloColor(haloColor),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
        )
    )
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun peopleFeatures(people: List<MapPerson>): FeatureCollection =
    FeatureCollection.fromFeatures(
        // you last, so your dot is on top
        people.sortedBy { it.isMe }.map { person ->
            Feature.fromGeometry(Point.fromLngLat(person.lon, person.lat)).apply {
                addStringProperty("name", person.name)
                addStringProperty("color", person.color.hex())
                addNumberProperty("opacity", if (person.stale) 0.45 else 1.0)
            }
        }
    )

private fun pinFeatures(pins: List<MapPin>): FeatureCollection =
    FeatureCollection.fromFeatures(
        pins.map { pin ->
            Feature.fromGeometry(Point.fromLngLat(pin.lon, pin.lat)).apply { addStringProperty("label", pin.label) }
        }
    )

private fun trailFeatures(trails: List<MapTrail>): FeatureCollection =
    FeatureCollection.fromFeatures(
        trails.filter { it.points.size >= 2 }.map { trail ->
            Feature.fromGeometry(LineString.fromLngLats(trail.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                addStringProperty("color", trail.color.hex())
            }
        }
    )

/** the plain trail view's camera: fit everyone, follow you, or wherever you panned */
@Stable
internal class TrailView {
    enum class Mode { FIT, ME, FREE }

    var mode by mutableStateOf(Mode.FIT)
    /** screen position of the anchor point (FREE) */
    var origin by mutableStateOf(Offset.Zero)
    /** pixels per meter (FREE and ME) */
    var scale by mutableFloatStateOf(0f)

    // what the last frame used (gestures continue from there); not state: drawing sets them
    internal var drawnOrigin = Offset.Zero
    internal var drawnScale = 1f
    internal var drawnWidth = 1f
    internal var drawnAnchor: GeoPoint? = null

    /** where a place is on screen, as the last frame drew it */
    fun project(lat: Double, lon: Double): Offset? {
        val a = drawnAnchor ?: return null
        val x = ((lon - a.lon) * 111_320.0 * cos(Math.toRadians(a.lat))).toFloat()
        val y = ((lat - a.lat) * 110_540.0).toFloat()
        return Offset(drawnOrigin.x + x * drawnScale, drawnOrigin.y - y * drawnScale)
    }

    fun fitAll() {
        mode = Mode.FIT
    }

    fun followMe() {
        // at least ~400 m across the screen
        scale = maxOf(drawnScale, drawnWidth / 400f)
        mode = Mode.ME
    }
}

/**
 * North-up trails and dots on a grid, drawn from GPS alone (no map data needed).
 * Pinch to zoom, drag to move.
 */
@Composable
private fun TrailMap(
    people: List<MapPerson>,
    trails: List<MapTrail>,
    pins: List<MapPin>,
    view: TrailView,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nameColor = MaterialTheme.colorScheme.onSurface
    val haloColor = MaterialTheme.colorScheme.surface

    // everything is measured in meters from this point (the first one we saw)
    val candidate = people.firstOrNull { it.isMe }?.let { GeoPoint(it.lat, it.lon) }
        ?: people.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
        ?: trails.firstNotNullOfOrNull { it.points.firstOrNull() }
    var anchor by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(candidate == null) { if (anchor == null) anchor = candidate }

    Canvas(
        modifier
            .pointerInput(view) {
                // screen -> meters -> latitude / longitude, with what the last frame drew
                detectTapGestures(onLongPress = { at ->
                    val a = view.drawnAnchor ?: return@detectTapGestures
                    val metersX = (at.x - view.drawnOrigin.x) / view.drawnScale
                    val metersY = (view.drawnOrigin.y - at.y) / view.drawnScale
                    val lat = a.lat + metersY / 110_540.0
                    val lon = a.lon + metersX / (111_320.0 * cos(Math.toRadians(a.lat)))
                    currentOnLongPress(lat, lon)
                })
            }
            .pointerInput(view) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val from = view.drawnScale
                    val to = (from * zoom).coerceIn(0.0005f, 40f)
                    val factor = to / from
                    view.origin = centroid + (view.drawnOrigin - centroid) * factor + pan
                    view.scale = to
                    view.drawnOrigin = view.origin
                    view.drawnScale = to
                    view.mode = TrailView.Mode.FREE
                }
            }
    ) {
        val a = anchor ?: return@Canvas
        val cosLat = cos(Math.toRadians(a.lat)).toFloat()
        fun meters(lat: Double, lon: Double) = Offset(
            ((lon - a.lon) * 111_320.0 * cosLat).toFloat(),
            ((lat - a.lat) * 110_540.0).toFloat()
        )

        val trailMeters = trails.map { trail -> trail to trail.points.map { meters(it.lat, it.lon) } }
        val peopleMeters = people.map { it to meters(it.lat, it.lon) }

        // the camera for this frame
        val (origin, scale) = when (view.mode) {
            TrailView.Mode.FREE -> view.origin to view.scale
            TrailView.Mode.ME -> {
                val me = peopleMeters.firstOrNull { it.first.isMe }?.second ?: Offset.Zero
                Offset(center.x - me.x * view.scale, center.y + me.y * view.scale) to view.scale
            }
            TrailView.Mode.FIT -> {
                val all = peopleMeters.map { it.second } + trailMeters.flatMap { it.second } +
                        pins.map { meters(it.lat, it.lon) }
                val minX = all.minOfOrNull { it.x } ?: 0f
                val maxX = all.maxOfOrNull { it.x } ?: 0f
                val minY = all.minOfOrNull { it.y } ?: 0f
                val maxY = all.maxOfOrNull { it.y } ?: 0f
                // at least 100 m across, a margin around everyone
                val spanX = maxOf(maxX - minX, 100f)
                val spanY = maxOf(maxY - minY, 100f)
                val s = minOf(size.width / spanX, size.height / spanY) * 0.75f
                val midX = (minX + maxX) / 2f
                val midY = (minY + maxY) / 2f
                Offset(center.x - midX * s, center.y + midY * s) to s
            }
        }
        view.drawnOrigin = origin
        view.drawnScale = scale
        view.drawnWidth = size.width
        view.drawnAnchor = a
        fun screen(m: Offset) = Offset(origin.x + m.x * scale, origin.y - m.y * scale)

        // grid, one line per scale-bar step
        val step = niceStep(size.width / 4f / scale)
        val stepPx = step * scale
        if (stepPx > 8f) {
            val firstX = floor(((0f - origin.x) / scale) / step) * step
            var x = firstX
            var lines = 0
            while (lines++ < 80) {
                val sx = origin.x + x * scale
                if (sx > size.width) break
                drawLine(gridColor, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1f)
                x += step
            }
            val firstY = floor(((origin.y - size.height) / scale) / step) * step
            var y = firstY
            lines = 0
            while (lines++ < 80) {
                val sy = origin.y - y * scale
                if (sy < 0f) break
                drawLine(gridColor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1f)
                y += step
            }
        }

        // trails
        trailMeters.forEach { (trail, points) ->
            if (points.size < 2) return@forEach
            val path = Path().apply {
                val first = screen(points.first())
                moveTo(first.x, first.y)
                points.drop(1).forEach { val p = screen(it); lineTo(p.x, p.y) }
            }
            drawPath(
                path,
                trail.color.copy(alpha = 0.8f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // meeting points: gold rings with a label above
        pins.forEach { pin ->
            val pos = screen(meters(pin.lat, pin.lon))
            drawCircle(nameColor, radius = 13.dp.toPx(), center = pos)
            drawCircle(PinColor, radius = 11.dp.toPx(), center = pos)
            val label = textMeasurer.measure(pin.label, TextStyle(color = nameColor, fontSize = 12.sp), maxLines = 1)
            drawText(label, topLeft = Offset(pos.x - label.size.width / 2f, pos.y - 16.dp.toPx() - label.size.height))
        }

        // people (you last, on top)
        peopleMeters.sortedBy { it.first.isMe }.forEach { (person, m) ->
            val pos = screen(m)
            val alpha = if (person.stale) 0.45f else 1f
            drawCircle(haloColor, radius = 11.dp.toPx(), center = pos)
            drawCircle(person.color.copy(alpha = alpha), radius = 8.dp.toPx(), center = pos)
            val label = textMeasurer.measure(
                person.name,
                TextStyle(color = nameColor.copy(alpha = alpha), fontSize = 12.sp),
                maxLines = 1
            )
            drawText(label, topLeft = Offset(pos.x - label.size.width / 2f, pos.y + 12.dp.toPx()))
        }

        // "N ↑" and the scale bar
        val north = textMeasurer.measure("N ↑", TextStyle(color = labelColor, fontSize = 12.sp))
        drawText(north, topLeft = Offset(12.dp.toPx(), 12.dp.toPx() + 44.dp.toPx()))
        val barStart = Offset(16.dp.toPx(), size.height - 20.dp.toPx())
        val barEnd = barStart + Offset(stepPx, 0f)
        drawLine(labelColor, barStart, barEnd, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val barLabel = textMeasurer.measure(scaleLabel(step), TextStyle(color = labelColor, fontSize = 11.sp))
        drawText(barLabel, topLeft = Offset(barStart.x, barStart.y - barLabel.size.height - 4.dp.toPx()))
    }
}

/** 1, 2 or 5 times a power of ten, close to [meters] */
private fun niceStep(meters: Float): Float {
    if (meters <= 0f || meters.isNaN() || meters.isInfinite()) return 100f
    val power = 10.0.pow(floor(log10(meters.toDouble()))).toFloat()
    val unit = meters / power
    val nice = when {
        unit < 1.5f -> 1f
        unit < 3.5f -> 2f
        unit < 7.5f -> 5f
        else -> 10f
    }
    return nice * power
}

private fun scaleLabel(meters: Float): String = when {
    meters >= 1_000f -> {
        val km = meters / 1_000f
        if (abs(km - km.roundToInt()) < 0.01f) "${km.roundToInt()} km" else "%.1f km".format(km)
    }
    else -> "${meters.roundToInt()} m"
}
