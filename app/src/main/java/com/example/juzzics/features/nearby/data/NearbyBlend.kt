package com.example.juzzics.features.nearby.data

import com.example.juzzics.features.lyrics.domain.util.SongNameCleaner
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.nearby.domain.Blend
import com.example.juzzics.features.nearby.domain.BlendItem
import com.example.juzzics.features.nearby.domain.ConnectedFriend
import com.example.juzzics.features.nearby.domain.RemoteSong
import com.example.juzzics.features.nearby.domain.SharedSong
import com.example.juzzics.features.nearby.domain.TasteMatch
import com.example.juzzics.features.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Mixes this phone's songs with connected friends'.
 * Each song's name is cleaned once and looked up by it (not every song against every other:
 * 600 × 600 songs would freeze the phone). Run it off the main thread.
 */
object BlendMaker {

    /** a song with its name cleaned once, for comparing */
    private class Keyed(val song: RemoteSong) {
        val title = simple(SongNameCleaner.clean(song.title))
        val artist = simple(song.artist)
    }

    fun make(mine: List<RemoteSong>, friends: List<ConnectedFriend>, seed: Int, size: Int = MIX_SIZE): Blend {
        val random = Random(seed)
        val myKeyed = mine.map(::Keyed)
        val myByTitle = myKeyed.filter { it.title.isNotEmpty() }.groupBy { it.title }
        val friendsKeyed = friends.filter { it.library.isNotEmpty() }.map { friend -> friend to friend.library.map(::Keyed) }
        val friendsByTitle = friendsKeyed.map { (friend, songs) ->
            friend.name to songs.filter { it.title.isNotEmpty() }.groupBy { it.title }
        }

        /** the same song on this phone (then it plays from here: nothing to send) */
        fun mineToo(song: Keyed): Keyed? = myByTitle[song.title]?.firstOrNull { sameArtist(it, song) }

        val shared = myKeyed.mapNotNull { song ->
            if (song.title.isEmpty()) return@mapNotNull null
            val with = friendsByTitle.filter { (_, byTitle) -> byTitle[song.title]?.any { sameArtist(it, song) } == true }
            if (with.isEmpty()) null else SharedSong(song.song, with.map { it.first })
        }

        val pools = buildList {
            add(myKeyed.shuffled(random).map { it to BlendItem(null, "You", it.song) })
            friendsKeyed.forEach { (friend, songs) ->
                add(songs.shuffled(random).map { song ->
                    val mine = mineToo(song)
                    if (mine != null) mine to BlendItem(null, "You", mine.song)
                    else song to BlendItem(friend.endpointId, friend.name, song.song)
                })
            }
        }.map { it.toMutableList() }

        // one song per person in turn, each song once
        val mix = mutableListOf<BlendItem>()
        val used = HashSet<String>()
        while (mix.size < size && pools.any { it.isNotEmpty() }) {
            for (pool in pools) {
                if (mix.size >= size) break
                while (pool.isNotEmpty()) {
                    val (keyed, item) = pool.removeAt(pool.lastIndex)
                    val key = if (keyed.title.isEmpty()) "id:${item.ownerId}:${item.song.id}" else "${keyed.title}|${keyed.artist}"
                    if (used.add(key)) {
                        mix += item
                        break
                    }
                }
            }
        }
        return Blend(
            people = listOf("You") + friendsKeyed.map { it.first.name },
            mix = mix,
            shared = shared,
            matches = friendsKeyed.map { (friend, songs) -> tasteMatch(friend.name, myKeyed, myByTitle, songs) },
        )
    }

