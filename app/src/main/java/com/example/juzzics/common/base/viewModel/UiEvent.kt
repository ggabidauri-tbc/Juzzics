package com.example.juzzics.common.base.viewModel

/**
 * One-off events from ViewModel to Screen (a toast, a scroll...). Each event is delivered once,
 * to one collector: a screen collects its ViewModel's events in exactly one place
 * ([com.example.juzzics.common.base.BaseHandler] or [listen]).
 * Loading is not an event: it's the [LOADING] state.
 */
interface UiEvent {
    data class Message(val msg: String) : UiEvent
}
