package com.example.juzzics.features.home.domain.usecase

import com.example.juzzics.features.home.domain.repo.HistoryRepo
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.coroutines.flow.Flow

class RecordPlayUseCase(private val repo: HistoryRepo) {
    suspend operator fun invoke(song: MusicFileDomain) = repo.recordPlay(song)
}

class GetRecentlyPlayedUseCase(private val repo: HistoryRepo) {
    operator fun invoke(limit: Int = 20): Flow<List<MusicFileDomain>> = repo.observeRecentlyPlayed(limit)
}

class GetMostPlayedUseCase(private val repo: HistoryRepo) {
    operator fun invoke(limit: Int = 10): Flow<List<Pair<MusicFileDomain, Int>>> =
        repo.observeMostPlayed(limit)
}
