package com.example.juzzics.features.musics.data.repo

import com.example.juzzics.common.songs.SongSettings
import com.example.juzzics.features.musics.data.local.SongOrderDao
import com.example.juzzics.features.musics.data.localProvider.MusicLocalProvider
import com.example.juzzics.features.musics.data.model.toDomain
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.domain.repo.MusicRepo

class MusicRepoImpl(
    private val musicLocalProvider: MusicLocalProvider,
    private val songOrderDao: SongOrderDao,
    private val songSettings: SongSettings,
) : MusicRepo {

    override suspend fun getAllLocalMusicFiles(): Result<List<MusicFileDomain>> =
        musicLocalProvider.getAllLocalMusicFiles().map { songs ->
            val savedPositions = songOrderDao.getAll().associate { it.songId to it.position }
            val names = songSettings.names.value
            // stable sort: songs without a saved position keep their (title) order at the end
            songs.map { it.toDomain() }
                .map { song -> names[song.id]?.let { song.copy(title = it.title, artist = it.artist) } ?: song }
                .sortedBy { savedPositions[it.id] ?: Int.MAX_VALUE }
        }

    override suspend fun saveSongOrder(songIds: List<Long>) = songOrderDao.replaceAll(songIds)
}
