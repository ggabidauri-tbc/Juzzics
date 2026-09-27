package com.example.juzzics.features.musics.di

import com.example.juzzics.features.musics.domain.usecases.GetAllLocalMusicFilesUseCase
import com.example.juzzics.features.musics.domain.usecases.SaveSongOrderUseCase
import org.koin.dsl.module

val musicUseCasesModule = module {
    factory { GetAllLocalMusicFilesUseCase(get()) }
    factory { SaveSongOrderUseCase(get()) }
}
