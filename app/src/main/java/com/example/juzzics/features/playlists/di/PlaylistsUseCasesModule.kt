package com.example.juzzics.features.playlists.di

import com.example.juzzics.features.playlists.domain.usecase.AddSongToPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.CreatePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase
import com.example.juzzics.features.playlists.domain.usecase.RemoveSongFromPlaylistUseCase
import org.koin.dsl.module

val playlistsUseCasesModule = module {
    factory { GetPlaylistsUseCase(get()) }
    factory { CreatePlaylistUseCase(get()) }
    factory { AddSongToPlaylistUseCase(get()) }
    factory { RemoveSongFromPlaylistUseCase(get()) }
}
