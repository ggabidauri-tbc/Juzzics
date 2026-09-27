package com.example.juzzics.features.nearby.ui.vm

import androidx.lifecycle.viewModelScope
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.features.nearby.data.BlendMaker
import com.example.juzzics.features.nearby.data.NearbyManager
import com.example.juzzics.features.nearby.data.ReceivedSong
import com.example.juzzics.features.nearby.data.ReceivedSongs
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.Blend
import com.example.juzzics.features.nearby.domain.PlayTarget
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.domain.RemoteSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NearbyVM(
    private val nearby: NearbyManager,
    private val received: ReceivedSongs,
    private val player: PlayerController,
) : BaseViewModel(
    listOf(NEARBY, OPEN_FRIEND, FRIEND_QUERY, PLAY_TARGET, SEND_MODE, SEND_TO_QUEUE, MY_SONGS, RECEIVED, MESSAGE, BLEND, SHOW_BLEND)
) {

    companion object {
        /** connections, found phones, friends and their songs */
        val NEARBY = StateKey("nearby", NearbyState())
        /** friend whose song list is open */
        val OPEN_FRIEND = StateKey<String?>("openFriend", null)
        val FRIEND_QUERY = StateKey("friendQuery", "")
        /** where tapping a friend's song plays it */
        val PLAY_TARGET = StateKey("playTarget", PlayTarget.THEIR_PHONE)
        /** the "send my songs" page adds them to the friend's queue (Car DJ) instead of playing them */
        val SEND_TO_QUEUE = StateKey("sendToQueue", false)
        /** your music mixed with connected friends' (null until made) */
        val BLEND = StateKey<Blend?>("blend", null)
        val SHOW_BLEND = StateKey("showBlend", false)
        /** the open page lists this phone's songs, to send one to the friend */
        val SEND_MODE = StateKey("sendMode", false)
        /** this phone's songs (null until loaded) */
        val MY_SONGS = StateKey<List<RemoteSong>?>("mySongs", null)
        /** songs friends sent, newest first */
        val RECEIVED = StateKey<List<ReceivedSong>>("received", emptyList())
        /** a message for the user (e.g. "Saved to Music/Juzzics") */
        val MESSAGE = StateKey<String?>("message", null)
    }

    /** "Reshuffle" makes a different mix */
    private var blendSeed = (System.currentTimeMillis() % 100_000).toInt()

    private fun makeBlend() = viewModelScope.launch {
        val mine = MY_SONGS() ?: nearby.mySongs().also { MY_SONGS(it) }
        val friends = NEARBY().friends
        val seed = blendSeed
        // hundreds of songs per phone: off the main thread
        BLEND(withContext(Dispatchers.Default) { BlendMaker.make(mine, friends, seed) })
    }

    init {
        nearby.state.collectIn(NEARBY)
        received.songs.collectIn(RECEIVED)
    }

    override fun onAction(action: Action) {
        when (action) {
            is SetSharingAction -> nearby.setSharing(action.on)
            is RenameDeviceAction -> nearby.setDeviceName(action.name)
            is SearchAction -> if (action.on) nearby.startSearching() else nearby.stopSearching()
            is ConnectAction -> nearby.connect(action.device)
            is AcceptAction -> nearby.acceptPending()
            is RejectAction -> nearby.rejectPending()
            is DisconnectAction -> {
                nearby.disconnect(action.endpointId)
                if (OPEN_FRIEND() == action.endpointId) OPEN_FRIEND(null)
            }
            is OpenFriendAction -> {
                OPEN_FRIEND(action.endpointId)
                SEND_MODE(false)
                FRIEND_QUERY("")
            }
            is OpenSendAction -> {
                OPEN_FRIEND(action.endpointId)
                SEND_MODE(true)
                SEND_TO_QUEUE(action.toQueue)
                FRIEND_QUERY("")
                viewModelScope.launch { MY_SONGS(nearby.mySongs()) }
            }
            is PlayTargetAction -> PLAY_TARGET(action.target)
            is ListenHereAction -> nearby.listenHere(action.endpointId, action.song)
            is QueueOnFriendAction -> {
                nearby.queueOnFriend(action.endpointId, action.songId)
                MESSAGE("Added to their queue")
            }
            is SetCarDjAction -> nearby.setCarDj(action.on)
            is OpenBlendAction -> {
                SHOW_BLEND(action.open)
                if (action.open) makeBlend()
            }
            is ReshuffleBlendAction -> {
                blendSeed++
                makeBlend()
            }
            is PlayBlendAction -> BLEND()?.let { blend ->
                nearby.playBlend(blend.mix, action.index)
                OpenPlayerRequests.request()
            }
            is PlaySharedAction -> BLEND()?.let { blend ->
                nearby.playMine(blend.shared.map { it.song.id }, action.index, source = "Songs you share")
                OpenPlayerRequests.request()
            }
            is StartShoutOutAction -> nearby.startShoutOut()
            is StopShoutOutAction -> nearby.stopShoutOut(action.send)
            is StartSingingAction -> nearby.startSinging(action.endpointId)
            is StopSingingAction -> nearby.stopSinging()
            is StopSingerAction -> nearby.stopSinger()
            is MicGainAction -> nearby.setMicGain(action.gain)
            is SendToFriendAction -> if (SEND_TO_QUEUE()) {
                nearby.queueMySongOnFriend(action.endpointId, action.songId)
                MESSAGE("Sending it to their queue…")
            } else nearby.sendToFriend(action.endpointId, action.songId)
            is FriendQueryAction -> FRIEND_QUERY(action.query)
            is PlayOnFriendAction -> nearby.playOnFriend(action.endpointId, action.songId)
            is CommandAction -> nearby.sendCommand(action.endpointId, action.command)
            is RefreshLibraryAction -> nearby.requestLibrary(action.endpointId)
            is DismissErrorAction -> nearby.clearError()
            is LetFriendsSaveAction -> nearby.setLetFriendsSave(action.allow)
            is StartPartyAction -> nearby.startParty()
            is EndPartyAction -> nearby.endParty()
            is PlayReceivedAction -> received.find(action.songId)?.let { song ->
                player.playQueue(listOf(received.toMusicFile(song)), 0, source = "From ${song.from}")
                OpenPlayerRequests.request()
            }
            is SaveReceivedAction -> launch(emitLoadingAction = false) {
                received.saveToLibrary(action.songId)
                    .onSuccess { MESSAGE("Saved to Music/Juzzics. It shows up in your songs next time the app opens.") }
                    .onFailure { MESSAGE("Couldn't save it: ${it.message}") }
            }
            is DismissMessageAction -> MESSAGE(null)
            is ShowMessageAction -> MESSAGE(action.message)
        }
    }

    data class SetSharingAction(val on: Boolean) : Action
    data class RenameDeviceAction(val name: String) : Action
    data class SearchAction(val on: Boolean) : Action
    data class ConnectAction(val device: NearbyDevice) : Action
    data object AcceptAction : Action
    data object RejectAction : Action
    data class DisconnectAction(val endpointId: String) : Action
    /** null closes the friend's song list */
    data class OpenFriendAction(val endpointId: String?) : Action
    data class FriendQueryAction(val query: String) : Action
    /** opens this phone's songs, to send one to [endpointId] */
    data class OpenSendAction(val endpointId: String, val toQueue: Boolean = false) : Action
    data class PlayTargetAction(val target: PlayTarget) : Action
    data class QueueOnFriendAction(val endpointId: String, val songId: Long) : Action
    data class SetCarDjAction(val on: Boolean) : Action
    data class OpenBlendAction(val open: Boolean) : Action
    data object ReshuffleBlendAction : Action
    data class PlayBlendAction(val index: Int) : Action
    data class PlaySharedAction(val index: Int) : Action
    data object StartShoutOutAction : Action
    /** sing-along: this phone's mic plays live on [endpointId]'s phone */
    data class StartSingingAction(val endpointId: String) : Action
    data object StopSingingAction : Action
    /** on the phone playing a friend's voice: turn their mic off */
    data object StopSingerAction : Action
    data class MicGainAction(val gain: Float) : Action
    /** [send] false: cancelled */
    data class StopShoutOutAction(val send: Boolean) : Action
    data class ListenHereAction(val endpointId: String, val song: RemoteSong) : Action
    data class SendToFriendAction(val endpointId: String, val songId: Long) : Action
    data class PlayOnFriendAction(val endpointId: String, val songId: Long) : Action
    data class CommandAction(val endpointId: String, val command: RemoteCommand) : Action
    data class RefreshLibraryAction(val endpointId: String) : Action
    data object DismissErrorAction : Action
    data class LetFriendsSaveAction(val allow: Boolean) : Action
    data object StartPartyAction : Action
    /** host: ends it for everyone; guest: leaves */
    data object EndPartyAction : Action
    data class PlayReceivedAction(val songId: Long) : Action
    data class SaveReceivedAction(val songId: Long) : Action
    data object DismissMessageAction : Action
    data class ShowMessageAction(val message: String) : Action
}
