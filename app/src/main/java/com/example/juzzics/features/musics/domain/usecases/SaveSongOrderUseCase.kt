package com.example.juzzics.features.musics.domain.usecases

import com.example.juzzics.features.musics.domain.repo.MusicRepo

class SaveSongOrderUseCase(private val repo: MusicRepo) {
    suspend operator fun invoke(songIds: List<Long>) = repo.saveSongOrder(songIds)
}
