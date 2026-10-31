package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Coordinates image-painter completion with an off-screen B30 capture.
 *
 * Coil preloading only guarantees that the decode request has finished. The
 * export still must wait for every card's (and the avatar's) AsyncImage
 * painter to apply that result to its composition before the platform
 * renderer draws the view.
 */
internal class B30ExportImageLoadTracker(expectedSlotIds: Set<String>) {
    private val pending = expectedSlotIds.toMutableSet()
    private val failures = linkedMapOf<String, Throwable>()
    private val completion = CompletableDeferred<Unit>()

    init {
        if (pending.isEmpty()) completion.complete(Unit)
    }

    fun onIllustrationSettled(slotId: String, error: Throwable?) {
        if (!pending.remove(slotId)) return
        if (error != null) failures[slotId] = error
        if (pending.isEmpty()) completion.complete(Unit)
    }

    /**
     * TimeoutCancellationException IS a CancellationException, so it must be
     * intercepted here: after ensuring the CALLER itself was not cancelled (in
     * which case cancellation still propagates), the wait converts into a
     * normal failure the screen can render — a stuck image must surface as a
     * retryable error, never as a silently cancelled export that leaves the
     * page generating forever.
     */
    suspend fun awaitAll(timeoutMs: Long = 15_000L) {
        try {
            withTimeout(timeoutMs) { completion.await() }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException(
                "B30 illustration wait timed out after ${timeoutMs}ms; still pending: " +
                    pending.joinToString(prefix = "[", postfix = "]")
            )
        }
        check(failures.isEmpty()) {
            "B30 illustration rendering failed for ${failures.size} card(s): " +
                failures.entries.joinToString { (slot, error) -> "$slot=${error.message ?: error::class.simpleName}" }
        }
    }
}

internal fun B30ExportData.illustrationSlotIds(): Set<String> = buildSet {
    if (!avatarUri.isNullOrBlank()) add(B30_EXPORT_AVATAR_SLOT)
    phiRecords.forEachIndexed { index, card ->
        if (!card.illustrationUri.isNullOrBlank()) add("phi:$index")
    }
    bestRecords.forEachIndexed { index, card ->
        if (!card.illustrationUri.isNullOrBlank()) add("best:$index")
    }
    overflowRecords.forEachIndexed { index, card ->
        if (!card.illustrationUri.isNullOrBlank()) add("overflow:$index")
    }
}
