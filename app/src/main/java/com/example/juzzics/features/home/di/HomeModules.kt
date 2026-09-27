package com.example.juzzics.features.home.di

import com.example.juzzics.features.home.data.repo.HistoryRepoImpl
import com.example.juzzics.features.home.domain.repo.HistoryRepo
import com.example.juzzics.features.home.domain.usecase.GetMostPlayedUseCase
import com.example.juzzics.features.home.domain.usecase.GetRecentlyPlayedUseCase
import com.example.juzzics.features.home.domain.usecase.RecordPlayUseCase
import com.example.juzzics.features.home.ui.vm.HomeVM
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val homeRepoModule = module {
    single { HistoryRepoImpl(get()) } bind HistoryRepo::class
}

val homeUseCasesModule = module {
    factory { RecordPlayUseCase(get()) }
    factory { GetRecentlyPlayedUseCase(get()) }
    factory { GetMostPlayedUseCase(get()) }
}

val homeViewModelsModule = module {
    viewModelOf(::HomeVM)
}
