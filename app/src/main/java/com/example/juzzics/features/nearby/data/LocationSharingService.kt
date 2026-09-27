package com.example.juzzics.features.nearby.data

import android.annotation.SuppressLint
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
import com.example.juzzics.features.nearby.domain.NearbyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.text.DateFormat
import java.util.Date

/**
 * Keeps location sharing (friend radar) going with the screen off or another app open.
 * Started only when the user turns sharing on in the app (so the normal "while using the app"
 * location permission is enough), always visible as a notification with a Stop button.
 */
class LocationSharingService : Service(), KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = get<NearbyManager>()
        if (intent?.action == ACTION_STOP) {
            manager.setLocationSharing(false)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(manager.state.value), type)
        }.isSuccess
        if (!started) {
            // not allowed right now (e.g. location permission taken away): sharing can't go on in the background
            stopSelf()
            return START_NOT_STICKY
        }
        if (watching == null) {
            // keeps the notification up to date; stops with sharing
            watching = scope.launch {
                manager.state
                    .map { Triple(it.radar.sharing, it.friends.map { f -> f.name }, it.radar.sharingUntilMs) }
                    .distinctUntilChanged()
                    .collect { (sharing, _, _) ->
                        if (!sharing) {
                            ServiceCompat.stopForeground(this@LocationSharingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            update(manager.state.value)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(state: NearbyState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_music_note)
        .setContentTitle("Sharing your location")
        .setContentText(
            buildString {
                val names = state.friends.map { it.name }
                append(if (names.isEmpty()) "No friends connected right now" else "With ${names.joinToString()}")
                state.radar.sharingUntilMs?.let {
                    append(" · until ")
                    append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                }
            }
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .addAction(
            0, "Stop",
            PendingIntent.getService(
                this, 2, Intent(this, LocationSharingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    @SuppressLint("MissingPermission") // without notification permission the update is just not shown
    private fun update(state: NearbyState) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 4343
        private const val ACTION_STOP = "com.example.juzzics.STOP_SHARING_LOCATION"

        /** only while the app is on screen (the user just turned sharing on) */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LocationSharingService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationSharingService::class.java))
        }
    }
}
