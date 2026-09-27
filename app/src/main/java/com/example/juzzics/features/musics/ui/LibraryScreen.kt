package com.example.juzzics.features.musics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.LOADING
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.valueOf
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.uiComponents.PageHeader
import com.example.juzzics.common.uiComponents.ScreenHeader
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.components.MusicListItem
import com.example.juzzics.features.musics.ui.components.SongGroupList
import com.example.juzzics.features.musics.ui.components.SongMenuActions
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.SongSort
import com.example.juzzics.features.musics.ui.model.canReorder
import com.example.juzzics.features.musics.ui.model.groups
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.playlists.ui.PlaylistsContent
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import kotlinx.coroutines.flow.Flow

/**
 * The Library tab: Songs · Albums · Artists · Playlists, with search and sort.
 * An opened album / artist / playlist takes the whole screen (back returns).
 */
@Composable
fun LibraryScreen(
    states: BaseState,
    uiEvent: Flow<UiEvent>,
    onAction: (Action) -> Unit,
    playlistStates: BaseState,
    onPlaylistAction: (Action) -> Unit,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    with2(states, MusicVM) {
        val lazyListState = rememberLazyListState()
        uiEvent.BaseHandler(
            loading = LOADING(),
            onEvent = { if (it is MusicVM.ScrollToPositionUiEvent) lazyListState.animateScrollToItem(it.position) },
        ) {
            val tab = BROWSE_TAB()
            val group = BROWSE_GROUP()
            val playlistOpen = playlistStates.valueOf(PlaylistsVM.SELECTED_PLAYLIST_ID) != null
            // an opened album / artist / playlist gets the whole screen
            val focused = (group != null && tab != BrowseTab.SONGS) || (tab == BrowseTab.PLAYLISTS && playlistOpen)
            val songCount = MUSIC_LIST().size

            Column(Modifier.fillMaxSize()) {
                if (!focused) {
                    ScreenHeader(
                        title = "Library",
                        subtitle = if (songCount > 0) "$songCount songs on this phone" else null,
                        actions = {
                            if (tab == BrowseTab.SONGS) {
                                SortMenu(SORT(), onSort = { onAction(MusicVM.SortAction(it)) })
                            }
                        }
                    )
                    if (tab != BrowseTab.PLAYLISTS) {
                        SearchField(
                            query = !SEARCH_QUERY,
                            placeholder = when (tab) {
                                BrowseTab.ALBUMS -> "Search albums"
                                BrowseTab.ARTISTS -> "Search artists"
                                else -> "Search songs and artists"
                            },
                            onQueryChange = { onAction(MusicVM.SearchAction(it)) }
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrowseTab.entries.forEach { option ->
                            FilterChip(
                                selected = option == tab,
                                onClick = { onAction(MusicVM.BrowseTabAction(option)) },
                                label = { Text(option.label) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    when (tab) {
                        BrowseTab.PLAYLISTS -> PlaylistsContent(states = playlistStates, onAction = onPlaylistAction)
                        BrowseTab.SONGS -> SongsTab(states, lazyListState, onAction, onAddToPlaylist)
                        else -> GroupsTab(states, tab, lazyListState, onAction, onAddToPlaylist)
                    }
                }
            }
        }
    }
}

/** all songs, or the playing playlist's songs ("Playing from …") */
@Composable
private fun SongsTab(
    states: BaseState,
    lazyListState: LazyListState,
    onAction: (Action) -> Unit,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    with2(states, MusicVM) {
        val musicList = MUSIC_LIST()
        val query = !SEARCH_QUERY
        val sort = SORT()
        val playingFrom = PLAYING_FROM()

        if (playingFrom != null && query.isBlank()) {
            PlayingFrom(states, playingFrom, lazyListState, onAction, onAddToPlaylist)
            return@with2
        }
        if (musicList.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = "No music on this phone yet",
                message = "Songs you download or copy to the phone show up here"
            )
            return@with2
        }
        val visible = remember(musicList, query, sort) {
            musicList.visibleSongs(query, sort).toMutableStateList()
        }
        if (visible.isEmpty()) {
            EmptyState(icon = Icons.Filled.SearchOff, title = "Nothing matches \"$query\"")
            return@with2
        }
        SongList(
            songs = visible,
            states = states,
            lazyListState = lazyListState,
            reorderEnabled = canReorder(query, sort),
            onAction = onAction,
            onAddToPlaylist = onAddToPlaylist,
            onPlay = { onAction(MusicVM.PlayMusicAction(it)) },
        )
    }
}

/** albums / artists; an opened one shows its songs */
@Composable
private fun GroupsTab(
    states: BaseState,
    tab: BrowseTab,
    lazyListState: LazyListState,
    onAction: (Action) -> Unit,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    with2(states, MusicVM) {
        val musicList = MUSIC_LIST()
        val query = !SEARCH_QUERY
        val group = BROWSE_GROUP()
        if (group == null) {
            SongGroupList(
                groups = remember(musicList, tab, query) { musicList.groups(tab, query) },
                tab = tab,
                onOpen = { onAction(MusicVM.OpenGroupAction(it)) },
            )
            return@with2
        }
        BackHandler { onAction(MusicVM.OpenGroupAction(null)) }
        val songs = remember(musicList, tab, group) {
            musicList.visibleSongs("", SongSort.CUSTOM, tab, group).toMutableStateList()
        }
        Column {
            PageHeader(
                title = group,
                subtitle = "${songs.size} song${if (songs.size == 1) "" else "s"}",
                onBack = { onAction(MusicVM.OpenGroupAction(null)) },
                actions = {
                    IconButton(onClick = { songs.firstOrNull()?.let { onAction(MusicVM.PlayMusicAction(it)) } }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                    }
                }
            )
            SongList(
                songs = songs,
                states = states,
                lazyListState = lazyListState,
                reorderEnabled = false,
                onAction = onAction,
                onAddToPlaylist = onAddToPlaylist,
                onPlay = { onAction(MusicVM.PlayMusicAction(it)) },
            )
        }
    }
}

/** a playlist (etc.) is playing: its songs, in queue order, instead of all songs */
@Composable
private fun PlayingFrom(
    states: BaseState,
    source: String,
    lazyListState: LazyListState,
    onAction: (Action) -> Unit,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    with2(states, MusicVM) {
        val queue = QUEUE()
        val currentIndex = QUEUE_INDEX()
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Playing from",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(source, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                IconButton(onClick = { onAction(MusicVM.ShowAllSongsAction) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Show all songs")
                }
            }
            val songs = remember(queue, currentIndex) {
                queue.mapIndexed { index, song -> song.copy(isPlaying = index == currentIndex) }.toMutableStateList()
            }
            SongList(
                songs = songs,
                states = states,
                lazyListState = lazyListState,
                reorderEnabled = true,
                onAction = onAction,
                onAddToPlaylist = onAddToPlaylist,
                onPlay = { song ->
                    val index = queue.indexOfFirst { it.id == song.id }
                    if (index >= 0) onAction(MusicVM.PlayQueueIndexAction(index))
                },
                onReorder = { reordered -> onAction(MusicVM.ReorderQueueAction(reordered.map { it.id })) },
            )
        }
    }
}

