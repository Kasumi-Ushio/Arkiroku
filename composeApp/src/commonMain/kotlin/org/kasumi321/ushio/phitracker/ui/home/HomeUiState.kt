package org.kasumi321.ushio.phitracker.ui.home

import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogram
import org.kasumi321.ushio.phitracker.domain.model.B30TagAnalysis
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.utils.UiText

data class HomeUiState(
    val profile: ProfileUiState = ProfileUiState(),
    val songs: SongsUiState = SongsUiState(),
    val b30: B30UiState = B30UiState(),
    val tools: ToolsUiState = ToolsUiState(),
    val sync: SyncUiState = SyncUiState()
)

data class ProfileUiState(
    val nickname: String = "",
    val challengeModeRank: Int = 0,
    val avatarUri: String? = null,
    val moneyString: String = "",
    val clearCounts: Map<String, Int> = emptyMap(),
    val fcCount: Int = 0,
    val phiCount: Int = 0,
    val lastSyncTime: Long? = null,
    val lastSyncedRecord: BestRecord? = null,
    val recentSyncedRecords: List<BestRecord> = emptyList()
)

data class SongsUiState(
    val searchQuery: String = "",
    val filteredSongs: List<SongInfo> = emptyList(),
    val allSongs: List<SongInfo> = emptyList(),
    val availableChapters: List<String> = emptyList(),
    val selectedChapters: Set<String> = emptySet(),
    val selectedDifficulty: Difficulty? = null,
    val minLevel: Int = 1,
    val maxLevel: Int = 18,
    // Slider limits derived from the loaded song data; 1..18 is the pre-load
    // fallback so a constant-18 chart is never hidden by a stale hard cap.
    val levelMinBound: Int = 1,
    val levelMaxBound: Int = 18,
    val showFilterSheet: Boolean = false,
    val illustrationReady: Boolean = true,
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val preloadCurrentSong: String? = null,
    val isPreloading: Boolean = false,
    val songDataUpdateAvailable: Boolean = false,
    val isSongDataRefreshing: Boolean = false,
    val songDataStatusText: UiText? = null,
    val songDataProgressFraction: Float? = null,
    val songDataMessage: UiText? = null
)

/**
 * The compact Profile progress card only exists for a background run. While the
 * progress dialog is open it already shows the same information, so showing
 * both would duplicate it.
 */
internal fun SongsUiState.shouldShowPreloadCard(): Boolean =
    isPreloading && !showPreloadDialog

data class B30UiState(
    val b30: List<BestRecord> = emptyList(),
    val allRecords: List<BestRecord> = emptyList(),
    val displayRks: Float = 0f,
    val histogram: B30RksHistogram? = null,
    val themeSettings: PhiTrackerThemeSettings = PhiTrackerThemeSettings(),
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9,
    val tagAnalysis: B30TagAnalysisState = B30TagAnalysisState()
)

data class B30TagAnalysisState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val analysis: B30TagAnalysis? = null
)

data class ToolsUiState(
    val syncSnapshots: List<SyncSnapshot> = emptyList(),
    val sessionToken: String? = null,
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiUserId: String = "",
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val apiRksRank: Int? = null,
    val apiTotalUsers: Int? = null,
    val apiHistorySnapshots: List<SyncSnapshot> = emptyList(),
    val apiRankByUser: ApiToolResult = ApiToolResult(),
    val apiRankByPosition: ApiToolResult = ApiToolResult(),
    val apiRksRankResult: ApiToolResult = ApiToolResult()
)

data class SyncUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: UiText? = null,
    val isLoggedOut: Boolean = false,
    val gameUpdateInfo: GameUpdateInfo? = null,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
)
