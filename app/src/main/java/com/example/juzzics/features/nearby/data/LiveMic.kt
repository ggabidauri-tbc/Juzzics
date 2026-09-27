package com.example.juzzics.features.nearby.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Sing-along mic: this phone's microphone streams live to a friend's phone, which plays the
 * voice on top of its music (both play at once, the music isn't paused or lowered).
 *
 * Raw audio (16-bit mono) through a live Nearby stream, like Google's walkie-talkie sample.
 * Kept as close to real time as possible:
 * - the karaoke microphone mode (Android 10+), tiny 5 ms pieces, a small playback buffer
 * - Wi-Fi kept awake on both phones (its power saving adds up to ~100 ms)
 * - whatever piles up on the way (a hiccup in the connection) is dropped, not played late
 * And cleaned up on the singer's phone: the system's noise suppression where there is one,
 * a low cut (wind, handling rumble) and a noise gate (silence between phrases stays silent).
 */
class LiveMic(context: Context) {

    private val wifiLock: WifiManager.WifiLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifi.createWifiLock(mode, "juzzics:sing").apply { setReferenceCounted(false) }
    }.getOrNull()

    private val wakeLock: PowerManager.WakeLock? = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "juzzics:sing").apply { setReferenceCounted(false) }
    }.getOrNull()

    /** singing or listening: keep the CPU and Wi-Fi fully awake (screen off too) */
    private fun keepAwake(on: Boolean) {
        runCatching {
            if (on) {
                wakeLock?.acquire(MAX_SESSION_MS)
                wifiLock?.acquire()
            } else if (!recording && !listening) {
                wakeLock?.release()
                wifiLock?.release()
            }
        }
    }

    // ---------------------- this phone sings ----------------------

    @Volatile private var recording = false
    /** each start gets a new number: an older mic thread stops even if a new one started right away */
    @Volatile private var recordSession = 0

    /**
     * starts the microphone; returns the stream to send (the other side of a pipe the
     * microphone writes into), or null if the microphone couldn't be used
     */
    @SuppressLint("MissingPermission") // the screen asks for the microphone before this
    fun startSinging(): ParcelFileDescriptor? {
        stopSinging()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null
        // made for karaoke: low delay, no call-style processing
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaRecorder.AudioSource.VOICE_PERFORMANCE
        else MediaRecorder.AudioSource.MIC
        val recorder = runCatching {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer)
        }.getOrNull() ?: return null
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        val suppressor = if (NoiseSuppressor.isAvailable()) {
            runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
        } else null

        val (readSide, writeSide) = ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
        val session = ++recordSession
        recording = true
        keepAwake(true)
        thread(name = "juzzics-mic", priority = Thread.MAX_PRIORITY) {
            val cleaner = VoiceCleaner()
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                runCatching {
                    recorder.startRecording()
                    val samples = ShortArray(FRAME_SAMPLES)
                    val bytes = ByteArray(FRAME_SAMPLES * 2)
                    while (recordSession == session) {
                        val read = recorder.read(samples, 0, samples.size)
                        if (read < 0) break
                        if (read == 0) continue
                        cleaner.clean(samples, read)
                        for (i in 0 until read) {
                            bytes[i * 2] = samples[i].toInt().toByte()
                            bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
                        }
                        out.write(bytes, 0, read * 2)
                    }
                }
            }
            runCatching { recorder.stop() }
            suppressor?.release()
            recorder.release()
            if (recordSession == session) recording = false
            keepAwake(false)
        }
        return readSide
    }

    /** closing the stream tells the other phone the song is over */
    fun stopSinging() {
        recordSession++
        recording = false
    }

    /**
     * Low cut (~120 Hz: wind, handling rumble, hum) and a noise gate: quiet stretches between
     * phrases are muted, so background hiss and noise don't play. Opens instantly when you
     * sing, closes gently a moment after you stop (not to cut word endings).
     */
    private class VoiceCleaner {
        private var lastIn = 0f
        private var lastOut = 0f
        /** estimated background noise level, follows quiet moments */
        private var noiseFloor = 300f
        private var gain = 0f
        private var holdSamples = 0

        fun clean(samples: ShortArray, length: Int) {
            var sum = 0.0
            for (i in 0 until length) {
                val x = samples[i].toFloat()
                val y = LOW_CUT * (lastOut + x - lastIn)
                lastIn = x
                lastOut = y
                samples[i] = y.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                sum += y * y
            }
            val level = sqrt(sum / length).toFloat()
            // the floor drops fast to quiet levels and rises slowly (singing doesn't raise it)
            noiseFloor = if (level < noiseFloor) level * 0.5f + noiseFloor * 0.5f else noiseFloor + (level - noiseFloor) * 0.002f
            val open = level > maxOf(noiseFloor * GATE_RATIO, GATE_MIN)
            if (open) holdSamples = HOLD_SAMPLES else holdSamples -= length
            val target = if (holdSamples > 0) 1f else 0f
            for (i in 0 until length) {
                gain += if (target > gain) ATTACK else -RELEASE
                gain = gain.coerceIn(0f, 1f)
                samples[i] = (samples[i] * gain).toInt().toShort()
            }
        }

        private companion object {
            /** one-pole high-pass at ~120 Hz for 24 kHz */
            const val LOW_CUT = 0.9695f
            /** open when this much louder than the background */
            const val GATE_RATIO = 2.5f
            /** ...and at least this loud (16-bit scale) */
            const val GATE_MIN = 250f
            /** stays open this long after the voice drops (word endings, breaths) */
            const val HOLD_SAMPLES = SAMPLE_RATE / 5
            /** per sample: opens in ~2 ms, closes in ~60 ms */
            const val ATTACK = 1f / 48
            const val RELEASE = 1f / 1440
        }
    }

    // ---------------------- a friend sings on this phone ----------------------

    @Volatile private var listening = false
    @Volatile private var input: InputStream? = null
    /** each voice played gets a new number: a newer one replaces it without being cut by it */
    @Volatile private var listenSession = 0
    /** sessions ended because a newer voice took over (their end isn't reported) */
    private val replaced = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    /** how loud the friend's voice plays: phone mics are quiet, so up to 4× */
    @Volatile var gain = 2f

    /** plays the voice arriving on [voice] until it ends (or [stopListening]); then [onEnd] */
    /**
     * [forward]: the voice also goes on here, as it arrives (walkie-talkie relayed to friends
     * further away); closed when the voice ends
     */
    fun listen(voice: InputStream, sampleRate: Int, forward: OutputStream? = null, onEnd: () -> Unit) {
        if (listening) replaced += listenSession
        stopListening()
        val session = ++listenSession
        listening = true
        input = voice
        keepAwake(true)
        thread(name = "juzzics-voice", priority = Thread.MAX_PRIORITY) {
            val track = createTrack(sampleRate)
            var relay = forward
            try {
                track.play()
                val frameBytes = sampleRate * 2 * FRAME_MS / 1000
                val buffer = ByteArray(frameBytes)
                val maxBacklog = sampleRate * 2 * MAX_BACKLOG_MS / 1000
                val keepBacklog = sampleRate * 2 * KEEP_BACKLOG_MS / 1000
                while (listenSession == session) {
                    // fallen behind (a hiccup): skip to near "now" instead of staying late
                    val waiting = runCatching { voice.available() }.getOrDefault(0)
                    if (waiting > maxBacklog) voice.skip((waiting - keepBacklog).toLong() and 1L.inv())
                    val read = voice.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        // passed on untouched (before the volume boost); a broken relay just stops
                        relay?.let { out -> if (runCatching { out.write(buffer, 0, read) }.isFailure) relay = null }
                        amplify(buffer, read, gain)
                        track.write(buffer, 0, read)
                    }
                }
            } catch (_: Exception) {
                // stream closed / connection lost: the song is over
            } finally {
                runCatching { track.stop() }
                track.release()
                runCatching { voice.close() }
                // the relayed voice ends too
                runCatching { forward?.close() }
                if (listenSession == session) listening = false
                keepAwake(false)
                if (!replaced.remove(session)) onEnd()
            }
        }
    }

    fun stopListening() {
        listenSession++
        listening = false
        runCatching { input?.close() }
        input = null
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            // the smallest buffer the phone allows: the least delay
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        return builder.build()
    }

    /** 16-bit little-endian samples × [gain], clipped instead of wrapping around */
    private fun amplify(buffer: ByteArray, length: Int, gain: Float) {
        if (gain == 1f) return
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val louder = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = louder.toByte()
            buffer[i + 1] = (louder shr 8).toByte()
            i += 2
        }
    }

    companion object {
        /** voice quality is fine at this rate, and it's light enough for Bluetooth */
        const val SAMPLE_RATE = 24_000
        /** 5 ms of audio per piece: small pieces = low delay */
        private const val FRAME_MS = 5
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        /** voice waiting longer than this is skipped, down to [KEEP_BACKLOG_MS] */
        private const val MAX_BACKLOG_MS = 80
        private const val KEEP_BACKLOG_MS = 20
        /** safety: the wake lock never outlives a very long session */
        private const val MAX_SESSION_MS = 3 * 60 * 60 * 1000L
    }
}
