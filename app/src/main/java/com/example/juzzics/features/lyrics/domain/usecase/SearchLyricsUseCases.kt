package com.example.juzzics.features.lyrics.domain.usecase

import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo

/** automatic lookup for a song, from its (possibly messy) file name */
class FindLyricsUseCase(private val repo: LyricsRepo) {
    suspend operator fun invoke(rawTitle: String, artistTag: String, durationMs: Long): Result<LyricsDomain> =
        repo.findLyrics(rawTitle, artistTag, durationMs)
}

/** manual search: results to pick from */
class SearchLyricsUseCase(private val repo: LyricsRepo) {
    suspend operator fun invoke(artist: String, title: String, durationMs: Long): Result<List<LyricsCandidate>> =
        repo.searchLyrics(artist, title, durationMs)
}
