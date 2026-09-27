package com.example.juzzics.features.player.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** Quick-settings tile: shows the current song, tap to play/pause. */
class PlaybackTileService : TileService() {

    private val controller: PlayerController get() = GlobalContext.get().get()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch { controller.state.collect { render(it) } }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        controller.togglePlayPause()
    }

    private fun render(state: PlayerState) {
        val tile = qsTile ?: return
        val song = state.currentSong
        tile.state = when {
            song == null -> Tile.STATE_INACTIVE
            state.isPlaying -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = song?.title ?: "Juzzics"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                song == null -> "Nothing playing"
                state.isPlaying -> "Playing"
                else -> "Paused"
            }
        }
        tile.updateTile()
    }
}
