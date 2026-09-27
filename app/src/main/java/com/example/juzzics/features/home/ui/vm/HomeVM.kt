package com.example.juzzics.features.home.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.features.home.domain.usecase.GetMostPlayedUseCase
import com.example.juzzics.features.home.domain.usecase.GetRecentlyPlayedUseCase
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase

class HomeVM(
    getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    getMostPlayedUseCase: GetMostPlayedUseCase,
    getPlaylistsUseCase: GetPlaylistsUseCase,
    private val player: PlayerController,
) : BaseViewModel(listOf(RECENTLY_PLAYED, MOST_PLAYED, PLAYLISTS)) {

    companion object {
        val RECENTLY_PLAYED = StateKey<List<MusicFileDomain>>("recentlyPlayed", emptyList())
        /** song to how many times it was played */
        val MOST_PLAYED = StateKey<List<Pair<MusicFileDomain, Int>>>("mostPlayed", emptyList())
        val PLAYLISTS = StateKey<List<PlaylistDomain>>("playlists", emptyList())
    }

    init {
        getRecentlyPlayedUseCase().collectIn(RECENTLY_PLAYED)
        getMostPlayedUseCase().collectIn(MOST_PLAYED)
        getPlaylistsUseCase().collectIn(PLAYLISTS)
    }

    override fun onAction(action: Action) {
        when (action) {
            is PlaySongsAction -> player.playQueue(action.songs, action.startIndex, action.source)
            is PlayPlaylistAction -> PLAYLISTS().find { it.id == action.playlistId }
                ?.let { player.playQueue(it.songs, 0, source = it.name) }
        }
    }

    /** plays [songs] (e.g. the "recently played" row) starting from [startIndex] */
    data class PlaySongsAction(
        val songs: List<MusicFileDomain>,
        val startIndex: Int,
        val source: String,
    ) : Action
    data class PlayPlaylistAction(val playlistId: Long) : Action
}
