package org.kasumi321.ushio.phitracker.ui.home

import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogram
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeUiStateTest {
    @Test
    fun losingAccountAccessClearsEveryAccountDerivedProjection() {
        val record = BestRecord("song.0", "Song", Difficulty.IN, 999000, 99.9f, false, 15f, 15f)
        val snapshot = SyncSnapshot(1, 2, 15f, "player", 1, "song.0", "IN", 999000, 99.9f)
        val state = HomeUiState(
            profile = ProfileUiState(
                hasAccountAccess = true,
                nickname = "player",
                challengeModeRank = 1,
                avatarUri = "avatar",
                moneyString = "1 MiB",
                clearCounts = mapOf("IN" to 1),
                fcCount = 2,
                phiCount = 3,
                lastSyncTime = 4,
                lastSyncedRecord = record,
                recentSyncedRecords = listOf(record)
            ),
            b30 = B30UiState(
                b30 = listOf(record),
                allRecords = listOf(record),
                displayRks = 15f,
                histogram = B30RksHistogram(emptyList(), emptyList(), 15f, 0f, 1, 0f, 16f)
            ),
            tools = ToolsUiState(
                syncSnapshots = listOf(snapshot),
                sessionToken = "secret",
                apiRksRank = 1,
                apiTotalUsers = 2,
                apiHistorySnapshots = listOf(snapshot),
                apiRankByUser = ApiToolResult(message = null),
                apiRankByPosition = ApiToolResult(message = null),
                apiRksRankResult = ApiToolResult(message = null)
            )
        )

        val cleared = state.withoutAccountAccess()

        assertEquals(ProfileUiState(), cleared.profile)
        assertTrue(cleared.b30.b30.isEmpty())
        assertTrue(cleared.b30.allRecords.isEmpty())
        assertEquals(0f, cleared.b30.displayRks)
        assertNull(cleared.b30.histogram)
        assertEquals(B30TagAnalysisState(), cleared.b30.tagAnalysis)
        assertTrue(cleared.tools.syncSnapshots.isEmpty())
        assertNull(cleared.tools.sessionToken)
        assertNull(cleared.tools.apiRksRank)
        assertNull(cleared.tools.apiTotalUsers)
        assertTrue(cleared.tools.apiHistorySnapshots.isEmpty())
        assertEquals(ApiToolResult(), cleared.tools.apiRankByUser)
        assertEquals(ApiToolResult(), cleared.tools.apiRankByPosition)
        assertEquals(ApiToolResult(), cleared.tools.apiRksRankResult)
    }
}
