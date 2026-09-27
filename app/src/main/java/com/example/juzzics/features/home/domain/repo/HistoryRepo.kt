package com.example.juzzics.features.home.domain.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.coroutines.flow.Flow

interface HistoryRepo {
    suspend fun recordPlay(song: MusicFileDomain)

    /** each song once, most recent first */
    fun observeRecentlyPlayed(limit: Int): Flow<List<MusicFileDomain>>

    /** songs with how many times they were played, most first */
    fun observeMostPlayed(limit: Int): Flow<List<Pair<MusicFileDomain, Int>>>
}