@Composable
private fun SongList(
    songs: androidx.compose.runtime.snapshots.SnapshotStateList<MusicFileUi>,
    states: BaseState,
    lazyListState: LazyListState,
    reorderEnabled: Boolean,
    onAction: (Action) -> Unit,
    onAddToPlaylist: (MusicFileUi) -> Unit,
    onPlay: (MusicFileUi) -> Unit,
    onReorder: ((List<MusicFileUi>) -> Unit)? = null,
) {
    with2(states, MusicVM) {
        val likedIds = LIKED_IDS()
        ReorderableList(
            modifier = Modifier.fillMaxSize(),
            list = songs,
            key = { it.id },
            lazyListState = lazyListState,
            reorderEnabled = reorderEnabled,
            onDragEnd = { reordered -> onReorder?.invoke(reordered) ?: onAction(MusicVM.OnDragEndAction(reordered)) },
            onClick = onPlay,
        ) { song ->
            MusicListItem(
                musicFile = song,
                isLiked = song.id in likedIds,
                menu = SongMenuActions(
                    onPlayNext = { onAction(MusicVM.PlayNextAfterCurrentAction(song)) },
                    onAddToQueue = { onAction(MusicVM.AddToQueueAction(song)) },
                    onToggleLike = { onAction(MusicVM.ToggleLikeAction(song)) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** a rounded search field without the heavy outline */
@Composable
private fun SearchField(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SortMenu(sort: SongSort, onSort: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort songs") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        onSort(option)
                        open = false
                    }
                )
            }
        }
    }
}
