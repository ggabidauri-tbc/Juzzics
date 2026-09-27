package com.example.juzzics.features.musics.data.repo

import com.example.juzzics.features.musics.data.local.SongOrderDao
import com.example.juzzics.features.musics.data.localProvider.MusicLocalProvider
import com.example.juzzics.features.musics.data.model.toDomain
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.domain.repo.MusicRepo

class MusicRepoImpl(
    private val musicLocalProvider: MusicLocalProvider,
    private val songOrderDao: SongOrderDao,
) : MusicRepo {

    override suspend fun getAllLocalMusicFiles(): Result<List<MusicFileDomain>> =
        musicLocalProvider.getAllLocalMusicFiles().map { songs ->
            val savedPositions = songOrderDao.getAll().associate { it.songId to it.position }
            // stable sort: songs without a saved position keep their (title) order at the end
            songs.map { it.toDomain() }.sortedBy { savedPositions[it.id] ?: Int.MAX_VALUE }
        }

    override suspend fun saveSongOrder(songIds: List<Long>) = songOrderDao.replaceAll(songIds)
}
