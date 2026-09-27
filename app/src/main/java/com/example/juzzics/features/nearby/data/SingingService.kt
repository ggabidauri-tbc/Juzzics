package com.example.juzzics.features.nearby.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.juzzics.MainActivity
import com.example.juzzics.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Runs while this phone sings through a friend's phone. Android only lets an app use the
 * microphone with the screen off / locked while it shows it's doing so (this notification,
 * with a Stop button).
 */
class SingingService : Service(), KoinComponent {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            get<NearbyManager>().stopSinging()
            stopSelf()
            return START_NOT_STICKY
        }
        val to = intent?.getStringExtra(EXTRA_TO) ?: "a friend"
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SingingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("Singing on $to's phone")
            .setContentText("Your microphone is live")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Singing", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "singing"
        private const val NOTIFICATION_ID = 4242
        private const val EXTRA_TO = "to"
        private const val ACTION_STOP = "com.example.juzzics.STOP_SINGING"

        /** call while the app is on screen (Android doesn't allow starting it from the background) */
        fun start(context: Context, friendName: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SingingService::class.java).putExtra(EXTRA_TO, friendName)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SingingService::class.java))
        }
    }
}
