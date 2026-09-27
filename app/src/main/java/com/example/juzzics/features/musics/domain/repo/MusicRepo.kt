package com.example.juzzics.features.musics.domain.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain

interface MusicRepo {
    /** device songs, in your saved order (new songs at the end, by title) */
    suspend fun getAllLocalMusicFiles(): Result<List<MusicFileDomain>>

    suspend fun saveSongOrder(songIds: List<Long>)
}
