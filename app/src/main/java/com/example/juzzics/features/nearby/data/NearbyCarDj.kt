package com.example.juzzics.features.nearby.data

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.nearby.domain.QueueEntry
import com.example.juzzics.features.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Car DJ: the phone playing in the car takes songs from everyone connected (theirs, sent over,
 * or picked from this phone's library) into its queue. Friends' songs take turns
 * (Anna, Nika, Anna, Nika...) so nobody fills the whole queue, and everyone sees "Up next".
 */
class NearbyCarDj(
    private val player: PlayerController,
    scope: CoroutineScope,
    /** tells every connected friend (DJ_STATE) */
    private val broadcast: (NearbyMessage) -> Unit,
    private val onState: (open: Boolean, queue: List<QueueEntry>) -> Unit,
) {
    var open = false
        private set

    private class Added(val song: MusicFileDomain, val by: String) {
        /** it was in "up next" once: when it's not any more it was played (or removed) */
        var seen = false
    }

    /** friends' songs still to come, in the order they were added */
    private val pending = mutableListOf<Added>()

    init {
        // follow the queue: played / removed songs leave the list, everyone sees the new "up next"
        scope.launch {
            player.state
                .map { state -> state.currentIndex to state.queue.map { it.id } }
                .distinctUntilChanged()
                .collect { (index, ids) ->
                    val upcoming = ids.drop(index + 1).toSet()
                    pending.forEach { if (it.song.id in upcoming) it.seen = true }
                    pending.removeAll { it.seen && it.song.id !in upcoming }
                    if (open) publish()
                }
        }
    }

    fun setOpen(on: Boolean) {
        open = on
        if (!on) pending.clear()
        publish()
    }

    /** a friend added [song] (already playable here) */
    fun add(song: MusicFileDomain, by: String) {
        if (!open || pending.any { it.song.id == song.id }) return
        pending += Added(song, by)
        player.arrangeUpNext(takingTurns())
        publish()
    }

    /** DJ_STATE for a friend who just connected */
    fun stateMessage() = NearbyMessage(NearbyMessage.DJ_STATE, djOpen = open, queue = if (open) upNext() else emptyList())

    /** friends' songs, one per person in turn, in the order each person added theirs */
    private fun takingTurns(): List<MusicFileDomain> {
        val byPerson = pending.groupBy { it.by }.values.map { it.toMutableList() }
        val result = mutableListOf<MusicFileDomain>()
        while (byPerson.any { it.isNotEmpty() }) {
            byPerson.forEach { songs -> songs.removeFirstOrNull()?.let { result += it.song } }
        }
        return result
    }

    private fun upNext(): List<QueueEntry> {
        val state = player.state.value
        return state.queue.drop(state.currentIndex + 1).take(MAX_SHOWN).map { song ->
            QueueEntry(
                title = song.title.orEmpty(),
                artist = song.artist?.takeUnless { it == "<unknown>" }.orEmpty(),
                addedBy = pending.find { it.song.id == song.id }?.by.orEmpty(),
            )
        }
    }

    private fun publish() {
        val queue = if (open) upNext() else emptyList()
        onState(open, queue)
        broadcast(NearbyMessage(NearbyMessage.DJ_STATE, djOpen = open, queue = queue))
    }

    private companion object {
        const val MAX_SHOWN = 15
    }
}
