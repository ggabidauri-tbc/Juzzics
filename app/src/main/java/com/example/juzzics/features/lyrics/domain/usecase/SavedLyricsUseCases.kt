package com.example.juzzics.features.lyrics.domain.usecase

import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import kotlinx.coroutines.flow.Flow

class ObserveSavedLyricsUseCase(private val repo: LyricsRepo) {
    operator fun invoke(songId: Long): Flow<LyricsDomain?> = repo.observeSavedLyrics(songId)
}

class SaveLyricsUseCase(private val repo: LyricsRepo) {
    suspend operator fun invoke(songId: Long, lyrics: LyricsDomain) = repo.saveLyrics(songId, lyrics)
}
