package com.example.juzzics.features.lyrics.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.usecase.FetchLyricsUseCase
import com.example.juzzics.features.lyrics.ui.vm.logics.fetchLyrics

class FetchLyricsVM(
    val fetchLyricsUseCase: FetchLyricsUseCase
) : BaseViewModel(listOf(LYRICS, ARTIST, TITLE)) {
    companion object {
        val LYRICS = StateKey<LyricsDomain?>("lyrics", null)
        val ARTIST = StateKey("artist", "nightwish")
        val TITLE = StateKey("title", "ghost love score")
    }

    override fun onAction(action: Action) {
        when (action) {
            is FetchLyricsAction -> fetchLyrics()
            is UpdateArtistAction -> ARTIST(action.value)
            is UpdateTitleAction -> TITLE(action.value)
        }
    }

    data object FetchLyricsAction : Action
    data class UpdateArtistAction(val value: String) : Action
    data class UpdateTitleAction(val value: String) : Action
}
