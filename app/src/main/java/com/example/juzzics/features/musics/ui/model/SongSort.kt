package com.example.juzzics.features.musics.ui.model

enum class SongSort(val label: String) {
    CUSTOM("Your order"),
    TITLE("Title"),
    ARTIST("Artist"),
    RECENTLY_ADDED("Recently added"),
}

/** What the Library shows: all songs, albums / artists (then the songs of one), or playlists. */
enum class BrowseTab(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

val MusicFileUi.albumName: String get() = data?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown album"
val MusicFileUi.artistName: String get() = knownArtist.ifEmpty { "Unknown artist" }

/** An album or artist with its songs (in list order). */
data class SongGroup(val name: String, val songs: List<MusicFileUi>)

/** albums or artists of these songs, filtered by [query], by name */
fun List<MusicFileUi>.groups(tab: BrowseTab, query: String): List<SongGroup> {
    val key: (MusicFileUi) -> String = when (tab) {
        BrowseTab.ALBUMS -> { song -> song.albumName }
        else -> { song -> song.artistName }
    }
    return groupBy(key)
        .map { (name, songs) -> SongGroup(name, songs) }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
}

/**
 * Songs to show: optionally only those of album/artist [group] (in [tab]), then filtered by
 * search [query] and sorted by [sort]. [SongSort.CUSTOM] keeps the list's own order.
 */
fun List<MusicFileUi>.visibleSongs(
    query: String,
    sort: SongSort,
    tab: BrowseTab = BrowseTab.SONGS,
    group: String? = null,
): List<MusicFileUi> {
    val inGroup = when {
        group == null || tab == BrowseTab.SONGS -> this
        tab == BrowseTab.ALBUMS -> filter { it.albumName == group }
        else -> filter { it.artistName == group }
    }
    val q = query.trim()
    // inside an album/artist the search box searches its groups, not its songs
    val filtered = if (q.isEmpty() || group != null) inGroup else inGroup.filter {
        it.title.orEmpty().contains(q, ignoreCase = true) ||
                it.artist.orEmpty().contains(q, ignoreCase = true)
    }
    return when (sort) {
        SongSort.CUSTOM -> filtered
        SongSort.TITLE -> filtered.sortedBy { it.title.orEmpty().lowercase() }
        SongSort.ARTIST -> filtered.sortedWith(
            compareBy({ it.knownArtist.ifEmpty { "\uFFFF" } /* unknown artists last */.lowercase() }, { it.title.orEmpty().lowercase() })
        )
        SongSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
    }
}

/** Drag-to-reorder only makes sense on the full list in your own order. */
fun canReorder(query: String, sort: SongSort, tab: BrowseTab = BrowseTab.SONGS) =
    query.isBlank() && sort == SongSort.CUSTOM && tab == BrowseTab.SONGS
