package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juzzics.features.musics.ui.model.BrowseTab
import com.example.juzzics.features.musics.ui.model.SongSort

/** Search field + sort menu + Songs/Albums/Artists tabs above the song list. */
@Composable
fun MusicListHeader(
    query: String,
    sort: SongSort,
    tab: BrowseTab,
    onQueryChange: (String) -> Unit,
    onSortChange: (SongSort) -> Unit,
    onTabChange: (BrowseTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        when (tab) {
                            BrowseTab.SONGS -> "Search songs or artists"
                            BrowseTab.ALBUMS -> "Search albums"
                            BrowseTab.ARTISTS -> "Search artists"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SongSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                            onClick = {
                                onSortChange(option)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            BrowseTab.entries.forEach { option ->
                FilterChip(
                    selected = option == tab,
                    onClick = { onTabChange(option) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}
