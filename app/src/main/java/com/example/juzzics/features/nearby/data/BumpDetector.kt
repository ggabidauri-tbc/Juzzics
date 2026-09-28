package com.example.juzzics.features.nearby.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Feels a bump: two phones tapped together give a short, sharp jolt, much sharper than walking
 * or turning the phone in the hand. [onBump] gets the moment (elapsedRealtime) it happened.
 */
class BumpDetector(context: Context, private val onBump: (atMs: Long) -> Unit) {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** gravity, followed slowly; what's left is the phone's own movement */
    private val gravity = FloatArray(3)
    private var settled = 0
    private var lastBump = 0L

    val available: Boolean get() = accelerometer != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // start from where "down" is now
            if (settled == 0) event.values.copyInto(gravity, endIndex = 3)
            var sum = 0f
            for (i in 0..2) {
                // (slow: a quick jolt barely moves it, so the jolt stays whole)
                gravity[i] = gravity[i] * 0.97f + event.values[i] * 0.03f
                val moving = event.values[i] - gravity[i]
                sum += moving * moving
            }
            // the first moments only learn where "down" is
            if (settled < 60) {
                settled++
                return
            }
            val jolt = sqrt(sum)
            val now = SystemClock.elapsedRealtime()
            if (jolt > BUMP_JOLT && now - lastBump > BUMP_GAP_MS) {
                lastBump = now
                onBump(now)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        settled = 0
        // fast: a tap lasts only a few milliseconds, slower sampling misses its peak. As fast as
        // the phone allows (with HIGH_SAMPLING_RATE_SENSORS); if that's refused, 200 per second
        // (the most Android allows without it)
        val sensor = accelerometer ?: return
        val fastest = runCatching { sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST) }
        if (fastest.getOrDefault(false) != true) {
            runCatching { sensors.registerListener(listener, sensor, 5_000) }
        }
    }

    fun stop() {
        sensors.unregisterListener(listener)
    }

    private companion object {
        /** m/s² beyond gravity: a tap of two phones, not a step or a swing */
        const val BUMP_JOLT = 12f
        /** one bump, not the few jolts right after it */
        const val BUMP_GAP_MS = 800L
    }
}
