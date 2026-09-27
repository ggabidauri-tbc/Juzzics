package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.domain.usecase.FetchLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.ObserveSavedLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SaveLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.FindLyricsUseCase
import com.example.juzzics.features.lyrics.domain.usecase.SearchLyricsUseCase
import org.koin.dsl.module

val lyricsUseCasesModule = module {
    factory { FetchLyricsUseCase(get()) }
    factory { ObserveSavedLyricsUseCase(get()) }
    factory { SaveLyricsUseCase(get()) }
    factory { FindLyricsUseCase(get()) }
    factory { SearchLyricsUseCase(get()) }
}
