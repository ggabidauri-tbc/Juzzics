package com.example.juzzics.features.nearby.ui.vm

import androidx.lifecycle.viewModelScope
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.common.messages.AppMessages
import com.example.juzzics.features.nearby.data.BlendMaker
import com.example.juzzics.features.nearby.data.NearbyManager
import com.example.juzzics.features.nearby.data.OfflineMaps
import com.example.juzzics.features.nearby.data.OfflineMapsState
import com.example.juzzics.features.nearby.data.ReceivedSong
import com.example.juzzics.features.nearby.data.ReceivedSongs
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.Blend
import com.example.juzzics.features.nearby.domain.NearbyPanel
import com.example.juzzics.features.nearby.domain.PlayTarget
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.domain.RemoteSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds

class NearbyVM(
    private val nearby: NearbyManager,
    private val received: ReceivedSongs,
    private val player: PlayerController,
    private val offlineMaps: OfflineMaps,
) : BaseViewModel(
    listOf(NEARBY, OPEN_FRIEND, FRIEND_QUERY, PLAY_TARGET, SEND_MODE, SEND_TO_QUEUE, MY_SONGS, RECEIVED, BLEND, SHOW_BLEND, PANEL, FRIEND_PAGE, SHOW_SETTINGS, HEADING, RADAR_AS_MAP, OFFLINE_MAPS)
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
        /** a "Together" activity opened as its own page */
        val PANEL = StateKey<NearbyPanel?>("panel", null)
        /** a connected friend's page (endpoint id) */
        val FRIEND_PAGE = StateKey<String?>("friendPage", null)
        val SHOW_SETTINGS = StateKey("showSettings", false)
        /** friend radar: where the phone points (degrees from north), null without a compass */
        val HEADING = StateKey<Float?>("heading", null)
        /** the radar page shows a map instead of the radar */
        val RADAR_AS_MAP = StateKey("radarAsMap", false)
        /** map areas saved for offline use, and the one downloading */
        val OFFLINE_MAPS = StateKey("offlineMaps", OfflineMapsState())
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
        nearby.radarHeading.collectIn(HEADING)
        offlineMaps.state.collectIn(OFFLINE_MAPS)
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
                if (FRIEND_PAGE() == action.endpointId) FRIEND_PAGE(null)
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
                AppMessages.show("Added to their queue")
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
                AppMessages.show("Sending it to their queue…")
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
                    .onSuccess { AppMessages.show("Saved to Music/Juzzics. It shows up in your songs next time the app opens.") }
                    .onFailure { AppMessages.show("Couldn't save it: ${it.message}") }
            }
            is OpenPanelAction -> PANEL(action.panel)
            is OpenFriendPageAction -> FRIEND_PAGE(action.endpointId)
            is ShowSettingsAction -> SHOW_SETTINGS(action.show)
            is LocationSharingAction -> nearby.setLocationSharing(action.on, action.forMs)
            is RadarVisibleAction -> nearby.setRadarVisible(action.visible)
            is ClearTrailsAction -> nearby.clearTrails()
            is RadarModeAction -> RADAR_AS_MAP(action.map)
            is RefreshMapAreasAction -> offlineMaps.refresh()
            is SaveMapAreaAction -> offlineMaps.download(action.bounds, action.name)
            is CancelMapDownloadAction -> offlineMaps.cancelDownload()
            is DeleteMapAreaAction -> offlineMaps.delete(action.id)
            is SetMeetingPointAction -> nearby.setMeetingPoint(action.lat, action.lon)
            is ClearMeetingPointAction -> nearby.clearMeetingPoint()
            is HideMeetingPointAction -> nearby.hideMeetingPoint(action.personId)
            is ShowHiddenPinsAction -> nearby.showHiddenMeetingPoints()
            is CallOverAction -> nearby.callFriendsOver()
            is DismissComeToMeAction -> nearby.dismissComeToMe()
            is TalkAction -> if (action.on) nearby.startTalking() else nearby.stopTalking()
            is ForgetRememberedAction -> nearby.forgetRememberedPhones()
            is SendChatAction -> nearby.sendChat(action.text, action.withLocation)
            is ChatOpenAction -> nearby.setChatOpen(action.open)
            is SendPhotoAction -> nearby.sendPhoto(action.uri, action.caption)
            is CheckMinutesAction -> nearby.setCheckMinutes(action.minutes)
            is FindFriendsAction -> {
                // visible and looking at once: nobody has to understand who shares and who looks
                nearby.setSharing(true)
                nearby.startSearching()
            }
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
    /** null closes it */
    data class OpenPanelAction(val panel: NearbyPanel?) : Action
    /** null closes it */
    data class OpenFriendPageAction(val endpointId: String?) : Action
    data class ShowSettingsAction(val show: Boolean) : Action
    /** friend radar: share this phone's position with connected friends */
    data class LocationSharingAction(val on: Boolean, val forMs: Long? = null) : Action
    /** the radar is on screen (GPS + compass run only then, unless sharing) */
    data class RadarVisibleAction(val visible: Boolean) : Action
    data object ClearTrailsAction : Action
    /** the radar page shows a map (true) or the radar (false) */
    data class RadarModeAction(val map: Boolean) : Action
    data object RefreshMapAreasAction : Action
    /** saves [bounds] (what's on screen) so the map works there without internet */
    data class SaveMapAreaAction(val bounds: LatLngBounds, val name: String) : Action
    data object CancelMapDownloadAction : Action
    data class DeleteMapAreaAction(val id: Long) : Action
    /** "meet here": everyone connected sees it */
    data class SetMeetingPointAction(val lat: Double, val lon: Double) : Action
    data object ClearMeetingPointAction : Action
    /** someone else's meeting point, hidden on this phone */
    data class HideMeetingPointAction(val personId: String) : Action
    data object ShowHiddenPinsAction : Action
    /** "come to me": friends' phones buzz and point here */
    data object CallOverAction : Action
    data object DismissComeToMeAction : Action
    /** walkie-talkie: pressed (true) / let go (false) */
    data class TalkAction(val on: Boolean) : Action
    /** paired phones compare codes again next time */
    data object ForgetRememberedAction : Action
    /** group chat: to everyone; [withLocation]: "I'm here" too */
    data class SendChatAction(val text: String, val withLocation: Boolean = false) : Action
    /** the chat is on screen (nothing unread) / not */
    data class ChatOpenAction(val open: Boolean) : Action
    /** "check on friends": alert after this many minutes without moving / news (0 = off) */
    data class CheckMinutesAction(val minutes: Int) : Action
    /** photo drop: to everyone, into the group chat */
    data class SendPhotoAction(val uri: android.net.Uri, val caption: String = "") : Action
    /** this phone becomes visible and looks for friends */
    data object FindFriendsAction : Action
}
