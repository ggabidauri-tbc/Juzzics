package com.example.juzzics.features.playlists.di

import com.example.juzzics.features.playlists.data.repo.PlaylistsRepoImpl
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo
import org.koin.dsl.bind
import org.koin.dsl.module

val playlistsRepoModule = module {
    single { PlaylistsRepoImpl(get(), get()) } bind PlaylistsRepo::class
}
