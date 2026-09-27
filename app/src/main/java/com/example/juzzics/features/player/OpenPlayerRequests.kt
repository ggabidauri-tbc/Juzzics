package com.example.juzzics.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * "Open the player" requests from outside the screens (notification, widget).
 * The navigation switches to the Musics tab, the Musics screen opens the full player.
 */
object OpenPlayerRequests {
    private val _count = MutableStateFlow(0)

    /** increases with every request */
    val count: StateFlow<Int> = _count

    fun request() = _count.update { it + 1 }

    /** last request the navigation handled (so it isn't handled again, e.g. after rotation) */
    var handledByNavigation = 0

    /** last request the Musics screen handled */
    var handledByPlayer = 0
}
