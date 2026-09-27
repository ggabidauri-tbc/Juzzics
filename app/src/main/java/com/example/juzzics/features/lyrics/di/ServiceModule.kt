package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.data.service.LyricsService
import com.example.juzzics.features.lyrics.util.LYRICS_BASE_URL
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val serviceModule = module {
    single {
        Retrofit.Builder()
            .baseUrl(LYRICS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single {
        get<Retrofit>().create(LyricsService::class.java)
    }
}