package com.example.juzzics.features.player

import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.content.ContentUris
import android.net.Uri
import java.io.File
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.juzzics.features.home.domain.usecase.RecordPlayUseCase
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.player.widget.JuzzicsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * App-wide access to the player running in [PlaybackService].
 * ViewModels call its methods and observe [state] / [progress]; it also records plays in history.
 */
class PlayerController(
    private val context: Context,
    private val recordPlay: RecordPlayUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** position of the current song, updated 4 times a second (smooth enough for synced lyrics) */
    val progress: Flow<PlaybackProgress> = flow {
        while (true) {
            controller?.let {
                emit(PlaybackProgress(it.currentPosition, it.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0))
            }
            delay(250)
        }
    }.distinctUntilChanged()

    /** full song info by mediaId, for songs we queued (MediaItems only carry title/artist/art) */
    private val knownSongs = mutableMapOf<String, MusicFileDomain>()

    private val memory = PlaybackMemory(context)

    private var controller: MediaController? = null
    private val connected = CompletableDeferred<MediaController>()
    private var playRequest = 0
    private var queueSource: String? = null

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    restoreLastSession(mediaController)
                    publishState()
                    connected.complete(mediaController)
                }
                .onFailure { it.printStackTrace() }
        }, ContextCompat.getMainExecutor(context))
    }

    // ---------------------- commands ----------------------

    /**
     * replaces the queue with [songs] and plays from [startIndex].
     * [source]: where they come from (a playlist's name...), shown as "Playing from"
     */
    fun playQueue(songs: List<MusicFileDomain>, startIndex: Int, source: String? = null) = withController {
        if (songs.isEmpty()) return@withController
        songs.forEach { knownSongs[it.mediaId] = it }
        playRequest++
        queueSource = source
        it.setMediaItems(songs.map { song -> song.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        it.prepare()
        it.play()
    }

    fun playQueueIndex(index: Int) = withController {
        if (index !in 0 until it.mediaItemCount) return@withController
        it.seekToDefaultPosition(index)
        it.recoverAndPlay()
    }

    fun togglePlayPause() = withController {
        if (it.isPlaying) it.pause() else it.recoverAndPlay()
    }

    fun pause() = withController { it.pause() }

    fun next() = withController {
        if (it.hasNextMediaItem()) {
            it.seekToNextMediaItem()
            it.recoverAndPlay()
        }
    }

    fun previous() = withController {
        if (it.hasPreviousMediaItem()) {
            it.seekToPreviousMediaItem()
            it.recoverAndPlay()
        }
    }

    fun seekToMs(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

    /**
     * Plays [song] right after the current one. If it's already in the queue it's moved there
     * (the queue never holds a song twice). With an empty queue it just plays it.
     */
    fun playNext(song: MusicFileDomain) = withController {
        if (it.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return@withController
        }
        knownSongs[song.mediaId] = song
        val existing = it.indexOf(song.mediaId)
        if (existing == it.currentMediaItemIndex) return@withController
        if (existing >= 0) {
            val target = if (existing < it.currentMediaItemIndex) it.currentMediaItemIndex else it.currentMediaItemIndex + 1
            it.moveMediaItem(existing, target)
        } else {
            it.addMediaItem(it.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    /** adds [song] to the end of the queue (moves it there if it's already queued) */
    fun addToQueue(song: MusicFileDomain) = withController {
        if (it.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return@withController
        }
        knownSongs[song.mediaId] = song
        val existing = it.indexOf(song.mediaId)
        if (existing == it.currentMediaItemIndex) return@withController
        if (existing >= 0) it.moveMediaItem(existing, it.mediaItemCount - 1)
        else it.addMediaItem(song.toMediaItem())
    }

    /** puts the queue in the order of [songIds] (after a drag in the "Up next" sheet) */
    fun reorderQueue(songIds: List<Long>) = withController {
        songIds.forEachIndexed { target, id ->
            val current = it.indexOf(id.toString())
            if (current >= 0 && current != target) it.moveMediaItem(current, target)
        }
    }

    /** removes a song from the queue (not the one playing) */
    fun removeFromQueue(index: Int) = withController {
        if (index in 0 until it.mediaItemCount && index != it.currentMediaItemIndex) it.removeMediaItem(index)
    }

    /** [fraction] 0..1 of the current song */
    fun seekTo(fraction: Float) = withController {
        val duration = it.duration
        if (duration != C.TIME_UNSET && duration > 0) it.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** off -> all -> one -> off */
    fun cycleRepeatMode() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------------------- internals ----------------------

    /** the app was closed: bring back the last queue, song and position (paused) */
    private fun restoreLastSession(c: MediaController) {
        if (c.mediaItemCount > 0) return // the service is still playing: nothing to restore
        val saved = memory.load() ?: return
        saved.queue.forEach { knownSongs[it.mediaId] = it }
        queueSource = saved.source
        c.setMediaItems(saved.queue.map { it.toMediaItem() }, saved.index, saved.positionMs)
        c.prepare()
    }

    private fun withController(block: (MediaController) -> Unit) {
        val ready = controller
        if (ready != null) block(ready) else scope.launch { block(connected.await()) }
    }

    /** after an error (e.g. a file that can't be played) the player needs prepare() again */
    private fun MediaController.recoverAndPlay() {
        if (playerError != null) prepare()
        play()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED)
            publishState(queueChanged)
            if (queueChanged) memory.saveQueue(queue, queueSource)
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                memory.savePosition(player.currentMediaItemIndex, player.currentPosition)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.let { knownSongs[it.mediaId] ?: it.toSong() } ?: return
            if (song.id < 0) return // a friend's song (or a fake one): not in the history
            scope.launch { runCatching { recordPlay(song) } }
        }

        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            publishState()
        }
    }

    private var widgetSongId: Long? = null
    private var widgetIsPlaying = false

    /** home-screen widget shows the current song + play state */
    private fun updateWidgetIfNeeded(state: PlayerState) {
        val songId = state.currentSong?.id
        if (songId == widgetSongId && state.isPlaying == widgetIsPlaying) return
        widgetSongId = songId
        widgetIsPlaying = state.isPlaying
        scope.launch { runCatching { JuzzicsWidget().updateAll(context) } }
    }

    /** last built queue, reused while the playlist doesn't change (keeps the same list instance) */
    private var queue: List<MusicFileDomain> = emptyList()

    private fun publishState(queueChanged: Boolean = true) {
        val c = controller ?: return
        if (queueChanged || queue.size != c.mediaItemCount) {
            queue = (0 until c.mediaItemCount).map { i ->
                c.getMediaItemAt(i).let { knownSongs[it.mediaId] ?: it.toSong() }
            }
        }
        _state.value = PlayerState(
            currentSong = c.currentMediaItem?.let { knownSongs[it.mediaId] ?: it.toSong() },
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            playRequest = playRequest,
            queueSource = queueSource,
        )
        updateWidgetIfNeeded(_state.value)
    }

    private val MusicFileDomain.mediaId get() = id.toString()

    private fun Player.indexOf(mediaId: String): Int =
        (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).mediaId == mediaId } ?: -1

    private fun MusicFileDomain.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playableUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(data)
                .setArtworkUri(icon)
                .build()
        )
        .build()

    /**
     * the phone's own songs play from the media library; songs a friend sent over Nearby
     * (negative id) play from the file they were saved to
     */
    private fun MusicFileDomain.playableUri(): Uri {
        val path = data
        return if (id < 0 && path != null && path.startsWith("/")) File(path).toUri()
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    /** for items queued before the app was restarted (only what the MediaItem carries) */
    private fun MediaItem.toSong() = MusicFileDomain(
        id = mediaId.toLongOrNull() ?: 0,
        title = mediaMetadata.title?.toString(),
        artist = mediaMetadata.artist?.toString(),
        data = mediaMetadata.albumTitle?.toString(),
        duration = 0,
        icon = mediaMetadata.artworkUri ?: "".toUri(),
    )
}
