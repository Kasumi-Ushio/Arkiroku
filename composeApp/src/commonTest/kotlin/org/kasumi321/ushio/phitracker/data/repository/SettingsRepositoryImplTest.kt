package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryImplTest {

    private fun createRepo(): SettingsRepositoryImpl {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        return SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
    }

    @Test
    fun defaultsThemeModeZero(): Unit = runTest {
        val repo = createRepo()
        assertEquals(0, repo.themeMode.first())
    }

    @Test
    fun setAndReadThemeMode(): Unit = runTest {
        val repo = createRepo()
        repo.setThemeMode(2)
        assertEquals(2, repo.themeMode.first())
    }

    @Test
    fun defaultsAmoledBlackFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.amoledBlack.first())
    }

    @Test
    fun setAndReadAmoledBlack(): Unit = runTest {
        val repo = createRepo()
        repo.setAmoledBlack(true)
        assertEquals(true, repo.amoledBlack.first())
        repo.setAmoledBlack(false)
        assertEquals(false, repo.amoledBlack.first())
    }

    @Test
    fun defaultsThemeColorSettings(): Unit = runTest {
        val repo = createRepo()
        assertEquals("system", repo.themeColorSource.first())
        assertEquals(-10011977, repo.seedColorArgb.first())
        assertNull(repo.themeImageSeedColorArgb.first())
        assertNull(repo.themeImageUri.first())
        assertEquals("TonalSpot", repo.paletteStyleName.first())
    }

    @Test
    fun setAndReadThemeColorSettings(): Unit = runTest {
        val repo = createRepo()
        repo.setThemeColorSource("seed")
        repo.setSeedColorArgb(-16738680)
        repo.setPaletteStyleName("Vibrant")
        repo.setThemeImageColor("content://image/1", -1499549)

        assertEquals("seed", repo.themeColorSource.first())
        assertEquals(-16738680, repo.seedColorArgb.first())
        assertEquals("Vibrant", repo.paletteStyleName.first())
        assertEquals("content://image/1", repo.themeImageUri.first())
        assertEquals(-1499549, repo.themeImageSeedColorArgb.first())

        repo.clearThemeImageColor()
        assertNull(repo.themeImageUri.first())
        assertNull(repo.themeImageSeedColorArgb.first())
    }

    @Test
    fun defaultsShowB30OverflowFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.showB30Overflow.first())
    }

    @Test
    fun defaultsOverflowCountNine(): Unit = runTest {
        val repo = createRepo()
        assertEquals(9, repo.overflowCount.first())
    }

    @Test
    fun legacyGlobalAccountProfileValuesAreDiscarded(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage().apply {
            map["avatar_uri"] = "file:///previous-account-avatar.png"
            map["money_string"] = "999"
        }

        SettingsRepositoryImpl(storage, FakeSecureKeyValueStorage(), FakeSecureKeyValueStorage())

        assertNull(storage.map["avatar_uri"])
        assertNull(storage.map["money_string"])
    }

    @Test
    fun defaultsIncludePreReleaseFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.includePreRelease.first())
    }

    @Test
    fun setAndReadIncludePreRelease(): Unit = runTest {
        val repo = createRepo()
        repo.setIncludePreRelease(true)
        assertEquals(true, repo.includePreRelease.first())
    }

    @Test
    fun defaultsApiEnabledFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.apiEnabled.first())
    }

    @Test
    fun defaultsUseApiDataFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.useApiData.first())
    }

    @Test
    fun defaultsApiIdEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiId.first())
    }

    @Test
    fun setApiIdTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiId("  abc123  ")
        assertEquals("abc123", repo.apiId.first())
    }

    @Test
    fun defaultsApiPlatformEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiPlatform.first())
    }

    @Test
    fun setApiPlatformTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiPlatform("  TapTap  ")
        assertEquals("TapTap", repo.apiPlatform.first())
    }

    @Test
    fun defaultsApiPlatformIdEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals("", repo.apiPlatformId.first())
    }

    @Test
    fun setApiPlatformIdTrimsWhitespace(): Unit = runTest {
        val repo = createRepo()
        repo.setApiPlatformId("  id_001  ")
        assertEquals("id_001", repo.apiPlatformId.first())
    }

    @Test
    fun storageWritePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setThemeMode(3)
        repo1.setApiId("myId")
        repo1.setApiPlatform("platform")
        repo1.setIncludePreRelease(true)
        repo1.setApiEnabled(true)
        repo1.setUseApiData(true)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(3, repo2.themeMode.first())
        assertEquals("myId", repo2.apiId.first())
        assertEquals("platform", repo2.apiPlatform.first())
        assertEquals(true, repo2.includePreRelease.first())
        assertEquals(true, repo2.apiEnabled.first())
        assertEquals(true, repo2.useApiData.first())
    }

    @Test
    fun preloadStorageIsSeparate(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo.getPreloadDone())
        repo.setPreloadDone(true)
        assertEquals(true, repo.getPreloadDone())

        // preloadDone is stored in preloadStorage, not in main storage
        assertEquals(null, storage.map["preload_done"])
        assertEquals("true", preloadStorage.map["preload_done"])
    }

    @Test
    fun defaultsCrashNotificationGuideShownFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.crashNotificationGuideShown.first())
    }

    @Test
    fun setAndReadCrashNotificationGuideShown(): Unit = runTest {
        val repo = createRepo()
        repo.setCrashNotificationGuideShown(true)
        assertEquals(true, repo.crashNotificationGuideShown.first())
    }

    @Test
    fun crashNotificationGuideShownPersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setCrashNotificationGuideShown(true)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(true, repo2.crashNotificationGuideShown.first())
    }

    @Test
    fun defaultsAutoCheckUpdateTrue(): Unit = runTest {
        val repo = createRepo()
        assertEquals(true, repo.autoCheckUpdate.first())
    }

    @Test
    fun setAndReadAutoCheckUpdate(): Unit = runTest {
        val repo = createRepo()
        repo.setAutoCheckUpdate(false)
        assertEquals(false, repo.autoCheckUpdate.first())
        repo.setAutoCheckUpdate(true)
        assertEquals(true, repo.autoCheckUpdate.first())
    }

    @Test
    fun autoCheckUpdatePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setAutoCheckUpdate(false)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo2.autoCheckUpdate.first())
    }

    @Test
    fun defaultsAutoCheckSongDataUpdateTrue(): Unit = runTest {
        val repo = createRepo()
        assertEquals(true, repo.autoCheckSongDataUpdate.first())
    }

    @Test
    fun setAndReadAutoCheckSongDataUpdate(): Unit = runTest {
        val repo = createRepo()
        repo.setAutoCheckSongDataUpdate(false)
        assertEquals(false, repo.autoCheckSongDataUpdate.first())
        repo.setAutoCheckSongDataUpdate(true)
        assertEquals(true, repo.autoCheckSongDataUpdate.first())
    }

    @Test
    fun autoCheckSongDataUpdatePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setAutoCheckSongDataUpdate(false)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo2.autoCheckSongDataUpdate.first())
    }

    @Test
    fun overflowCountClampedOnReadFromStorage(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("overflow_count", "35")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(30, repo.overflowCount.first())
    }

    @Test
    fun overflowCountClampedOnReadFromStorageBelowOne(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("overflow_count", "0")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(1, repo.overflowCount.first())
    }

    @Test
    fun setOverflowCountClampsValue(): Unit = runTest {
        val repo = createRepo()
        repo.setOverflowCount(35)
        assertEquals(30, repo.overflowCount.first())
        repo.setOverflowCount(0)
        assertEquals(1, repo.overflowCount.first())
        repo.setOverflowCount(15)
        assertEquals(15, repo.overflowCount.first())
    }

    @Test
    fun defaultsHazeBlurEnabledTrue(): Unit = runTest {
        val repo = createRepo()
        assertEquals(true, repo.hazeBlurEnabled.first())
    }

    @Test
    fun defaultsHazeBlurStrengthThreeQuarters(): Unit = runTest {
        val repo = createRepo()
        assertEquals(0.75f, repo.hazeBlurStrength.first())
    }

    @Test
    fun setAndReadHazeBlurSettings(): Unit = runTest {
        val repo = createRepo()
        repo.setHazeBlurEnabled(false)
        repo.setHazeBlurStrength(1.5f)
        assertEquals(false, repo.hazeBlurEnabled.first())
        assertEquals(1.5f, repo.hazeBlurStrength.first())
    }

    @Test
    fun hazeBlurSettingsPersistBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setHazeBlurEnabled(false)
        repo1.setHazeBlurStrength(1.25f)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo2.hazeBlurEnabled.first())
        assertEquals(1.25f, repo2.hazeBlurStrength.first())
    }

    @Test
    fun setHazeBlurStrengthClampsValue(): Unit = runTest {
        val repo = createRepo()
        repo.setHazeBlurStrength(3f)
        assertEquals(1.5f, repo.hazeBlurStrength.first())
        repo.setHazeBlurStrength(0.1f)
        assertEquals(0.5f, repo.hazeBlurStrength.first())
    }

    @Test
    fun hazeBlurStrengthClampedOnReadFromStorage(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("haze_blur_strength", "5.0")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(1.5f, repo.hazeBlurStrength.first())
    }

    @Test
    fun gameUpdateCacheDefaultsToNull(): Unit = runTest {
        val repo = createRepo()
        assertNull(repo.gameUpdateInfoCache.first())
    }

    @Test
    fun gameUpdateCachePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setGameUpdateInfoCache("""{"version":"3.20.0","versionCode":154}""")

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals("""{"version":"3.20.0","versionCode":154}""", repo2.gameUpdateInfoCache.first())
    }

    @Test
    fun gameUpdateCacheCanBeCleared(): Unit = runTest {
        val repo = createRepo()
        repo.setGameUpdateInfoCache("""{"version":"3.20.0"}""")
        repo.setGameUpdateInfoCache(null)
        assertNull(repo.gameUpdateInfoCache.first())
    }

    @Test
    fun defaultsB30CardStyleClassic(): Unit = runTest {
        val repo = createRepo()
        assertEquals("classic", repo.b30CardStyle.first())
    }

    @Test
    fun setAndReadB30CardStyle(): Unit = runTest {
        val repo = createRepo()
        repo.setB30CardStyle("score_focus")
        assertEquals("score_focus", repo.b30CardStyle.first())
        repo.setB30CardStyle("classic")
        assertEquals("classic", repo.b30CardStyle.first())
    }

    @Test
    fun b30CardStyleUnknownValueFallsBackToClassic(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("b30_card_style", "unknown_style")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals("classic", repo.b30CardStyle.first())

        repo.setB30CardStyle("unknown_style")
        assertEquals("classic", repo.b30CardStyle.first())
    }

    @Test
    fun b30CardStylePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setB30CardStyle("score_focus")

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals("score_focus", repo2.b30CardStyle.first())
    }

    @Test
    fun b30ThemeOverrideDefaultsToFollowGlobalWithUnsetMode(): Unit = runTest {
        val repo = createRepo()
        assertEquals(true, repo.b30ThemeFollowGlobal.first())
        assertEquals(null, repo.b30ExportThemeMode.first())
    }

    @Test
    fun b30ThemeOverrideRoundTripsAndClampsMode(): Unit = runTest {
        val repo = createRepo()
        repo.setB30ThemeFollowGlobal(false)
        repo.setB30ExportThemeMode(3)
        assertEquals(false, repo.b30ThemeFollowGlobal.first())
        assertEquals(3, repo.b30ExportThemeMode.first())

        repo.setB30ExportThemeMode(9)
        assertEquals(3, repo.b30ExportThemeMode.first())
        repo.setB30ExportThemeMode(0)
        assertEquals(1, repo.b30ExportThemeMode.first())
    }

    @Test
    fun b30ThemeOverridePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setB30ThemeFollowGlobal(false)
        repo1.setB30ExportThemeMode(2)

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo2.b30ThemeFollowGlobal.first())
        assertEquals(2, repo2.b30ExportThemeMode.first())
    }

    @Test
    fun defaultsAppLanguageSystem(): Unit = runTest {
        val repo = createRepo()
        assertEquals("system", repo.appLanguage.first())
    }

    @Test
    fun setAndReadAppLanguage(): Unit = runTest {
        val repo = createRepo()
        repo.setAppLanguage("zh-Hant")
        assertEquals("zh-Hant", repo.appLanguage.first())
        repo.setAppLanguage("system")
        assertEquals("system", repo.appLanguage.first())
    }

    @Test
    fun appLanguageUnknownValueFallsBackToSystem(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("app_language", "fr")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals("system", repo.appLanguage.first())

        repo.setAppLanguage("fr")
        assertEquals("system", repo.appLanguage.first())
    }

    @Test
    fun appLanguagePersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setAppLanguage("en")

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals("en", repo2.appLanguage.first())
    }

    @Test
    fun defaultsOnboardingFlagsFalse(): Unit = runTest {
        val repo = createRepo()
        assertEquals(false, repo.onboardingCompleted.first())
        assertEquals(false, repo.illustrationPreloadRequested.first())
        assertEquals(false, repo.illustrationPreloadDeclined.first())
    }

    @Test
    fun setAndReadOnboardingFlags(): Unit = runTest {
        val repo = createRepo()
        repo.setOnboardingCompleted(true)
        repo.setIllustrationPreloadRequested(true)
        assertEquals(true, repo.onboardingCompleted.first())
        assertEquals(true, repo.illustrationPreloadRequested.first())
        repo.setIllustrationPreloadRequested(false)
        assertEquals(false, repo.illustrationPreloadRequested.first())
    }

    @Test
    fun rerunRequestedRoundTripsAndPersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(false, repo1.onboardingRerunRequested.first())
        repo1.setOnboardingRerunRequested(true)
        assertEquals(true, repo1.onboardingRerunRequested.first())
        assertEquals("true", storage.map["onboarding_rerun_requested"])

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(true, repo2.onboardingRerunRequested.first())
        repo2.setOnboardingRerunRequested(false)
        assertEquals(false, repo2.onboardingRerunRequested.first())
    }

    @Test
    fun onboardingFlagsPersistInMainStorageBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setOnboardingCompleted(true)
        repo1.setIllustrationPreloadRequested(true)

        // Both flags live in the main storage, unlike preload_done
        assertEquals("true", storage.map["onboarding_completed"])
        assertEquals("true", storage.map["illustration_preload_requested"])
        assertEquals(null, preloadStorage.map["onboarding_completed"])

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(true, repo2.onboardingCompleted.first())
        assertEquals(true, repo2.illustrationPreloadRequested.first())
    }

    @Test
    fun illustrationPreloadDeclinedPersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.setIllustrationPreloadDeclined(true)

        assertEquals("true", storage.map["illustration_preload_declined"])
        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(true, repo2.illustrationPreloadDeclined.first())

        repo2.setIllustrationPreloadDeclined(false)
        assertEquals(false, repo2.illustrationPreloadDeclined.first())
    }

    @Test
    fun chartVoteKeysDefaultToEmpty(): Unit = runTest {
        val repo = createRepo()
        assertEquals(emptySet(), repo.chartVoteKeys.first())
    }

    @Test
    fun recordChartVoteAccumulatesKeysAndPersistsBetweenInstances(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()

        val repo1 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        repo1.recordChartVote("taptap:player-id:api-user:song-a.0:IN")
        repo1.recordChartVote("taptap:player-id:api-user:song-a.0:EZ")
        assertEquals(
            setOf("taptap:player-id:api-user:song-a.0:IN", "taptap:player-id:api-user:song-a.0:EZ"),
            repo1.chartVoteKeys.first()
        )

        val repo2 = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(
            setOf("taptap:player-id:api-user:song-a.0:IN", "taptap:player-id:api-user:song-a.0:EZ"),
            repo2.chartVoteKeys.first()
        )
    }

    @Test
    fun recordChartVoteIsIdempotent(): Unit = runTest {
        val repo = createRepo()
        repo.recordChartVote("taptap:player-id:api-user:song-a.0:IN")
        repo.recordChartVote("taptap:player-id:api-user:song-a.0:IN")
        assertEquals(setOf("taptap:player-id:api-user:song-a.0:IN"), repo.chartVoteKeys.first())
    }

    @Test
    fun blankStorageEntryReadsAsEmptyKeySet(): Unit = runTest {
        val storage = FakeSecureKeyValueStorage()
        val preloadStorage = FakeSecureKeyValueStorage()
        storage.putString("chart_vote_keys", "")

        val repo = SettingsRepositoryImpl(storage, preloadStorage, FakeSecureKeyValueStorage())
        assertEquals(emptySet(), repo.chartVoteKeys.first())
    }
}
