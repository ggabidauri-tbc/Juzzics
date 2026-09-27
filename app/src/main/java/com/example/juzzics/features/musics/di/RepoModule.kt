package com.example.juzzics.features.musics.di

import com.example.juzzics.features.musics.data.repo.MusicRepoImpl
import com.example.juzzics.features.musics.domain.repo.MusicRepo
import org.koin.dsl.bind
import org.koin.dsl.module


val musicRepoModule = module {
    single { MusicRepoImpl(get(), get(), get()) } bind MusicRepo::class
}
