package com.example.juzzics.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * "Open the player" requests from outside the screens (notification, widget).
 * The player (drawn over every tab) opens full screen.
 */
object OpenPlayerRequests {
    private val _count = MutableStateFlow(0)

    /** increases with every request */
    val count: StateFlow<Int> = _count

    fun request() = _count.update { it + 1 }

    /** last request the Musics screen handled */
    var handledByPlayer = 0
}
