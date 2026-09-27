package com.example.juzzics.features.lyrics.data.repo

import com.example.juzzics.features.lyrics.data.dto.LrclibDto
import com.example.juzzics.features.lyrics.data.local.LyricsDao
import com.example.juzzics.features.lyrics.data.local.LyricsEntity
import com.example.juzzics.features.lyrics.data.service.LrclibService
import com.example.juzzics.features.lyrics.data.service.LyricsService
import com.example.juzzics.features.lyrics.domain.model.LyricsCandidate
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.model.lrcToPlainText
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import com.example.juzzics.features.lyrics.domain.util.SongNameCleaner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/** results this close in length (seconds) are treated as the same recording */
private const val SAME_LENGTH_SECONDS = 3.0

/** music videos often have an intro/outro: still accept results this close */
private const val CLOSE_LENGTH_SECONDS = 20.0

/** share of a result's name words that must appear in what we searched for */
private const val NAME_MATCH = 0.6

class LyricsRepoImpl(
    private val lyricsService: LyricsService,
    private val lrclibService: LrclibService,
    private val lyricsDao: LyricsDao,
) : LyricsRepo {

    override suspend fun getLyrics(artist: String, title: String): Result<LyricsDomain> =
        searchLyrics(artist, title, durationMs = 0).mapCatching { it.first().lyrics }

    override suspend fun findLyrics(rawTitle: String, artistTag: String, durationMs: Long): Result<LyricsDomain> =
        runCatching {
            val guesses = SongNameCleaner.guesses(rawTitle, artistTag)

            // all LRCLIB tries at once (as fast as one request), in order of how likely they are:
            // artist/title guesses first, then free text (the whole cleaned name, and a shorter one)
            val tries: List<Pair<String, suspend () -> List<LrclibDto>>> =
                guesses.map { guess ->
                    "${guess.artist} ${guess.title}" to suspend {
                        lrclibService.search(guess.title, guess.artist.ifBlank { null })
                    }
                } + SongNameCleaner.freeTexts(rawTitle, artistTag).map { text ->
                    text to suspend { lrclibService.searchText(text) }
                }
            val results = coroutineScope {
                tries.map { (_, request) -> async { runCatching { request() }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }
            // the first convincing match, in the tries' order
            tries.zip(results).firstNotNullOfOrNull { (attempt, found) ->
                bestMatch(found, attempt.first, durationMs)
            }?.let { return@runCatching it.lyrics }

            // nothing on LRCLIB: plain lyrics from lyrics.ovh with the best guess
            val guess = guesses.firstOrNull() ?: error("No lyrics found")
            fromLyricsOvh(guess.artist, guess.title)
        }

    override suspend fun searchLyrics(
        artist: String,
        title: String,
        durationMs: Long,
    ): Result<List<LyricsCandidate>> = runCatching {
        val searched = "$artist $title"
        // both at once: by fields, and free text (finds songs typed the other way round too)
        val results = coroutineScope {
            val byFields = async {
                if (title.isBlank()) emptyList()
                else runCatching { lrclibService.search(title, artist.ifBlank { null }) }.getOrDefault(emptyList())
            }
            val byText = async {
                if (searched.isBlank()) emptyList()
                else runCatching { lrclibService.searchText(searched.trim()) }.getOrDefault(emptyList())
            }
            byFields.await() + byText.await()
        }.distinctBy { it.id ?: "${it.artistName}|${it.trackName}" }

        val candidates = rank(results, searched, durationMs).take(10).map { it.second }
        candidates.ifEmpty {
            // nothing on LRCLIB: maybe lyrics.ovh has plain lyrics
            listOf(
                LyricsCandidate(
                    title = title,
                    artist = artist,
                    durationMs = null,
                    lyrics = fromLyricsOvh(artist, title),
                    sameLength = false,
                )
            )
        }
    }

    /** the best result if it's convincing (right length or clearly the right name), else null */
    private fun bestMatch(results: List<LrclibDto>, searched: String, durationMs: Long): LyricsCandidate? =
        rank(results, searched, durationMs).firstOrNull { (score, candidate) ->
            candidate.sameLength || score.closeLength || score.nameMatch >= NAME_MATCH
        }?.second

    private data class Score(val closeLength: Boolean, val nameMatch: Double)

    /** usable results, best first: same length, then synced, then closest name */
    private fun rank(results: List<LrclibDto>, searched: String, durationMs: Long): List<Pair<Score, LyricsCandidate>> {
        val searchedWords = SongNameCleaner.words(searched)
        val songSeconds = durationMs / 1000.0
        return results
            .filter { it.instrumental != true && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()) }
            .map { dto ->
                val lengthDiff = if (durationMs > 0 && dto.duration != null) abs(dto.duration - songSeconds) else null
                val resultWords = SongNameCleaner.words("${dto.artistName.orEmpty()} ${dto.trackName.orEmpty()}")
                val nameMatch = if (resultWords.isEmpty()) 0.0
                else resultWords.count { it in searchedWords }.toDouble() / resultWords.size
                val score = Score(closeLength = lengthDiff != null && lengthDiff <= CLOSE_LENGTH_SECONDS, nameMatch = nameMatch)
                score to dto.toCandidate(sameLength = lengthDiff != null && lengthDiff <= SAME_LENGTH_SECONDS)
            }
            .sortedWith(
                compareByDescending<Pair<Score, LyricsCandidate>> { it.second.sameLength }
                    .thenByDescending { it.first.closeLength }
                    .thenByDescending { it.second.isSynced }
                    .thenByDescending { it.first.nameMatch }
            )
    }

    private fun LrclibDto.toCandidate(sameLength: Boolean): LyricsCandidate {
        val synced = syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = plainLyrics?.takeIf { it.isNotBlank() } ?: synced?.let(::lrcToPlainText).orEmpty()
        return LyricsCandidate(
            title = trackName.orEmpty(),
            artist = artistName.orEmpty(),
            durationMs = duration?.let { (it * 1000).toLong() },
            lyrics = LyricsDomain(lyrics = plain, synced = synced),
            sameLength = sameLength,
        )
    }

    private suspend fun fromLyricsOvh(artist: String, title: String): LyricsDomain {
        val body = lyricsService.getLyrics(artist, title).body()
        return body?.toDomain()?.takeIf { it.lyrics.isNotBlank() } ?: error("No lyrics found")
    }

    override fun observeSavedLyrics(songId: Long): Flow<LyricsDomain?> =
        lyricsDao.observeLyrics(songId).map { entity ->
            entity?.let { LyricsDomain(lyrics = it.lyrics, synced = it.synced) }
        }

    override suspend fun saveLyrics(songId: Long, lyrics: LyricsDomain) =
        lyricsDao.save(LyricsEntity(songId, lyrics.lyrics, lyrics.synced))
}
