package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class B30ImagePreloadTest {
    @Test
    fun cancellationFailureFromImageLoaderIsRethrown() = runTest {
        val error = assertFailsWith<CancellationException> {
            preloadB30ExportImages(exportData()) { _, _, _ ->
                Result.failure(CancellationException("cancelled by caller"))
            }
        }

        assertTrue(error.message?.contains("cancelled") == true)
    }

    @Test
    fun ordinaryImageFailureRemainsARetryablePreparationFailure() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            preloadB30ExportImages(exportData()) { _, _, _ ->
                Result.failure(IllegalStateException("image missing"))
            }
        }

        assertTrue(error.message?.contains("preparation failed") == true)
    }

    @Test
    fun productionAdapterCancellationPropagatesThroughWholePreloadAndExport() = runTest {
        assertFailsWith<CancellationException> {
            runB30Export(exportData(), preload = { data ->
                preloadB30ExportImages(data) { _, _, _ -> throw CancellationException("adapter cancelled") }
            })
        }
    }

    @Test
    fun neverEndingProductionPreloadTimesOutAndCancelsItsTasks() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        assertFailsWith<TimeoutCancellationException> {
            runB30Export(exportData(cardCount = 1), timeoutMs = 100, dispatcher = Dispatchers.Unconfined, preload = { data ->
                preloadB30ExportImages(data) { _, _, _ ->
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            })
        }
        cancelled.await()
    }

    @Test
    fun pageDefaultTimeoutIsTheDocumentedFortyFiveSeconds() = runTest {
        // The B30 page calls runB30Export(data) with no timeout override, so
        // the default parameter is the production budget. Virtual time makes
        // the real 45 s observable without hardcoding a test-only timeout.
        assertEquals(45_000L, B30_EXPORT_TOTAL_TIMEOUT_MS)

        val startedAt = testScheduler.currentTime
        assertFailsWith<TimeoutCancellationException> {
            runB30Export(
                exportData(cardCount = 1),
                preload = { awaitCancellation() },
                generate = { error("generation must not start after the preload timed out") }
            )
        }

        assertEquals(B30_EXPORT_TOTAL_TIMEOUT_MS, testScheduler.currentTime - startedAt)
    }

    @Test
    fun leavingTheExportCancelsStartedAndQueuedProductionTasks() = runTest {
        val allPermitsStarted = CompletableDeferred<Unit>()
        var started = 0
        var cancelled = 0
        val job = launch {
            preloadB30ExportImages(exportData(cardCount = 8)) { _, _, _ ->
                started++
                if (started == 6) allPermitsStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    if (!currentCoroutineContext().isActive) cancelled++
                }
            }
        }

        allPermitsStarted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(6, started, "two tasks must still be queued behind the semaphore")
        assertEquals(6, cancelled, "every permit holder must be cancelled; queued tasks must never start")
    }

    @Test
    fun optionalBackgroundDoesNotBlockRequiredArtworkPreload() = runTest {
        val requested = mutableListOf<String>()

        preloadB30ExportImages(
            exportData(cardCount = 1).copy(backgroundUri = "https://slow.test/background.png")
        ) { url, _, _ ->
            requested += url
            Result.success(Unit)
        }

        assertEquals(listOf("file:///tmp/0.png"), requested)
    }

    @Test
    fun backgroundTimeoutFallsBackAndStillCompletesExport() = runTest {
        val completed = assertFailsWith<IllegalStateException> {
            runB30Export(
                exportData = exportData(),
                dispatcher = StandardTestDispatcher(testScheduler),
                preload = {},
                generate = {
                    val background = withOptionalB30Background<Unit>(timeoutMs = 100) {
                        awaitCancellation()
                    }
                    assertNull(background)
                    error("export completed without background")
                }
            )
        }

        assertEquals("export completed without background", completed.message)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun externalBackgroundCancellationIsNotConvertedToFallback() = runTest {
        assertFailsWith<CancellationException> {
            withOptionalB30Background<Unit> {
                throw CancellationException("composition destroyed")
            }
        }
    }

    private fun exportData(cardCount: Int = 0) = B30ExportData(
        nickname = "player",
        rks = 15f,
        challengeLevel = 0,
        moneyString = "",
        dateText = "",
        avatarUri = if (cardCount == 0) "file:///tmp/avatar.png" else null,
        statsTable = B30StatsTable(clearCounts = emptyMap(), fcCount = 0, phiCount = 0),
        phiRecords = emptyList(),
        bestRecords = List(cardCount) { index ->
            ExportCardData(record(index), "file:///tmp/$index.png")
        },
        overflowRecords = emptyList(),
        backgroundUri = null
    )

    private fun record(index: Int) = BestRecord(
        songId = "song-$index.0",
        songName = "Song $index",
        difficulty = Difficulty.IN,
        score = 1_000_000,
        accuracy = 100f,
        isFullCombo = true,
        chartConstant = 15f,
        rks = 15f,
        isPhi = true
    )
}
