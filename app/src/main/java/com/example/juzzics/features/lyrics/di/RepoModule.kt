package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.data.repo.LyricsRepoImpl
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import org.koin.dsl.bind
import org.koin.dsl.module

val lyricsRepoModule = module {
    single { LyricsRepoImpl(get(), get(), get()) } bind LyricsRepo::class
}
