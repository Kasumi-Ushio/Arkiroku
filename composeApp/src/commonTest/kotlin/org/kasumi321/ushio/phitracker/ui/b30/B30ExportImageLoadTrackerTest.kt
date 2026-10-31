package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class B30ExportImageLoadTrackerTest {

    @Test
    fun captureWaitsUntilEveryCardSlotSettles() = runTest {
        val tracker = B30ExportImageLoadTracker(setOf("phi:0", "best:0"))
        tracker.onIllustrationSettled("phi:0", error = null)
        tracker.onIllustrationSettled("best:0", error = null)

        tracker.awaitAll()
    }

    @Test
    fun captureRejectsAnIncompleteImageResult() = runTest {
        val tracker = B30ExportImageLoadTracker(setOf("best:0"))
        tracker.onIllustrationSettled("best:0", IllegalStateException("decode failed"))

        assertFailsWith<IllegalStateException> { tracker.awaitAll() }
    }

    @Test
    fun aNeverSettlingSlotTimesOutAsAFailureNamingThePendingSlots() = runTest {
        // TimeoutCancellationException is a CancellationException; the tracker
        // must convert the WAIT timeout into a normal failure so the export
        // page reaches its error state instead of staying "generating".
        val tracker = B30ExportImageLoadTracker(setOf("phi:0", "best:1"))

        val failure = assertFailsWith<IllegalStateException> {
            tracker.awaitAll(timeoutMs = 1_000)
        }
        assertTrue("best:1" in (failure.message ?: ""), "timeout message should name pending slots")
    }

    @Test
    fun externalCancellationStillPropagatesAsCancellation() = runTest {
        val tracker = B30ExportImageLoadTracker(setOf("phi:0"))
        val awaitJob = async { tracker.awaitAll(timeoutMs = 60_000) }

        advanceTimeBy(59_999)
        awaitJob.cancelAndJoin()

        assertTrue(awaitJob.isCancelled, "cancelling the caller must propagate, not convert to failure")
    }

    @Test
    fun slotIdsIncludeAvatarWhenAvatarUriPresent() {
        val withAvatar = exportData(avatarUri = "file:///tmp/avatar.png")
        val withoutAvatar = exportData(avatarUri = null)

        assertEquals(setOf(B30_EXPORT_AVATAR_SLOT), withAvatar.illustrationSlotIds())
        assertEquals(emptySet(), withoutAvatar.illustrationSlotIds())
    }

    private fun exportData(avatarUri: String?): B30ExportData = B30ExportData(
        nickname = "player",
        rks = 15f,
        challengeLevel = 0,
        moneyString = "",
        dateText = "",
        avatarUri = avatarUri,
        statsTable = B30StatsTable(clearCounts = emptyMap(), fcCount = 0, phiCount = 0),
        phiRecords = emptyList(),
        bestRecords = emptyList(),
        overflowRecords = emptyList(),
        backgroundUri = null
    )
}
