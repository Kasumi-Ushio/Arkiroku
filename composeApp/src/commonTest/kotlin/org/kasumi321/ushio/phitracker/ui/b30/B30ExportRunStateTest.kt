package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class B30ExportRunStateTest {
    @Test
    fun successFailureAndRetryPublishConsistentState() = runTest {
        val state = B30ExportRunState<String>()

        runB30ExportEffect("data", state) { "image" }
        assertEquals("image", state.result)
        assertFalse(state.isGenerating)
        assertFalse(state.generationFailed)

        runB30ExportEffect("data", state) {
            kotlinx.coroutines.withTimeout(1) { awaitCancellation() }
        }
        assertNull(state.result)
        assertFalse(state.isGenerating)
        assertTrue(state.generationFailed)

        state.retry()
        assertEquals(1, state.retryToken)
        runB30ExportEffect("data", state) { "retried" }
        assertEquals("retried", state.result)
        assertFalse(state.generationFailed)
    }

    @Test
    fun styleChangeIgnoresLateSuccessAndFailureFromPreviousRun() = runTest {
        val state = B30ExportRunState<String>()
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val old = launch {
            runB30ExportEffect("old-style", state) {
                oldStarted.complete(Unit)
                releaseOld.await()
                "old-image"
            }
        }
        oldStarted.await()

        runB30ExportEffect("new-style", state) { "new-image" }
        releaseOld.complete(Unit)
        old.join()
        assertEquals("new-image", state.result)
        assertFalse(state.generationFailed)

        val failingOld = launch {
            runB30ExportEffect("old-style", state) { "stale" }
        }
        failingOld.join()
        runB30ExportEffect("new-style", state) { "current" }
        val currentRun = state.result
        state.completeRun(1, Result.failure(RuntimeException("late failure")))
        assertEquals(currentRun, state.result)
        assertFalse(state.generationFailed)
    }

    @Test
    fun innerCancellationIsRetryableButOuterCancellationDoesNotPublishFailure() = runTest {
        val state = B30ExportRunState<String>()
        runB30ExportEffect("data", state) { throw CancellationException("adapter cancelled") }
        assertTrue(state.generationFailed)
        assertFalse(state.isGenerating)

        val started = CompletableDeferred<Unit>()
        val job = launch {
            runB30ExportEffect("data", state) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(state.generationFailed)
        assertTrue(state.isGenerating)
        assertNull(state.result)
    }

    @Test
    fun cancelledBlockingRunnerCannotPublishLateSuccess() = runTest {
        val state = B30ExportRunState<String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            runB30ExportEffect("data", state) {
                withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                }
                "late-image"
            }
        }
        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertNull(state.result)
        assertTrue(state.isGenerating)
        assertFalse(state.generationFailed)
    }
}
