package org.kasumi321.ushio.phitracker.di

import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.LoggingStateHolder
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetChartTagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSongLevelBoundsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.ProposeSongAliasUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.AnalyzeB30TagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.FetchGameUpdateInfoUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.VoteChartTagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SearchCollectibleUseCase
import org.kasumi321.ushio.phitracker.ui.collectible.CollectibleDetailViewModel
import org.kasumi321.ushio.phitracker.ui.collectible.CollectiblesViewModel
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel
import org.kasumi321.ushio.phitracker.ui.login.LoginViewModel
import org.kasumi321.ushio.phitracker.ui.onboarding.OnboardingViewModel
import org.kasumi321.ushio.phitracker.ui.settings.SettingsViewModel
import org.kasumi321.ushio.phitracker.ui.song.SongDetailViewModel
import org.kasumi321.ushio.phitracker.ui.suggest.SuggestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { GetB30UseCase(get()) }
    single { GetSuggestUseCase() }
    single { GetSongLevelBoundsUseCase() }
    single { SearchSongUseCase() }
    single { CheckForUpdateUseCase(get()) }
    single { FetchGameUpdateInfoUseCase(get()) }
    single { GetChartTagsUseCase(get()) }
    single { VoteChartTagsUseCase(get()) }
    single { ProposeSongAliasUseCase(get()) }
    single { AnalyzeB30TagsUseCase() }
    single { SearchCollectibleUseCase() }
    single {
        val store = LoggingStateHolder.state?.store
            ?: error("LoggingState not initialised. Call createLoggingState() before initKoin().")
        RuntimeLogExporter(store)
    }
    single {
        val store = LoggingStateHolder.state?.store
            ?: error("LoggingState not initialised. Call createLoggingState() before initKoin().")
        CrashReportExporter(store)
    }
    viewModel { LoginViewModel(get(), get()) }
    viewModel {
        OnboardingViewModel(
            settingsRepository = get(),
            illustrationPreloadSession = get(),
            getB30UseCase = get(),
            songDataProvider = get(),
            songDataUpdateCoordinator = get(),
            illustrationUriResolver = get()
        )
    }
    viewModel { parameters ->
        SongDetailViewModel(
            songId = parameters.get(),
            initialDifficulty = parameters.get(),
            repository = get(),
            settingsRepository = get(),
            songDataProvider = get(),
            illustrationUriResolver = get(),
            getChartTagsUseCase = get(),
            voteChartTagsUseCase = get(),
            proposeSongAliasUseCase = get()
        )
    }
    viewModel {
        SettingsViewModel(
            repository = get(),
            sessionCoordinator = get(),
            settingsRepository = get(),
            checkForUpdateUseCase = get(),
            getB30UseCase = get(),
            songDataProvider = get(),
            songDataUpdateCoordinator = get(),
            illustrationProvider = get(),
            artworkFileCache = get(),
            runtimeLogExporter = get(),
            crashReportExporter = get(),
            tipsProvider = get(),
            collectibleDataVersionProvider = get<CollectibleDataProvider>()::currentVersionText
        )
    }
    viewModel {
        SuggestViewModel(
            repository = get(),
            getB30UseCase = get(),
            getSuggestUseCase = get(),
            songDataProvider = get()
        )
    }
    viewModel {
        HomeViewModel(
            repository = get(),
            getB30UseCase = get(),
            sessionCoordinator = get(),
            searchSongUseCase = get(),
            getSongLevelBoundsUseCase = get(),
            songDataProvider = get(),
            illustrationProvider = get(),
            tipsProvider = get(),
            settingsRepository = get(),
            artworkFileCache = get(),
            illustrationPreloadCoordinator = get(),
            checkForUpdateUseCase = get(),
            fetchGameUpdateInfoUseCase = get(),
            songDataUpdateCoordinator = get(),
            preloadSession = get(),
        )
    }
    viewModel {
        CollectiblesViewModel(
            collectibleDataProvider = get(),
            searchCollectibleUseCase = get(),
            settingsRepository = get(),
            illustrationUriResolver = get()
        )
    }
    viewModel { parameters ->
        CollectibleDetailViewModel(
            folderKey = parameters.get(),
            key = parameters.get(),
            collectibleDataProvider = get(),
            illustrationUriResolver = get(),
            settingsRepository = get()
        )
    }
}