    /** songs you both have + artists you both have, relative to the smaller collection */
    private fun tasteMatch(
        friendName: String,
        mine: List<Keyed>,
        myByTitle: Map<String, List<Keyed>>,
        theirs: List<Keyed>,
    ): TasteMatch {
        val sharedSongs = theirs.count { song -> song.title.isNotEmpty() && myByTitle[song.title]?.any { sameArtist(it, song) } == true }
        val myArtists = artistCounts(mine)
        val theirArtists = artistCounts(theirs)
        val sharedArtistKeys = myArtists.keys intersect theirArtists.keys

        val songPart = if (mine.isEmpty() || theirs.isEmpty()) 0.0
        else sharedSongs.toDouble() / minOf(mine.size, theirs.size)
        val artistPart = if (myArtists.isEmpty() || theirArtists.isEmpty()) 0.0
        else sharedArtistKeys.size.toDouble() / minOf(myArtists.size, theirArtists.size)
        // artists weigh a bit more: the same taste doesn't need the exact same songs
        val percent = ((songPart * 0.4 + artistPart * 0.6) * 100).toInt().coerceIn(0, 100)

        return TasteMatch(
            friendName = friendName,
            percent = percent,
            sharedSongs = sharedSongs,
            sharedArtists = sharedArtistKeys
                .sortedByDescending { (myArtists[it]?.second ?: 0) + (theirArtists[it]?.second ?: 0) }
                .take(3)
                .mapNotNull { myArtists[it]?.first },
            youBring = myArtists.filterKeys { it !in theirArtists }.maxByOrNull { it.value.second }?.value?.first,
            theyBring = theirArtists.filterKeys { it !in myArtists }.maxByOrNull { it.value.second }?.value?.first,
        )
    }

    /** artist (compared simplified) to its shown name and how many songs */
    private fun artistCounts(songs: List<Keyed>): Map<String, Pair<String, Int>> =
        songs.filter { it.artist.isNotEmpty() && it.artist != "unknown" }
            .groupBy { it.artist }
            .mapValues { (_, list) -> list.first().song.artist.trim() to list.size }

    /** a missing artist matches any */
    private fun sameArtist(a: Keyed, b: Keyed): Boolean =
        a.artist.isEmpty() || b.artist.isEmpty() || a.artist == b.artist

    private fun simple(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private const val MIX_SIZE = 60
}

/**
 * Plays a blend: this phone's songs directly, friends' songs streamed in one at a time
 * (the current one, and the next while the current plays), so the mix starts right away
 * without sending everything.
 */
class NearbyBlendPlayer(
    private val player: PlayerController,
    scope: CoroutineScope,
    /** asks a friend to stream one of their songs here, answered with [onSongReady] / [onSongFailed] and [key] */
    private val requestSong: (endpointId: String, songId: Long, key: Long) -> Unit,
    private val findMine: suspend (songId: Long) -> MusicFileDomain?,
    /** runs a suspending job (this phone's songs are looked up from the library) */
    private val runAsync: (suspend () -> Unit) -> Unit,
) {
    private var items: List<BlendItem> = emptyList()
    /** a new blend ignores songs still arriving for the old one */
    private var session = 0L
    /** blend position to the player's song id */
    private val loaded = mutableMapOf<Int, Long>()
    private val requested = mutableSetOf<Int>()
    private var current = -1

    init {
        // the player moved on to the next blend song: fetch the one after
        scope.launch {
            player.state.map { it.currentSong?.id }.distinctUntilChanged().collect { id ->
                val index = loaded.entries.firstOrNull { it.value == id }?.key ?: return@collect
                if (index != current) {
                    current = index
                    load(index + 1)
                }
            }
        }
    }

    fun play(blend: List<BlendItem>, start: Int) {
        session++
        items = blend
        loaded.clear()
        requested.clear()
        current = start
        load(start)
    }

    /** a friend's song arrived (enough of it to play) */
    fun onSongReady(key: Long, song: MusicFileDomain) {
        if (key / KEYS_PER_SESSION != session) return
        place((key % KEYS_PER_SESSION).toInt(), song)
    }

    /** a friend couldn't send one: skip it */
    fun onSongFailed(key: Long) {
        if (key / KEYS_PER_SESSION != session) return
        val index = (key % KEYS_PER_SESSION).toInt()
        if (index == current) {
            current = index + 1
            load(index + 1)
        } else load(index + 1)
    }

    private fun load(index: Int) {
        val item = items.getOrNull(index) ?: return
        if (!requested.add(index)) return
        val owner = item.ownerId
        if (owner == null) {
            runAsync {
                val song = findMine(item.song.id)
                if (song != null) place(index, song) else onSongFailed(session * KEYS_PER_SESSION + index)
            }
        } else {
            requestSong(owner, item.song.id, session * KEYS_PER_SESSION + index)
        }
    }

    private fun place(index: Int, song: MusicFileDomain) {
        loaded[index] = song.id
        if (index == current) {
            player.playQueue(listOf(song), 0, source = "Blend")
            // only now the next one: queued after this one, never before it
            load(index + 1)
        } else {
            player.addToQueue(song)
        }
    }

    private companion object {
        const val KEYS_PER_SESSION = 10_000L
    }
}
