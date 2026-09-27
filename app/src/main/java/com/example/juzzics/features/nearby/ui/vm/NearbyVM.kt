package com.example.juzzics.features.nearby.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.features.nearby.data.NearbyManager
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.RemoteCommand

class NearbyVM(
    private val nearby: NearbyManager,
) : BaseViewModel(listOf(NEARBY, OPEN_FRIEND, FRIEND_QUERY)) {

    companion object {
        /** connections, found phones, friends and their songs */
        val NEARBY = StateKey("nearby", NearbyState())
        /** friend whose song list is open */
        val OPEN_FRIEND = StateKey<String?>("openFriend", null)
        val FRIEND_QUERY = StateKey("friendQuery", "")
    }

    init {
        nearby.state.collectIn(NEARBY)
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
                FRIEND_QUERY("")
            }
            is FriendQueryAction -> FRIEND_QUERY(action.query)
            is PlayOnFriendAction -> nearby.playOnFriend(action.endpointId, action.songId)
            is CommandAction -> nearby.sendCommand(action.endpointId, action.command)
            is RefreshLibraryAction -> nearby.requestLibrary(action.endpointId)
            is DismissErrorAction -> nearby.clearError()
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
    data class PlayOnFriendAction(val endpointId: String, val songId: Long) : Action
    data class CommandAction(val endpointId: String, val command: RemoteCommand) : Action
    data class RefreshLibraryAction(val endpointId: String) : Action
    data object DismissErrorAction : Action
}
