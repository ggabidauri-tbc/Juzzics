package com.example.juzzics.common.base.viewModel

/**
 * Typed key of one piece of ViewModel state.
 *
 * ```
 * val IS_PLAYING = StateKey("isPlaying", false)
 * val CLICKED_MUSIC = StateKey<MusicFileUi?>("clickedMusic", null)
 * ```
 * Reading `IS_PLAYING()` gives a `Boolean`, `IS_PLAYING("yes")` doesn't compile.
 */
class StateKey<T>(val name: String, val default: T) {
    override fun toString() = name
}
