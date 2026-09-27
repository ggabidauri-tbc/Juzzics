package com.example.juzzics.features.nearby.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.juzzics.MainActivity
import com.example.juzzics.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * "Open the friend radar" requests from outside the Nearby screen (a notification, a message's
 * "Show" button): the app goes to Nearby and opens the radar.
 */
object OpenRadarRequests {
    private val _count = MutableStateFlow(0)

    /** increases with every request */
    val count: StateFlow<Int> = _count

    fun request() = _count.update { it + 1 }

    /** last request the app's navigation / the Nearby screen handled */
    var handledByNavigation = 0
    var handledByNearby = 0

    /** in a notification's intent: open the radar */
    const val EXTRA_OPEN_RADAR = "open_radar"
}

/** Buzzes, beeps and notifications for the radar and the walkie-talkie. */
class RadarAlerts(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    /** "Nika asks you to come to them": buzz, and a notification (the phone may be in a pocket) */
    @SuppressLint("MissingPermission") // checked in canNotify()
    fun comeToMe(from: String, distance: String?) {
        vibrate(longArrayOf(0, 300, 150, 300, 150, 600))
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            7,
            Intent(context, MainActivity::class.java)
                .putExtra(OpenRadarRequests.EXTRA_OPEN_RADAR, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("$from asks you to come to them")
            .setContentText(distance?.let { "$it away · tap to see where" } ?: "Tap to see where they are")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** a meeting point was set: a short buzz */
    fun pin() = vibrate(longArrayOf(0, 120, 80, 120))

    /** walkie-talkie: a friend starts / stops talking */
    fun talkStart() = beep(ToneGenerator.TONE_PROP_BEEP, 90)

    fun talkEnd() = beep(ToneGenerator.TONE_PROP_ACK, 120)

    private fun beep(tone: Int, ms: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            generator.startTone(tone, ms)
            handler.postDelayed({ runCatching { generator.release() } }, ms + 200L)
        }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Friends nearby", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a friend asks you to come to them"
                }
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "friends_nearby"
        const val NOTIFICATION_ID = 4343
    }
}
