package com.example.juzzics.features.player.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.juzzics.MainActivity
import com.example.juzzics.R
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.player.PlayerState
import org.koin.core.context.GlobalContext

private fun playerController(): PlayerController = GlobalContext.get().get()

/** Home-screen widget: current song, previous / play-pause / next. Tap the text to open the app. */
class JuzzicsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = playerController()
        provideContent {
            val state by controller.state.collectAsState()
            GlanceTheme { WidgetContent(state) }
        }
    }
}

class JuzzicsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JuzzicsWidget()
}

@Composable
private fun WidgetContent(state: PlayerState) {
    val song = state.currentSong
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(28.dp)
        )
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 8.dp)
                .clickable(actionStartActivity(MainActivity.openPlayerIntent(LocalContext.current)))
        ) {
            Text(
                text = song?.title ?: "Nothing playing",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold)
            )
            Text(
                text = song?.artist?.takeUnless { it == "<unknown>" } ?: "Open Juzzics",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
        WidgetButton(R.drawable.ic_skip_previous, "Previous", actionRunCallback<WidgetPreviousAction>())
        WidgetButton(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (state.isPlaying) "Pause" else "Play",
            actionRunCallback<WidgetPlayPauseAction>()
        )
        WidgetButton(R.drawable.ic_skip_next, "Next", actionRunCallback<WidgetNextAction>())
    }
}

@Composable
private fun WidgetButton(icon: Int, description: String, action: androidx.glance.action.Action) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        modifier = GlanceModifier
            .size(40.dp)
            .padding(6.dp)
            .clickable(action)
    )
}

class WidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        playerController().togglePlayPause()
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        playerController().next()
}

class WidgetPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        playerController().previous()
}
