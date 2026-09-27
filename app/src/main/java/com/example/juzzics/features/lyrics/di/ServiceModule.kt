package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.data.service.LrclibService
import com.example.juzzics.features.lyrics.data.service.LyricsService
import com.example.juzzics.features.lyrics.util.LRCLIB_BASE_URL
import com.example.juzzics.features.lyrics.util.LYRICS_BASE_URL
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val LYRICS_OVH = "lyricsOvh"
private const val LRCLIB = "lrclib"

val serviceModule = module {
    single(named(LYRICS_OVH)) {
        Retrofit.Builder()
            .baseUrl(LYRICS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single(named(LRCLIB)) {
        // LRCLIB asks apps to say who they are
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Juzzics (https://github.com/ggabidauri-tbc/Juzzics)")
                        .build()
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single { get<Retrofit>(named(LYRICS_OVH)).create(LyricsService::class.java) }
    single { get<Retrofit>(named(LRCLIB)).create(LrclibService::class.java) }
}
