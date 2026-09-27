package com.example.juzzics.features.musics.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.juzzics.features.musics.data.localProvider.FakeMusicLocalProvider
import com.example.juzzics.features.musics.data.localProvider.MusicLocalProvider
import com.example.juzzics.features.musics.data.localProvider.MusicLocalProviderImpl
import org.koin.dsl.module

val musicLocalDataModule = module {
    single<MusicLocalProvider> {
        val context = get<Context>()
        val real = MusicLocalProviderImpl(context)
        // debug builds only: show fake songs when the device has no music (emulator)
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) FakeMusicLocalProvider(real) else real
    }
}
