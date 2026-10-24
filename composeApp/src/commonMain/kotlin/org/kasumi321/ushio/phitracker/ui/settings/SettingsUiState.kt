package org.kasumi321.ushio.phitracker.ui.settings

import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText

data class SettingsUiState(
    val themeMode: Int = 0,
    val amoledBlack: Boolean = false,
    val themeColorSource: String = "system",
    val seedColorArgb: Int = -10011977,
    val themeImageSeedColorArgb: Int? = null,
    val themeImageUri: String? = null,
    val paletteStyleName: String = "TonalSpot",
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9,
    val hazeBlurEnabled: Boolean = true,
    val hazeBlurStrength: Float = 0.75f,
    val appLanguage: String = "system",
    val isCachingB30Artwork: Boolean = false,
    val b30ArtworkCacheCompleted: Int = 0,
    val b30ArtworkCacheTotal: Int = 0,
    val b30ArtworkCacheError: UiText? = null,
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiUserId: String = "",
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val apiToken: String = "",
    val isApiTesting: Boolean = false,
    val apiTestMessage: UiText? = null,
    val isUpdatingData: Boolean = false,
    val updateDataPhase: UpdateDataPhase = UpdateDataPhase.Files,
    val updateDataProgress: Int = 0,
    val updateDataTotal: Int = 0,
    val updateDataFileName: String = "",
    val updateDataError: UiText? = null,
    // Text of the currently effective collection_version.txt, shown alongside
    // the index-data update row in Settings.
    val collectibleDataVersion: String = "",
    // Non-null after a successful song-data update; lists the newly added
    // songs whose illustrations were synced, shown in the result dialog.
    val updateResultSongNames: List<String>? = null,
    val includePreRelease: Boolean = false,
    val autoCheckUpdate: Boolean = true,
    val autoCheckSongDataUpdate: Boolean = true,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val hasRuntimeLogs: Boolean = false,
    val hasCrashLogs: Boolean = false,
    val crashNotificationGuideShown: Boolean = false,
    val tip: String = ""
)

enum class UpdateDataPhase {
    Files,
    Illustrations
}

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
    data object RestartRequested : SettingsEvent
}
