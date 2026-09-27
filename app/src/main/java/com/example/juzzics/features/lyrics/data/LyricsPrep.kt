package com.example.juzzics.features.lyrics.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.juzzics.common.songs.SongSettings
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import com.example.juzzics.features.lyrics.domain.util.suggestName
import com.example.juzzics.features.musics.domain.repo.MusicRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** How many songs have lyrics saved (so they work offline). */
data class LyricsCoverage(val withLyrics: Int, val total: Int)

/** Progress of [LyricsPrep]. */
data class LyricsPrepState(
    val running: Boolean = false,
    /** songs without lyrics when it started */
    val total: Int = 0,
    val done: Int = 0,
    val found: Int = 0,
    /** how it ended (or why it couldn't start), shown until dismissed */
    val message: String? = null,
)

/**
 * "Trip prep": while there's internet, looks up lyrics for every song that has none yet and
 * saves them, so they're there offline (in the mountains...). Lookups with a sure match also
 * suggest a clean name for songs with messy ones (Home > Fix song names).
 *
 * App-wide, so it keeps going while you use the rest of the app.
 */
class LyricsPrep(
    private val context: Context,
    private val musicRepo: MusicRepo,
    private val lyricsRepo: LyricsRepo,
    private val songSettings: SongSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LyricsPrepState())
    val state: StateFlow<LyricsPrepState> = _state.asStateFlow()

    private var job: Job? = null

    /** why it stopped early (shown instead of the usual summary) */
    @Volatile
    private var stopReason: String? = null

    fun start() {
        if (job?.isActive == true) return
        if (!isOnline()) {
            _state.value = LyricsPrepState(message = "Connect to the internet first, then start trip prep.")
            return
        }
        stopReason = null
        _state.value = LyricsPrepState(running = true)
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            val songs = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).filter { it.id > 0 }
            val withLyrics = lyricsRepo.songIdsWithLyrics()
            val missing = songs.filter { it.id !in withLyrics }
            _state.update { it.copy(total = missing.size) }

            // a few songs at a time: each lookup already sends several requests at once
            val permits = Semaphore(PARALLEL_SONGS)
            coroutineScope {
                missing.forEach { song ->
                    launch {
                        permits.withPermit {
                            val title = song.title.orEmpty()
                            val artist = song.artist?.takeUnless { it.isBlank() || it == "<unknown>" }.orEmpty()
                            val lyrics = if (title.isBlank()) null
                            else lyricsRepo.findLyrics(title, artist, song.duration).getOrNull()
                            if (lyrics != null) {
                                lyricsRepo.saveLyrics(song.id, lyrics)
                                suggestName(song.id, title, artist, lyrics.match)?.let(songSettings::suggest)
                            } else if (!isOnline()) {
                                stopReason = "The internet went away."
                                job?.cancel()
                            }
                            _state.update { it.copy(done = it.done + 1, found = it.found + if (lyrics != null) 1 else 0) }
                        }
                    }
                }
            }
        }
        job = newJob
        newJob.start()
        newJob.invokeOnCompletion { cause ->
            _state.update { state ->
                val summary = when {
                    state.total == 0 -> "All your songs already have lyrics."
                    cause != null -> "${stopReason ?: "Stopped."} ${state.found} of ${state.done} songs got lyrics."
                    else -> "Done: ${state.found} of ${state.total} songs got lyrics." +
                            if (state.found < state.total) " The others can be searched by hand." else ""
                }
                state.copy(running = false, message = summary)
            }
        }
    }

    /** songs with lyrics saved, of all songs */
    suspend fun coverage(): LyricsCoverage {
        val songs = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).filter { it.id > 0 }
        val withLyrics = lyricsRepo.songIdsWithLyrics()
        return LyricsCoverage(withLyrics = songs.count { it.id in withLyrics }, total = songs.size)
    }

    fun stop() {
        job?.cancel()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val PARALLEL_SONGS = 2
    }
}
