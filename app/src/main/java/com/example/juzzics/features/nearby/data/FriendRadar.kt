package com.example.juzzics.features.nearby.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.juzzics.features.nearby.domain.ComeToMe
import com.example.juzzics.features.nearby.domain.GeoFix
import com.example.juzzics.features.nearby.domain.GeoPoint
import com.example.juzzics.features.nearby.domain.MeetingPin
import com.example.juzzics.features.nearby.domain.RadarPerson
import com.example.juzzics.features.nearby.domain.RadarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** something on the radar the user should notice (a buzz, a notification) */
sealed interface RadarEvent {
    data class PinSet(val name: String) : RadarEvent
    data class CalledOver(val name: String, val distanceM: Float?) : RadarEvent
}

/**
 * Friend radar: connected phones tell each other where they are (GPS works without internet),
 * so everyone sees who's where: distance, and direction relative to the way you face (compass).
 * Sharing is off until turned on, and only goes to connected friends (and on through their phones,
 * see [Mesh]). Also meeting points ("meet here") and "come to me".
 *
 * People are kept by their phone's lasting id; someone out of range stays at their last known
 * position (getting older) until they're back.
 */
class FriendRadar(
    private val context: Context,
    /** to every connected friend (and on through their phones) */
    private val broadcast: (NearbyMessage) -> Unit,
    private val sendTo: (endpointId: String, NearbyMessage) -> Unit,
    /** a connected friend's name */
    private val nameOf: (endpointId: String) -> String,
    private val onState: (RadarState) -> Unit,
    private val onEvent: (RadarEvent) -> Unit,
) {
    private var state = RadarState()
        set(value) {
            field = value
            onState(value)
        }

    private val _heading = MutableStateFlow<Float?>(null)
    /** where the phone points, degrees from true north (0..360); null without a compass / screen closed */
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** the radar screen is open (compass on, location on even when not sharing) */
    private var visible = false
    private var listening = false
    /** screen off (phone in the pocket): a position every 10 s instead of every 2 s saves battery */
    private var screenOff = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val off = intent.action == Intent.ACTION_SCREEN_OFF
            if (off != screenOff) {
                screenOff = off
                if (listening) restartLocationUpdates()
            }
        }
    }
    private var compassOn = false
    private var lastSentAt = 0L

    /** [untilMs]: when it stops by itself (System.currentTimeMillis), null = until turned off */
    fun setSharing(on: Boolean, untilMs: Long? = null) {
        if (on == state.sharing) {
            if (on) state = state.copy(sharingUntilMs = untilMs)
            return
        }
        state = state.copy(sharing = on, sharingUntilMs = if (on) untilMs else null)
        if (on) state.me?.let { sendFix(it, force = true) } else broadcast(NearbyMessage(NearbyMessage.LOCATION_OFF))
        updateUpdates()
    }

    fun setVisible(on: Boolean) {
        visible = on
        updateUpdates()
    }

    fun onFriendConnected(endpointId: String) {
        val me = state.me
        if (state.sharing && me != null) sendTo(endpointId, fixMessage(me))
        state.pins[RadarState.ME]?.let { sendTo(endpointId, NearbyMessage(NearbyMessage.PIN, lat = it.lat, lon = it.lon)) }
    }

    /** true if it was a radar message */
    fun handle(endpointId: String, message: NearbyMessage): Boolean {
        // relayed: the person who said it, not the friend who passed it on
        val personId = message.origin ?: endpointId
        val name = message.originName ?: nameOf(endpointId)
        val relayed = (message.hops ?: 0) > 0
        when (message.type) {
            NearbyMessage.LOCATION -> {
                val lat = message.lat ?: return true
                val lon = message.lon ?: return true
                val fix = GeoFix(lat, lon, message.accuracy ?: 50f, SystemClock.elapsedRealtime())
                state = state.copy(
                    people = state.people + (personId to RadarPerson(name, fix, relayed)),
                    trails = withTrailPoint(personId, fix),
                )
            }
            NearbyMessage.LOCATION_OFF -> if (personId in state.people) state = state.copy(people = state.people - personId)
            NearbyMessage.PIN -> {
                val lat = message.lat ?: return true
                val lon = message.lon ?: return true
                val isNew = state.pins[personId]?.let { it.lat != lat || it.lon != lon } ?: true
                state = state.copy(
                    pins = state.pins + (personId to MeetingPin(name, lat, lon, mine = false)),
                    // a new spot shows again even if the old one was hidden
                    hiddenPins = if (isNew) state.hiddenPins - personId else state.hiddenPins,
                )
                if (isNew) onEvent(RadarEvent.PinSet(name))
            }
            NearbyMessage.PIN_CLEAR -> if (personId in state.pins) {
                state = state.copy(pins = state.pins - personId, hiddenPins = state.hiddenPins - personId)
            }
            NearbyMessage.COME_TO_ME -> {
                val lat = message.lat ?: return true
                val lon = message.lon ?: return true
                val now = SystemClock.elapsedRealtime()
                // (not added to the people sharing: they may not share their location)
                state = state.copy(comeToMe = ComeToMe(name, personId, lat, lon, now))
                val distance = state.me?.let { me ->
                    FloatArray(1).also { Location.distanceBetween(me.lat, me.lon, lat, lon, it) }[0]
                }
                onEvent(RadarEvent.CalledOver(name, distance))
            }
            else -> return false
        }
        return true
    }

    /**
     * someone left on purpose: off the radar (their trail stays until cleared, and their meeting
     * point too: they may still be linked through another friend)
     */
    fun forget(personId: String) {
        state = state.copy(
            people = state.people - personId,
            comeToMe = state.comeToMe?.takeIf { it.personId != personId },
        )
    }

    /** "meet here": everyone sees it with an arrow and a distance */
    fun setPin(lat: Double, lon: Double) {
        state = state.copy(pins = state.pins + (RadarState.ME to MeetingPin("You", lat, lon, mine = true)))
        broadcast(NearbyMessage(NearbyMessage.PIN, lat = lat, lon = lon))
    }

    /** takes this phone's meeting point away for everyone */
    fun clearPin() {
        if (RadarState.ME !in state.pins) return
        state = state.copy(pins = state.pins - RadarState.ME)
        broadcast(NearbyMessage(NearbyMessage.PIN_CLEAR))
    }

    /** hides someone else's meeting point on this phone (kept, see [showHiddenPins]) */
    fun hidePin(personId: String) {
        state = state.copy(hiddenPins = state.hiddenPins + personId)
    }

    fun showHiddenPins() {
        state = state.copy(hiddenPins = emptySet())
    }

    /** "come to me": friends' phones buzz and point here. false: this phone's position isn't known yet */
    fun callOver(): Boolean {
        val me = state.me ?: return false
        broadcast(NearbyMessage(NearbyMessage.COME_TO_ME, lat = me.lat, lon = me.lon, accuracy = me.accuracyM))
        return true
    }

    fun dismissComeToMe() {
        state = state.copy(comeToMe = null)
    }

    // ---------------------- this phone's position ----------------------

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** GPS while sharing or looking at the radar; compass only while looking */
    @SuppressLint("MissingPermission") // checked in hasPermission()
    private fun updateUpdates() {
        val wantLocation = state.sharing || visible
        if (wantLocation && !listening && hasPermission()) {
            val gpsOn = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            state = state.copy(locationOff = !gpsOn)
            runCatching {
                // where it was last, until a fresh fix comes
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::onLocation)
                requestLocationUpdates()
                listening = true
                ContextCompat.registerReceiver(
                    context,
                    screenReceiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_SCREEN_ON)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        } else if (!wantLocation && listening) {
            runCatching { locationManager.removeUpdates(locationListener) }
            runCatching { context.unregisterReceiver(screenReceiver) }
            listening = false
        }

        if (visible && !compassOn) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sensor != null) {
                sensorManager.registerListener(compassListener, sensor, SensorManager.SENSOR_DELAY_UI)
                compassOn = true
            }
        } else if (!visible && compassOn) {
            sensorManager.unregisterListener(compassListener)
            compassOn = false
            _heading.value = null
        }
    }

    @SuppressLint("MissingPermission") // only called while allowed (see updateUpdates)
    private fun requestLocationUpdates() {
        val every = if (screenOff) POCKET_UPDATE_MS else UPDATE_MS
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, every, 0f, locationListener, Looper.getMainLooper())
        // with a connection, the network gives a (rougher) first position faster
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, every, 0f, locationListener, Looper.getMainLooper())
        }
    }

    private fun restartLocationUpdates() {
        runCatching {
            locationManager.removeUpdates(locationListener)
            requestLocationUpdates()
        }
    }

    private fun onLocation(location: Location) {
        val current = state.me
        val fix = GeoFix(location.latitude, location.longitude, location.accuracy, SystemClock.elapsedRealtime())
        // a rough network position doesn't replace a fresh, better GPS one
        val keep = current != null && fix.accuracyM > current.accuracyM * 2 &&
                fix.atElapsedMs - current.atElapsedMs < 20_000
        if (keep) return
        state = state.copy(me = fix, locationOff = false, trails = withTrailPoint(RadarState.ME, fix))
        if (state.sharing) sendFix(fix, force = false)
    }

    /** forgets everyone's trails */
    fun clearTrails() {
        state = state.copy(trails = emptyMap())
    }

    /**
     * [who]'s trail with [fix] added: only exact enough positions, and only after moving a few
     * meters (standing still doesn't pile up points); the oldest go beyond [MAX_TRAIL_POINTS]
     */
    private fun withTrailPoint(who: String, fix: GeoFix): Map<String, List<GeoPoint>> {
        if (fix.accuracyM > TRAIL_MAX_ACCURACY_M) return state.trails
        val trail = state.trails[who].orEmpty()
        val last = trail.lastOrNull()
        if (last != null) {
            val moved = FloatArray(1)
            Location.distanceBetween(last.lat, last.lon, fix.lat, fix.lon, moved)
            if (moved[0] < TRAIL_STEP_M) return state.trails
        }
        return state.trails + (who to (trail + GeoPoint(fix.lat, fix.lon)).takeLast(MAX_TRAIL_POINTS))
    }

    private fun sendFix(fix: GeoFix, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSentAt < SEND_EVERY_MS) return
        lastSentAt = now
        broadcast(fixMessage(fix))
    }

    private fun fixMessage(fix: GeoFix) =
        NearbyMessage(NearbyMessage.LOCATION, lat = fix.lat, lon = fix.lon, accuracy = fix.accuracyM)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) state = state.copy(locationOff = false)
        }
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) state = state.copy(locationOff = true)
        }
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    // ---------------------- compass ----------------------

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    private val compassListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val unreliable = event.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            if (unreliable != state.compassUnreliable) state = state.copy(compassUnreliable = unreliable)
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            SensorManager.getOrientation(rotation, orientation)
            var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            // magnetic north -> true north (GPS bearings are to true north)
            state.me?.let { me ->
                degrees += GeomagneticField(me.lat.toFloat(), me.lon.toFloat(), 0f, System.currentTimeMillis()).declination
            }
            val target = (degrees + 360f) % 360f
            val previous = _heading.value
            // smooth the jitter, the short way round (359° -> 1° is 2°, not 358°)
            _heading.value = if (previous == null) target else {
                val diff = ((target - previous + 540f) % 360f) - 180f
                (previous + diff * 0.2f + 360f) % 360f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            val unreliable = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            if (unreliable != state.compassUnreliable) state = state.copy(compassUnreliable = unreliable)
        }
    }

    private companion object {
        const val UPDATE_MS = 2_000L
        const val POCKET_UPDATE_MS = 10_000L
        /** a trail gets a new point after moving this far */
        const val TRAIL_STEP_M = 5f
        /** rougher positions don't go on trails (they'd zigzag) */
        const val TRAIL_MAX_ACCURACY_M = 30f
        const val MAX_TRAIL_POINTS = 3_000
        /** friends get this phone's position at most this often */
        const val SEND_EVERY_MS = 3_000L
    }
}
