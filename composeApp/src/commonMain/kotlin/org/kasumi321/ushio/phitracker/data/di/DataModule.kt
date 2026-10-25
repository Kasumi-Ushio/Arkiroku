package org.kasumi321.ushio.phitracker.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.api.TapTapGameApi
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataUpdater
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.database.createAppDatabase
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.logging.LoggingStateHolder
import org.kasumi321.ushio.phitracker.data.parser.AesDecryptor
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.data.platform.ArtworkFileCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.createPlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.createSecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.repository.PhigrosRepositoryImpl
import org.kasumi321.ushio.phitracker.data.repository.QrLoginRepositoryImpl
import org.kasumi321.ushio.phitracker.data.repository.SettingsRepositoryImpl
import org.kasumi321.ushio.phitracker.data.session.SessionCoordinatorImpl
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadCoordinator
import org.kasumi321.ushio.phitracker.data.song.IllustrationPreloadSession
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdateCoordinator
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.koin.dsl.module

val dataModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
    single {
        HttpClient {
            install(ContentNegotiation) { json(get()) }
            // Debug builds only: request/response metadata goes through AppLogger
            // (file writer + Logcat/OSLog). Release installs nothing, so there is
            // no overhead and no header-leak surface.
            if (LoggingStateHolder.state?.isDebugBuild == true) {
                install(Logging) {
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) = AppLogger.d("Http", message)
                    }
                    level = LogLevel.INFO
                    listOf("Authorization", "X-LC-Session", "X-LC-Id", "X-LC-Sign", "token")
                        .forEach { header -> sanitizeHeader(header) { true } }
                }
            }
        }
    }
    single { TapTapApiClient(get()) }
    single { PhiPluginApi(get()) }
    single { TapTapGameApi(get()) }
    single { TapTapQrLoginApi(get()) }
    single<QrLoginRepository> { QrLoginRepositoryImpl(get()) }
    single { AesDecryptor() }
    single { SaveParser(get()) }
    single<AppDatabase> { createAppDatabase() }
    single { get<AppDatabase>().recordDao() }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().syncSnapshotDao() }
    single { get<AppDatabase>().songSyncHistoryDao() }
    single { get<AppDatabase>().currentAccountDao() }
    single<SessionCoordinator> { SessionCoordinatorImpl(get()) }
    single { TokenManager(createSecureKeyValueStorage("phi_tracker_secure_prefs")) }
    single<SettingsRepository> {
        SettingsRepositoryImpl(
            storage = createSecureKeyValueStorage("phitracker_settings"),
            preloadStorage = createSecureKeyValueStorage("illustration_prefs")
        )
    }
    single { createPlatformPaths() }
    single<StandardArtworkCache> { ArtworkFileCache(get(), get()) }
    single { SongDataProvider(paths = get()) }
    single<PhigrosRepository> {
        PhigrosRepositoryImpl(
            apiClient = get(),
            phiPluginApi = get(),
            tapTapGameApi = get(),
            httpClient = get(),
            saveParser = get(),
            database = get(),
            recordDao = get(),
            userDao = get(),
            syncSnapshotDao = get(),
            songSyncHistoryDao = get(),
            currentAccountDao = get(),
            tokenManager = get(),
            json = get(),
            songDataProvider = get()
        )
    }
    single { IllustrationProvider() }
    single { IllustrationUriResolver(get(), get()) }
    single { TipsProvider() }
    single { SongDataUpdater(get(), get(), get()) }
    single { CollectibleDataProvider(paths = get()) }
    single { CollectibleDataUpdater(get(), get(), get()) }
    single {
        SongDataUpdateCoordinator(
            songDataUpdater = get(),
            songDataProvider = get(),
            illustrationProvider = get(),
            artworkFileCache = get(),
            collectibleDataUpdater = get()
        )
    }
    single {
        IllustrationPreloadCoordinator(
            songDataProvider = get(),
            illustrationProvider = get(),
            artworkFileCache = get(),
            thumbnailPreloader = CoilIllustrationThumbnailPreloader
        )
    }
    single { IllustrationPreloadSession(get(), get()) }
}
