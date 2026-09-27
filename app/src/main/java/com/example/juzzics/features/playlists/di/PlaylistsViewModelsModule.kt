package com.example.juzzics.features.playlists.di

import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playlistsViewModelsModule = module {
    viewModelOf(::PlaylistsVM)
}
