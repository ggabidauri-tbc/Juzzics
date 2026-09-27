package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.data.LyricsPrep
import com.example.juzzics.features.lyrics.data.repo.LyricsRepoImpl
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val lyricsRepoModule = module {
    single { LyricsRepoImpl(get(), get(), get()) } bind LyricsRepo::class
    single { LyricsPrep(androidContext(), get(), get(), get()) }
}
