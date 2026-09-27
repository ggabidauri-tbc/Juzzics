package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.data.service.LrclibService
import com.example.juzzics.features.lyrics.data.service.LyricsService
import com.example.juzzics.features.lyrics.util.LRCLIB_BASE_URL
import com.example.juzzics.features.lyrics.util.LYRICS_BASE_URL
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val LYRICS_OVH = "lyricsOvh"
private const val LRCLIB = "lrclib"

val serviceModule = module {
    single(named(LYRICS_OVH)) {
        // the fallback, often slow: give up quickly instead of holding up trip prep
        val client = OkHttpClient.Builder()
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(LYRICS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    single(named(LRCLIB)) {
        // LRCLIB asks apps to say who they are
        // OkHttp allows only 5 requests to one site at a time by default: trip prep sends more
        // (several songs, several searches each). Still polite to a free service.
        val dispatcher = Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 12
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .callTimeout(15, TimeUnit.SECONDS)
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
