package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kasumi321.ushio.phitracker.data.platform.SecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val storage: SecureKeyValueStorage,
    private val preloadStorage: SecureKeyValueStorage,
    // Credentials must be supplied explicitly so a wiring omission cannot put them in plain storage.
    private val secureStorage: SecureKeyValueStorage,
    private val legacySecureStorage: SecureKeyValueStorage? = null
) : SettingsRepository {

    init {
        storage.remove(KEY_MONEY_STRING)
        storage.remove(KEY_AVATAR_URI)
        migrateLegacyApiToken()
    }

    private val themeModeState = MutableStateFlow(storage.getString(KEY_THEME_MODE)?.toIntOrNull() ?: 0)
    override val themeMode: Flow<Int> = themeModeState.asStateFlow()

    private val amoledBlackState = MutableStateFlow(
        storage.getString(KEY_AMOLED_BLACK)?.toBooleanStrictOrNull() ?: false
    )
    override val amoledBlack: Flow<Boolean> = amoledBlackState.asStateFlow()

    private val themeColorSourceState = MutableStateFlow(storage.getString(KEY_THEME_COLOR_SOURCE) ?: "system")
    override val themeColorSource: Flow<String> = themeColorSourceState.asStateFlow()

    private val seedColorArgbState = MutableStateFlow(storage.getString(KEY_SEED_COLOR_ARGB)?.toIntOrNull() ?: -10011977)
    override val seedColorArgb: Flow<Int> = seedColorArgbState.asStateFlow()

    private val themeImageSeedColorArgbState = MutableStateFlow(storage.getString(KEY_THEME_IMAGE_SEED_COLOR_ARGB)?.toIntOrNull())
    override val themeImageSeedColorArgb: Flow<Int?> = themeImageSeedColorArgbState.asStateFlow()

    private val themeImageUriState = MutableStateFlow(storage.getString(KEY_THEME_IMAGE_URI))
    override val themeImageUri: Flow<String?> = themeImageUriState.asStateFlow()

    private val paletteStyleNameState = MutableStateFlow(storage.getString(KEY_PALETTE_STYLE_NAME) ?: "TonalSpot")
    override val paletteStyleName: Flow<String> = paletteStyleNameState.asStateFlow()

    private val showB30OverflowState = MutableStateFlow(storage.getString(KEY_SHOW_B30_OVERFLOW)?.toBooleanStrictOrNull() ?: false)
    override val showB30Overflow: Flow<Boolean> = showB30OverflowState.asStateFlow()

    private val overflowCountState = MutableStateFlow(
        storage.getString(KEY_OVERFLOW_COUNT)?.toIntOrNull()?.coerceIn(1, 30) ?: 9
    )
    override val overflowCount: Flow<Int> = overflowCountState.asStateFlow()

    private val appLanguageState = MutableStateFlow(
        storage.getString(KEY_APP_LANGUAGE)?.takeIf { it in SUPPORTED_APP_LANGUAGES } ?: APP_LANGUAGE_SYSTEM
    )
    override val appLanguage: Flow<String> = appLanguageState.asStateFlow()

    override suspend fun setAppLanguage(language: String) {
        val normalized = language.takeIf { it in SUPPORTED_APP_LANGUAGES } ?: APP_LANGUAGE_SYSTEM
        storage.putString(KEY_APP_LANGUAGE, normalized)
        appLanguageState.value = normalized
    }

    override suspend fun setThemeMode(mode: Int) {
        storage.putString(KEY_THEME_MODE, mode.toString())
        themeModeState.value = mode
    }

    override suspend fun setAmoledBlack(enabled: Boolean) {
        storage.putString(KEY_AMOLED_BLACK, enabled.toString())
        amoledBlackState.value = enabled
    }

    override suspend fun setThemeColorSource(source: String) {
        storage.putString(KEY_THEME_COLOR_SOURCE, source)
        themeColorSourceState.value = source
    }

    override suspend fun setSeedColorArgb(argb: Int) {
        storage.putString(KEY_SEED_COLOR_ARGB, argb.toString())
        seedColorArgbState.value = argb
    }

    override suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int) {
        if (uri == null) {
            storage.remove(KEY_THEME_IMAGE_URI)
        } else {
            storage.putString(KEY_THEME_IMAGE_URI, uri)
        }
        storage.putString(KEY_THEME_IMAGE_SEED_COLOR_ARGB, seedColorArgb.toString())
        themeImageUriState.value = uri
        themeImageSeedColorArgbState.value = seedColorArgb
    }

    override suspend fun clearThemeImageColor() {
        storage.remove(KEY_THEME_IMAGE_URI)
        storage.remove(KEY_THEME_IMAGE_SEED_COLOR_ARGB)
        themeImageUriState.value = null
        themeImageSeedColorArgbState.value = null
    }

    override suspend fun setPaletteStyleName(name: String) {
        storage.putString(KEY_PALETTE_STYLE_NAME, name)
        paletteStyleNameState.value = name
    }

    override suspend fun setShowB30Overflow(show: Boolean) {
        storage.putString(KEY_SHOW_B30_OVERFLOW, show.toString())
        showB30OverflowState.value = show
    }

    override suspend fun setOverflowCount(count: Int) {
        val normalized = count.coerceIn(1, 30)
        storage.putString(KEY_OVERFLOW_COUNT, normalized.toString())
        overflowCountState.value = normalized
    }

    private val b30CardStyleState = MutableStateFlow(
        storage.getString(KEY_B30_CARD_STYLE)?.takeIf { it == B30_CARD_STYLE_SCORE_FOCUS }
            ?: B30_CARD_STYLE_CLASSIC
    )
    override val b30CardStyle: Flow<String> = b30CardStyleState.asStateFlow()

    override suspend fun setB30CardStyle(style: String) {
        val normalized = if (style == B30_CARD_STYLE_SCORE_FOCUS) B30_CARD_STYLE_SCORE_FOCUS else B30_CARD_STYLE_CLASSIC
        storage.putString(KEY_B30_CARD_STYLE, normalized)
        b30CardStyleState.value = normalized
    }

    private val b30ThemeFollowGlobalState = MutableStateFlow(
        storage.getString(KEY_B30_THEME_FOLLOW_GLOBAL)?.toBooleanStrictOrNull() ?: true
    )
    override val b30ThemeFollowGlobal: Flow<Boolean> = b30ThemeFollowGlobalState.asStateFlow()

    override suspend fun setB30ThemeFollowGlobal(follow: Boolean) {
        storage.putString(KEY_B30_THEME_FOLLOW_GLOBAL, follow.toString())
        b30ThemeFollowGlobalState.value = follow
    }

    private val b30ExportThemeModeState = MutableStateFlow(
        storage.getString(KEY_B30_EXPORT_THEME_MODE)?.toIntOrNull()?.coerceIn(1, 3)
    )
    override val b30ExportThemeMode: Flow<Int?> = b30ExportThemeModeState.asStateFlow()

    override suspend fun setB30ExportThemeMode(mode: Int) {
        val normalized = mode.coerceIn(1, 3)
        storage.putString(KEY_B30_EXPORT_THEME_MODE, normalized.toString())
        b30ExportThemeModeState.value = normalized
    }

    private val hazeBlurEnabledState = MutableStateFlow(storage.getString(KEY_HAZE_BLUR_ENABLED)?.toBooleanStrictOrNull() ?: true)
    override val hazeBlurEnabled: Flow<Boolean> = hazeBlurEnabledState.asStateFlow()

    private val hazeBlurStrengthState = MutableStateFlow(
        storage.getString(KEY_HAZE_BLUR_STRENGTH)?.toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: 0.75f
    )
    override val hazeBlurStrength: Flow<Float> = hazeBlurStrengthState.asStateFlow()

    override suspend fun setHazeBlurEnabled(enabled: Boolean) {
        storage.putString(KEY_HAZE_BLUR_ENABLED, enabled.toString())
        hazeBlurEnabledState.value = enabled
    }

    override suspend fun setHazeBlurStrength(strength: Float) {
        val normalized = strength.coerceIn(0.5f, 1.5f)
        storage.putString(KEY_HAZE_BLUR_STRENGTH, normalized.toString())
        hazeBlurStrengthState.value = normalized
    }

    override suspend fun getPreloadDone(): Boolean = preloadStorage.getString(KEY_PRELOAD_DONE)?.toBooleanStrictOrNull() ?: false

    override suspend fun setPreloadDone(done: Boolean) {
        preloadStorage.putString(KEY_PRELOAD_DONE, done.toString())
    }

    private val includePreReleaseState = MutableStateFlow(storage.getString(KEY_INCLUDE_PRE_RELEASE)?.toBooleanStrictOrNull() ?: false)
    override val includePreRelease: Flow<Boolean> = includePreReleaseState.asStateFlow()

    override suspend fun setIncludePreRelease(enabled: Boolean) {
        storage.putString(KEY_INCLUDE_PRE_RELEASE, enabled.toString())
        includePreReleaseState.value = enabled
    }

    private val autoCheckUpdateState = MutableStateFlow(storage.getString(KEY_AUTO_CHECK_UPDATE)?.toBooleanStrictOrNull() ?: true)
    override val autoCheckUpdate: Flow<Boolean> = autoCheckUpdateState.asStateFlow()

    override suspend fun setAutoCheckUpdate(enabled: Boolean) {
        storage.putString(KEY_AUTO_CHECK_UPDATE, enabled.toString())
        autoCheckUpdateState.value = enabled
    }

    private val autoCheckSongDataUpdateState = MutableStateFlow(storage.getString(KEY_AUTO_CHECK_SONG_DATA_UPDATE)?.toBooleanStrictOrNull() ?: true)
    override val autoCheckSongDataUpdate: Flow<Boolean> = autoCheckSongDataUpdateState.asStateFlow()

    override suspend fun setAutoCheckSongDataUpdate(enabled: Boolean) {
        storage.putString(KEY_AUTO_CHECK_SONG_DATA_UPDATE, enabled.toString())
        autoCheckSongDataUpdateState.value = enabled
    }

    private val apiEnabledState = MutableStateFlow(storage.getString(KEY_API_ENABLED)?.toBooleanStrictOrNull() ?: false)
    override val apiEnabled: Flow<Boolean> = apiEnabledState.asStateFlow()

    override suspend fun setApiEnabled(enabled: Boolean) {
        storage.putString(KEY_API_ENABLED, enabled.toString())
        apiEnabledState.value = enabled
    }

    private val useApiDataState = MutableStateFlow(storage.getString(KEY_USE_API_DATA)?.toBooleanStrictOrNull() ?: false)
    override val useApiData: Flow<Boolean> = useApiDataState.asStateFlow()

    override suspend fun setUseApiData(useApiData: Boolean) {
        storage.putString(KEY_USE_API_DATA, useApiData.toString())
        useApiDataState.value = useApiData
    }

    private val apiIdState = MutableStateFlow(storage.getString(KEY_API_ID)?.trim() ?: "")
    override val apiId: Flow<String> = apiIdState.asStateFlow()

    override suspend fun setApiId(apiId: String) {
        val trimmed = apiId.trim()
        storage.putString(KEY_API_ID, trimmed)
        apiIdState.value = trimmed
    }

    private val apiPlatformState = MutableStateFlow(storage.getString(KEY_API_PLATFORM)?.trim() ?: "")
    override val apiPlatform: Flow<String> = apiPlatformState.asStateFlow()

    override suspend fun setApiPlatform(platform: String) {
        val trimmed = platform.trim()
        storage.putString(KEY_API_PLATFORM, trimmed)
        apiPlatformState.value = trimmed
    }

    private val apiPlatformIdState = MutableStateFlow(storage.getString(KEY_API_PLATFORM_ID)?.trim() ?: "")
    override val apiPlatformId: Flow<String> = apiPlatformIdState.asStateFlow()

    override suspend fun setApiPlatformId(platformId: String) {
        val trimmed = platformId.trim()
        storage.putString(KEY_API_PLATFORM_ID, trimmed)
        apiPlatformIdState.value = trimmed
    }

    private val apiTokenState = MutableStateFlow(readApiToken())
    override val apiToken: Flow<String> = apiTokenState.asStateFlow()

    override suspend fun setApiToken(apiToken: String): Result<Unit> {
        val previousSecureValue = secureStorage.getString(KEY_API_TOKEN)
        val result = runCatching {
            val trimmed = apiToken.trim()
            val writeResult = secureStorage.putString(KEY_API_TOKEN, trimmed)
            check(writeResult == StorageWriteResult.Persisted && secureStorage.getString(KEY_API_TOKEN)?.trim() == trimmed) {
                "API token was not persisted securely"
            }
            removeLegacyApiToken()
            Unit
        }
        if (result.isFailure) {
            runCatching {
                if (previousSecureValue == null) {
                    secureStorage.remove(KEY_API_TOKEN)
                } else {
                    secureStorage.putString(KEY_API_TOKEN, previousSecureValue)
                }
            }
        }
        // A failed write can still have changed or removed the underlying
        // value. Never leave the in-memory form showing a stale credential.
        apiTokenState.value = readApiToken()
        return result
    }

    private fun readApiToken(): String =
        secureStorage.getString(KEY_API_TOKEN)?.trim()
            ?: legacySecureStorage?.getString(KEY_API_TOKEN)?.trim()
            ?: storage.getString(KEY_API_TOKEN)?.trim().orEmpty()

    private fun migrateLegacyApiToken() {
        try {
            val secured = secureStorage.getString(KEY_API_TOKEN)?.trim()
            if (secured != null) {
                removeLegacyApiToken()
                return
            }
            val legacy = legacySecureStorage?.getString(KEY_API_TOKEN)?.trim().orEmpty().ifEmpty {
                storage.getString(KEY_API_TOKEN)?.trim().orEmpty()
            }
            if (legacy.isEmpty()) return
            val writeResult = secureStorage.putString(KEY_API_TOKEN, legacy)
            if (writeResult == StorageWriteResult.Persisted && secureStorage.getString(KEY_API_TOKEN)?.trim() == legacy) {
                removeLegacyApiToken()
            }
        } catch (_: Exception) {
            // Legacy sources remain available for the next migration attempt.
        }
    }

    private fun removeLegacyApiToken(): Boolean {
        var removed = true
        if (legacySecureStorage != null && legacySecureStorage !== secureStorage) {
            removed = legacySecureStorage.remove(KEY_API_TOKEN) == StorageWriteResult.Persisted && removed
        }
        if (storage !== secureStorage && storage !== legacySecureStorage) {
            removed = storage.remove(KEY_API_TOKEN) == StorageWriteResult.Persisted && removed
        }
        return removed
    }

    private val crashNotificationGuideShownState = MutableStateFlow(storage.getString(KEY_CRASH_NOTIFICATION_GUIDE_SHOWN)?.toBooleanStrictOrNull() ?: false)
    override val crashNotificationGuideShown: Flow<Boolean> = crashNotificationGuideShownState.asStateFlow()

    override suspend fun setCrashNotificationGuideShown(shown: Boolean) {
        storage.putString(KEY_CRASH_NOTIFICATION_GUIDE_SHOWN, shown.toString())
        crashNotificationGuideShownState.value = shown
    }

    private val onboardingCompletedState = MutableStateFlow(storage.getString(KEY_ONBOARDING_COMPLETED)?.toBooleanStrictOrNull() ?: false)
    override val onboardingCompleted: Flow<Boolean> = onboardingCompletedState.asStateFlow()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        storage.putString(KEY_ONBOARDING_COMPLETED, completed.toString())
        onboardingCompletedState.value = completed
    }

    private val onboardingRerunRequestedState = MutableStateFlow(storage.getString(KEY_ONBOARDING_RERUN_REQUESTED)?.toBooleanStrictOrNull() ?: false)
    override val onboardingRerunRequested: Flow<Boolean> = onboardingRerunRequestedState.asStateFlow()

    override suspend fun setOnboardingRerunRequested(requested: Boolean) {
        storage.putString(KEY_ONBOARDING_RERUN_REQUESTED, requested.toString())
        onboardingRerunRequestedState.value = requested
    }

    private val illustrationPreloadRequestedState = MutableStateFlow(storage.getString(KEY_ILLUSTRATION_PRELOAD_REQUESTED)?.toBooleanStrictOrNull() ?: false)
    override val illustrationPreloadRequested: Flow<Boolean> = illustrationPreloadRequestedState.asStateFlow()

    override suspend fun setIllustrationPreloadRequested(requested: Boolean) {
        storage.putString(KEY_ILLUSTRATION_PRELOAD_REQUESTED, requested.toString())
        illustrationPreloadRequestedState.value = requested
    }

    private val illustrationPreloadDeclinedState = MutableStateFlow(storage.getString(KEY_ILLUSTRATION_PRELOAD_DECLINED)?.toBooleanStrictOrNull() ?: false)
    override val illustrationPreloadDeclined: Flow<Boolean> = illustrationPreloadDeclinedState.asStateFlow()

    override suspend fun setIllustrationPreloadDeclined(declined: Boolean) {
        storage.putString(KEY_ILLUSTRATION_PRELOAD_DECLINED, declined.toString())
        illustrationPreloadDeclinedState.value = declined
    }

    private val gameUpdateInfoCacheState = MutableStateFlow(storage.getString(KEY_GAME_UPDATE_CACHE))
    override val gameUpdateInfoCache: Flow<String?> = gameUpdateInfoCacheState.asStateFlow()

    override suspend fun setGameUpdateInfoCache(cache: String?) {
        if (cache == null) {
            storage.remove(KEY_GAME_UPDATE_CACHE)
        } else {
            storage.putString(KEY_GAME_UPDATE_CACHE, cache)
        }
        gameUpdateInfoCacheState.value = cache
    }

    private val chartVoteKeysState = MutableStateFlow(readChartVoteKeys())
    override val chartVoteKeys: Flow<Set<String>> = chartVoteKeysState.asStateFlow()

    override suspend fun recordChartVote(chartVoteKey: String) {
        val updated = chartVoteKeysState.value + chartVoteKey
        storage.putString(KEY_CHART_VOTE_KEYS, updated.joinToString("\n"))
        chartVoteKeysState.value = updated
    }

    private fun readChartVoteKeys(): Set<String> =
        storage.getString(KEY_CHART_VOTE_KEYS)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AMOLED_BLACK = "amoled_black"
        const val KEY_THEME_COLOR_SOURCE = "theme_color_source"
        const val KEY_SEED_COLOR_ARGB = "seed_color_argb"
        const val KEY_THEME_IMAGE_SEED_COLOR_ARGB = "theme_image_seed_color_argb"
        const val KEY_THEME_IMAGE_URI = "theme_image_uri"
        const val KEY_PALETTE_STYLE_NAME = "palette_style_name"
        const val KEY_SHOW_B30_OVERFLOW = "show_b30_overflow"
        const val KEY_OVERFLOW_COUNT = "overflow_count"
        const val KEY_APP_LANGUAGE = "app_language"
        const val APP_LANGUAGE_SYSTEM = "system"
        val SUPPORTED_APP_LANGUAGES = setOf(APP_LANGUAGE_SYSTEM, "en", "zh-Hans", "zh-Hant")
        const val KEY_B30_CARD_STYLE = "b30_card_style"
        const val B30_CARD_STYLE_CLASSIC = "classic"
        const val B30_CARD_STYLE_SCORE_FOCUS = "score_focus"
        const val KEY_B30_THEME_FOLLOW_GLOBAL = "b30_theme_follow_global"
        const val KEY_B30_EXPORT_THEME_MODE = "b30_export_theme_mode"
        const val KEY_PRELOAD_DONE = "preload_done"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_MONEY_STRING = "money_string"
        const val KEY_INCLUDE_PRE_RELEASE = "include_pre_release"
        const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
        const val KEY_AUTO_CHECK_SONG_DATA_UPDATE = "auto_check_song_data_update"
        const val KEY_API_ENABLED = "api_enabled"
        const val KEY_USE_API_DATA = "use_api_data"
        const val KEY_API_ID = "api_id"
        const val KEY_API_PLATFORM = "api_platform"
        const val KEY_API_PLATFORM_ID = "api_platform_id"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_CRASH_NOTIFICATION_GUIDE_SHOWN = "crash_notification_guide_shown"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_ONBOARDING_RERUN_REQUESTED = "onboarding_rerun_requested"
        const val KEY_ILLUSTRATION_PRELOAD_REQUESTED = "illustration_preload_requested"
        const val KEY_ILLUSTRATION_PRELOAD_DECLINED = "illustration_preload_declined"
        const val KEY_GAME_UPDATE_CACHE = "game_update_cache"
        const val KEY_HAZE_BLUR_ENABLED = "haze_blur_enabled"
        const val KEY_HAZE_BLUR_STRENGTH = "haze_blur_strength"
        const val KEY_CHART_VOTE_KEYS = "chart_vote_keys"
    }
}
