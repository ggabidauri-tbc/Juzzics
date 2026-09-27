package com.example.juzzics.features.nearby.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Shout-outs: hold to record a short voice message, it plays on friends' phones over the
 * music (which gets quieter meanwhile, then comes back).
 */
class ShoutOuts(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** false if the microphone couldn't be used */
    fun startRecording(): Boolean {
        cancelRecording()
        val target = File(context.cacheDir, "shout_${System.currentTimeMillis()}.m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setMaxDuration(MAX_MS)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            file = target
            startedAt = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            target.delete()
            false
        }
    }

    /** the recording, or null if it was too short (a tap, not a hold) or failed */
    fun stopRecording(): File? {
        val active = recorder ?: return null
        val recorded = file
        val length = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        val ok = runCatching { active.stop() }.isSuccess
        active.release()
        if (!ok || length < MIN_MS || recorded == null) {
            recorded?.delete()
            return null
        }
        return recorded
    }

    fun cancelRecording() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.delete()
        file = null
    }

    /** plays a friend's shout-out over the music (asks the music to get quieter meanwhile) */
    fun play(voice: File, onDone: () -> Unit) {
        val focus = requestDuckingFocus()
        val mediaPlayer = MediaPlayer()
        fun finish() {
            runCatching { mediaPlayer.release() }
            abandonFocus(focus)
            voice.delete()
            onDone()
        }
        runCatching {
            mediaPlayer.setAudioAttributes(attributes)
            mediaPlayer.setDataSource(voice.absolutePath)
            mediaPlayer.setOnCompletionListener { finish() }
            mediaPlayer.setOnErrorListener { _, _, _ -> finish(); true }
            mediaPlayer.prepare()
            mediaPlayer.start()
        }.onFailure { finish() }
    }

    private fun requestDuckingFocus(): Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
                .also { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            null
        }

    private fun abandonFocus(focus: Any?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus is AudioFocusRequest) {
            audioManager.abandonAudioFocusRequest(focus)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private companion object {
        const val MIN_MS = 600L
        const val MAX_MS = 20_000
    }
}
