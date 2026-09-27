package com.example.juzzics.features.home.data.repo

import com.example.juzzics.features.home.data.local.PlayHistoryDao
import com.example.juzzics.features.home.data.local.toPlay
import com.example.juzzics.features.home.domain.repo.HistoryRepo
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepoImpl(private val dao: PlayHistoryDao) : HistoryRepo {

    override suspend fun recordPlay(song: MusicFileDomain) =
        dao.insert(song.toPlay(playedAt = System.currentTimeMillis()))

    override fun observeRecentlyPlayed(limit: Int): Flow<List<MusicFileDomain>> =
        dao.observeRecentlyPlayed(limit).map { plays -> plays.map { it.toDomain() } }

    override fun observeMostPlayed(limit: Int): Flow<List<Pair<MusicFileDomain, Int>>> =
        dao.observeMostPlayed(limit).map { rows -> rows.map { it.toDomain() to it.playCount } }
}
