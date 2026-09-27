package com.example.juzzics.features.musics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.base.viewModel.stateValue
import com.example.juzzics.common.uiComponents.EmptyState
import com.example.juzzics.common.uiComponents.dragable.ReorderableList
import com.example.juzzics.features.musics.ui.components.MusicListHeader
import com.example.juzzics.features.musics.ui.components.MusicListItem
import com.example.juzzics.features.musics.ui.components.SongGroupHeader
import com.example.juzzics.features.musics.ui.components.SongGroupList
import com.example.juzzics.features.musics.ui.components.SongMenuActions
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.canReorder
import com.example.juzzics.features.musics.ui.model.groups
import com.example.juzzics.features.musics.ui.model.visibleSongs
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FIRST
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.FOURTH
import com.example.juzzics.features.musics.ui.vm.MusicVM.MotionScenes.THIRD

/**
 * Song list with search, sort and Songs / Albums / Artists tabs. Its own Composable, so typing
 * a search or changing the list doesn't redraw the player around it.
 */
@Composable
fun MusicListSection(
    states: BaseState,
    lazyListState: LazyListState,
    onAction: (Action) -> Unit,
    onAddToPlaylist: ((MusicFileUi) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    with2(states, MusicVM) {
        val sceneName = SCENE_NAME()
        val likedIds = LIKED_IDS()
        val query = !SEARCH_QUERY
        val sort = SORT()
        val tab = BROWSE_TAB()
        val group = BROWSE_GROUP()
        val musicList = MUSIC_LIST()
        val visible = remember(musicList, query, sort, tab, group) {
            musicList.visibleSongs(query, sort, tab, group).toMutableStateList()
        }

        val playingFrom = PLAYING_FROM()
        if (playingFrom != null) {
            PlayingFromList(
                source = playingFrom,
                queue = QUEUE(),
                currentIndex = QUEUE_INDEX(),
                likedIds = likedIds,
                lazyListState = lazyListState,
                onAction = onAction,
                onAddToPlaylist = onAddToPlaylist,
                modifier = modifier,
            )
            return@with2
        }

        Column(modifier) {
            MusicListHeader(
                query = query,
                sort = sort,
                tab = tab,
                onQueryChange = { onAction(MusicVM.SearchAction(it)) },
                onSortChange = { onAction(MusicVM.SortAction(it)) },
                onTabChange = { onAction(MusicVM.BrowseTabAction(it)) },
            )
            when {
                musicList.isEmpty() -> EmptyState(
                    icon = Icons.Filled.LibraryMusic,
                    title = "No music found",
                    message = "Songs stored on this device show up here",
                    modifier = Modifier.weight(1f)
                )

                tab != BrowseTab.SONGS && group == null -> SongGroupList(
                    groups = remember(musicList, tab, query) { musicList.groups(tab, query) },
                    tab = tab,
                    onOpen = { onAction(MusicVM.OpenGroupAction(it)) },
                    modifier = Modifier.weight(1f)
                )

                else -> {
                    if (group != null) {
                        BackHandler(sceneName == FIRST) { onAction(MusicVM.OpenGroupAction(null)) }
                        SongGroupHeader(
                            name = group,
                            onBack = { onAction(MusicVM.OpenGroupAction(null)) },
                            onPlayAll = { visible.firstOrNull()?.let { onAction(MusicVM.PlayMusicAction(it)) } }
                        )
                    }
                    if (visible.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = "No songs match \"$query\"",
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ReorderableList(
                            modifier = Modifier.weight(1f),
                            list = visible,
                            key = { it.id },
                            lazyListState = lazyListState,
                            reorderEnabled = canReorder(query, sort, tab),
                            onDragEnd = { onAction(MusicVM.OnDragEndAction(it)) },
                            onClick = {
                                if (SCENE_NAME.stateValue() !in listOf(THIRD, FOURTH))
                                    onAction(MusicVM.PlayMusicAction(it))
                            }
                        ) { musicItem ->
                            MusicListItem(
                                musicFile = musicItem,
                                isLiked = musicItem.id in likedIds,
                                menu = SongMenuActions(
                                    onPlayNext = { onAction(MusicVM.PlayNextAfterCurrentAction(musicItem)) },
                                    onAddToQueue = { onAction(MusicVM.AddToQueueAction(musicItem)) },
                                    onToggleLike = { onAction(MusicVM.ToggleLikeAction(musicItem)) },
                                    onAddToPlaylist = onAddToPlaylist?.let { add -> { add(musicItem) } },
                                )
                            )
                        }
                    }
                }
            }
        }

    }
}

/** The songs of the playing playlist (the queue), instead of all songs. */
@Composable
private fun PlayingFromList(
    source: String,
    queue: List<MusicFileUi>,
    currentIndex: Int,
    likedIds: Set<Long>,
    lazyListState: LazyListState,
    onAction: (Action) -> Unit,
    onAddToPlaylist: ((MusicFileUi) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Playing from",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(source, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            IconButton(onClick = { onAction(MusicVM.ShowAllSongsAction) }) {
                Icon(Icons.Filled.Close, contentDescription = "Show all songs")
            }
        }
        val songs = remember(queue, currentIndex) {
            queue.mapIndexed { index, song -> song.copy(isPlaying = index == currentIndex) }.toMutableStateList()
        }
        ReorderableList(
            modifier = Modifier.weight(1f),
            list = songs,
            key = { it.id },
            lazyListState = lazyListState,
            onDragEnd = { reordered -> onAction(MusicVM.ReorderQueueAction(reordered.map { it.id })) },
            onClick = { song ->
                val index = queue.indexOfFirst { it.id == song.id }
                if (index >= 0) onAction(MusicVM.PlayQueueIndexAction(index))
            }
        ) { song ->
            MusicListItem(
                musicFile = song,
                isLiked = song.id in likedIds,
                menu = SongMenuActions(
                    onPlayNext = { onAction(MusicVM.PlayNextAfterCurrentAction(song)) },
                    onAddToQueue = { onAction(MusicVM.AddToQueueAction(song)) },
                    onToggleLike = { onAction(MusicVM.ToggleLikeAction(song)) },
                    onAddToPlaylist = onAddToPlaylist?.let { add -> { add(song) } },
                )
            )
        }
    }
}
