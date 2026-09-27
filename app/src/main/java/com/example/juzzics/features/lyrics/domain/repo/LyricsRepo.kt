package com.example.juzzics.features.lyrics.domain.repo

import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import kotlinx.coroutines.flow.Flow

interface LyricsRepo {
    /** searches lyrics online: LRCLIB first (synced if available), lyrics.ovh as fallback */
    suspend fun getLyrics(artist: String, title: String): Result<LyricsDomain>

    /**
     * Automatic search for a song with a messy (e.g. YouTube) name: cleans the name, tries
     * several artist/title guesses and a free-text search, and only accepts a result whose
     * length or name matches the song.
     */
    suspend fun findLyrics(rawTitle: String, artistTag: String, durationMs: Long): Result<LyricsDomain>

    /** manual search: results to pick from, best first */
    suspend fun searchLyrics(artist: String, title: String, durationMs: Long): Result<List<LyricsCandidate>>

    /** songs that have lyrics saved */
    suspend fun songIdsWithLyrics(): Set<Long>

    /** lyrics tied to a song, null if none */
    fun observeSavedLyrics(songId: Long): Flow<LyricsDomain?>

    suspend fun saveLyrics(songId: Long, lyrics: LyricsDomain)
}
