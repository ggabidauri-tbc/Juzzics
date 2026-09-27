package com.example.juzzics.features.playlists.di

import com.example.juzzics.features.playlists.domain.usecase.AddSongToPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.CreatePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.DeletePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase
import com.example.juzzics.features.playlists.domain.usecase.RemoveSongFromPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.RenamePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.ReorderPlaylistSongsUseCase
import com.example.juzzics.features.playlists.domain.usecase.ObserveLikedSongIdsUseCase
import com.example.juzzics.features.playlists.domain.usecase.ToggleLikeUseCase
import org.koin.dsl.module

val playlistsUseCasesModule = module {
    factory { GetPlaylistsUseCase(get()) }
    factory { CreatePlaylistUseCase(get()) }
    factory { RenamePlaylistUseCase(get()) }
    factory { DeletePlaylistUseCase(get()) }
    factory { AddSongToPlaylistUseCase(get()) }
    factory { RemoveSongFromPlaylistUseCase(get()) }
    factory { ReorderPlaylistSongsUseCase(get()) }
    factory { ObserveLikedSongIdsUseCase(get()) }
    factory { ToggleLikeUseCase(get()) }
}
