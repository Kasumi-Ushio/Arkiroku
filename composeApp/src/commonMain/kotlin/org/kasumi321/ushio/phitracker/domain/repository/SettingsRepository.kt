package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<Int>

    /**
     * Use AMOLED pure-black surfaces when the theme mode is "follow system"
     * and the system is in dark mode. Other modes ignore this flag (mode 3 is
     * always AMOLED).
     */
    val amoledBlack: Flow<Boolean>
    suspend fun setAmoledBlack(enabled: Boolean)
    val themeColorSource: Flow<String>
    val seedColorArgb: Flow<Int>
    val themeImageSeedColorArgb: Flow<Int?>
    val themeImageUri: Flow<String?>
    val paletteStyleName: Flow<String>
    val showB30Overflow: Flow<Boolean>
    val overflowCount: Flow<Int>

    /**
     * In-app display language override: "system" (default) follows the device
     * language; other supported values are "en", "zh-Hans" and "zh-Hant".
     * Applied through the platform per-app locale mechanism.
     */
    val appLanguage: Flow<String>
    suspend fun setAppLanguage(language: String)

    /**
     * B30 export card layout style: "classic" (default) or "score_focus".
     * Consumed as B30ExportCardStyle in ui.b30.
     */
    val b30CardStyle: Flow<String>
    suspend fun setB30CardStyle(style: String)

    /**
     * B30 export theme override: when [b30ThemeFollowGlobal] is true (default)
     * the export image follows the app theme; otherwise [b30ExportThemeMode]
     * decides. Mode values mirror themeMode (1 = light, 2 = dark, 3 = AMOLED);
     * a null value means the user never picked one and the current global
     * theme applies.
     */
    val b30ThemeFollowGlobal: Flow<Boolean>
    val b30ExportThemeMode: Flow<Int?>
    suspend fun setB30ThemeFollowGlobal(follow: Boolean)
    suspend fun setB30ExportThemeMode(mode: Int)

    val hazeBlurEnabled: Flow<Boolean>
    val hazeBlurStrength: Flow<Float>
    suspend fun setHazeBlurEnabled(enabled: Boolean)
    suspend fun setHazeBlurStrength(strength: Float)

    suspend fun setThemeMode(mode: Int)
    suspend fun setThemeColorSource(source: String)
    suspend fun setSeedColorArgb(argb: Int)
    suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int)
    suspend fun clearThemeImageColor()
    suspend fun setPaletteStyleName(name: String)
    suspend fun setShowB30Overflow(show: Boolean)
    suspend fun setOverflowCount(count: Int)
    suspend fun getPreloadDone(): Boolean    suspend fun setPreloadDone(done: Boolean)

    val includePreRelease: Flow<Boolean>
    val autoCheckUpdate: Flow<Boolean>
    val autoCheckSongDataUpdate: Flow<Boolean>
    suspend fun setIncludePreRelease(enabled: Boolean)
    suspend fun setAutoCheckUpdate(enabled: Boolean)
    suspend fun setAutoCheckSongDataUpdate(enabled: Boolean)

    val apiEnabled: Flow<Boolean>
    suspend fun setApiEnabled(enabled: Boolean)

    val useApiData: Flow<Boolean>
    suspend fun setUseApiData(useApiData: Boolean)

    val apiId: Flow<String>
    suspend fun setApiId(apiId: String)

    val apiPlatform: Flow<String>
    suspend fun setApiPlatform(platform: String)

    val apiPlatformId: Flow<String>
    suspend fun setApiPlatformId(platformId: String)

    val apiToken: Flow<String>
    suspend fun setApiToken(apiToken: String): Result<Unit>

    val crashNotificationGuideShown: Flow<Boolean>
    suspend fun setCrashNotificationGuideShown(shown: Boolean)

    /**
     * Whether the first-launch onboarding wizard has been completed (or
     * silently marked complete for upgrading users who already hold a session
     * token or preload record). Gates the Onboarding start destination.
     */
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * One-shot flag set by the Settings "re-run onboarding" action: the next
     * cold start opens the wizard even for token holders. The start gate
     * consumes (clears) it once it has routed to Onboarding. Kept separate
     * from [onboardingCompleted] so upgrading users whose flag is simply
     * absent keep the silent-mark behavior instead of seeing the wizard.
     */
    val onboardingRerunRequested: Flow<Boolean>
    suspend fun setOnboardingRerunRequested(requested: Boolean)

    /**
     * Set by the onboarding wizard when the user opts into downloading all
     * low-res illustrations. HomeViewModel consumes it to auto-start the
     * preload pipeline instead of showing the manual start/skip dialog; a
     * wizard "skip" is recorded via [setPreloadDone] instead.
     */
    val illustrationPreloadRequested: Flow<Boolean>
    suspend fun setIllustrationPreloadRequested(requested: Boolean)

    /**
     * Set by the onboarding wizard when the user declines the illustration
     * download. Suppresses the Home preload dialog permanently (unlike
     * [setPreloadDone], which still re-prompts when the thumbnails are
     * actually missing); cleared by the Settings "redownload" action.
     */
    val illustrationPreloadDeclined: Flow<Boolean>
    suspend fun setIllustrationPreloadDeclined(declined: Boolean)

    /**
     * JSON-serialized [org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo]
     * cache of the last fetched Phigros game update, so the profile page can
     * show the card offline. Null when nothing has been cached yet.
     */
    val gameUpdateInfoCache: Flow<String?>
    suspend fun setGameUpdateInfoCache(cache: String?)

    /**
     * Chart-tag votes already cast by the local user, as opaque record keys.
     * A key is `{userKey}:{songId}:{difficulty}` where userKey is the
     * phi-plugin identity triplet `platform:platformId:apiUserId`, so records
     * stay isolated per account. Consumers treat this as a hint for offline
     * gating only; the server's isMine markers remain the source of truth.
     */
    val chartVoteKeys: Flow<Set<String>>
    suspend fun recordChartVote(chartVoteKey: String)
}
