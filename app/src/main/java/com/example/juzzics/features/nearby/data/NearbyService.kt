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
 * Keeps Nearby going with the app in the background or the screen off: connections to friends
 * (chat, walkie-talkie, "come to me" keep arriving), looking for friends who dropped out, and
 * location sharing. Android would otherwise stop the app after a while and every connection
 * with it.
 *
 * Runs while there's something to keep (friends connected, friends being looked for, or
 * location sharing), shown as one quiet notification with what's going on and Stop buttons.
 * Started from the app while it's on screen (connecting, turning sharing on); it stops itself.
 */
class NearbyService : Service(), KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null
    /** foreground with the location type (while location sharing is on) */
    private var withLocation = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = get<NearbyManager>()
        when (intent?.action) {
            ACTION_STOP_LOCATION -> {
                manager.setLocationSharing(false)
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                manager.disconnectAll()
                return START_NOT_STICKY
            }
        }
        ensureChannel()
        val state = manager.state.value
        if (!needed(state) || !goForeground(state)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watching == null) {
            // keeps the notification up to date; drops the location part / stops when done
            watching = scope.launch {
                manager.state
                    .map { Triple(it.friends.map { f -> f.name }, it.reconnecting, it.radar.sharing to it.radar.sharingUntilMs) }
                    .distinctUntilChanged()
                    .collect {
                        val current = manager.state.value
                        when {
                            !needed(current) -> {
                                ServiceCompat.stopForeground(this@NearbyService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            }
                            // sharing stopped: no location type any more
                            withLocation && !current.radar.sharing -> goForeground(current)
                            else -> update(current)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    /** true if it's foreground now (with the location type while sharing, if allowed) */
    private fun goForeground(state: NearbyState): Boolean {
        val wantsLocation = state.radar.sharing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (wantsLocation) {
                val ok = runCatching {
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, notification(state), connected or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                }.isSuccess
                if (ok) {
                    withLocation = true
                    return true
                }
            }
            withLocation = false
            return runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(state), connected) }.isSuccess
        }
        withLocation = wantsLocation
        return runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(state), 0) }.isSuccess
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(state: NearbyState): android.app.Notification {
        val names = state.friends.map { it.name }
        val title = when {
            names.isNotEmpty() -> "Connected to ${names.joinToString()}"
            state.reconnecting.isNotEmpty() -> "Looking for ${state.reconnecting.joinToString()}"
            else -> "Nearby"
        }
        val text = buildString {
            if (names.isNotEmpty() && state.reconnecting.isNotEmpty()) {
                append("Looking for ${state.reconnecting.joinToString()} · ")
            }
            if (state.radar.sharing) {
                append("Sharing your location")
                state.radar.sharingUntilMs?.let {
                    append(" until ")
                    append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                }
            } else {
                append("Chat, walkie-talkie and \"come to me\" keep working in the background")
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .apply {
                if (state.radar.sharing) {
                    addAction(
                        0, "Stop sharing location",
                        PendingIntent.getService(
                            this@NearbyService, 2,
                            Intent(this@NearbyService, NearbyService::class.java).setAction(ACTION_STOP_LOCATION),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                addAction(
                    0, "Disconnect",
                    PendingIntent.getService(
                        this@NearbyService, 3,
                        Intent(this@NearbyService, NearbyService::class.java).setAction(ACTION_DISCONNECT),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            .build()
    }

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
                NotificationChannel(CHANNEL_ID, "Staying connected", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "While you're connected to friends nearby or sharing your location"
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "nearby_connection"
        private const val NOTIFICATION_ID = 4340
        private const val ACTION_STOP_LOCATION = "com.example.juzzics.STOP_SHARING_LOCATION"
        private const val ACTION_DISCONNECT = "com.example.juzzics.DISCONNECT_ALL"

        /** something worth keeping the app alive for */
        private fun needed(state: NearbyState) =
            state.friends.isNotEmpty() || state.reconnecting.isNotEmpty() || state.radar.sharing

        /**
         * starts it, or updates it (e.g. location sharing turned on). Call while the app is on
         * screen: Android doesn't allow starting it from the background.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, NearbyService::class.java))
            }
        }
    }
}
