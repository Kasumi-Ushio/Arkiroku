package org.kasumi321.ushio.phitracker.data.avatar

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kasumi321.ushio.phitracker.data.common.resultOf

class AvatarUpdateAlreadyInProgressException :
    Exception("an avatar catalog update is already in progress")

/** Serializes catalog transactions and the self-healing image reconciliation. */
class AvatarDataUpdateCoordinator(
    private val updater: AvatarDataUpdater,
    private val catalogProvider: AvatarCatalogProvider,
    private val imageStore: AvatarImageStore
) {
    data class ImageProgress(
        val completed: Int,
        val total: Int,
        val currentAvatarKey: String
    )

    data class UpdateOutcome(
        val downloadedKeys: Set<String> = emptySet(),
        val deletedKeys: Set<String> = emptySet(),
        val failedKeys: Set<String> = emptySet()
    ) {
        val isPartial: Boolean get() = failedKeys.isNotEmpty()
    }

    private val updateMutex = Mutex()

    suspend fun checkUpstreamChanged(): Result<Boolean> = updateMutex.withLock {
        resultOf {
            if (updater.hasPendingRecovery() || updater.hasPendingRepair()) return@resultOf true
            updater.checkUpstreamChanged().getOrThrow()
        }
    }

    fun hasPendingRecovery(): Boolean = updater.hasPendingRecovery()

    fun hasPendingRepair(): Boolean = updater.hasPendingRepair()

    fun isUsingBundledFallback(): Boolean = updater.isUsingBundledFallback()

    suspend fun recoverPendingUpdate(): Result<Unit> = updateMutex.withLock {
        updater.recoverPendingUpdate()
    }

    suspend fun markRepairRequired(): Result<Unit> = updateMutex.withLock {
        updater.markRepairRequired()
    }

    suspend fun useBundledFallback(): Result<Unit> = updateMutex.withLock {
        updater.useBundledFallback()
    }

    /** Reset means reset plus a complete update, never merely deleting evidence. */
    suspend fun resetPendingUpdate(
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onImageProgress: (ImageProgress) -> Unit = {}
    ): Result<UpdateOutcome> {
        if (!updateMutex.tryLock()) {
            return Result.failure(AvatarUpdateAlreadyInProgressException())
        }
        try {
            updater.resetPendingUpdate().getOrElse { return Result.failure(it) }
            return updateLocked(onFileProgress, onImageProgress)
        } finally {
            updateMutex.unlock()
        }
    }

    suspend fun update(
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onImageProgress: (ImageProgress) -> Unit = {}
    ): Result<UpdateOutcome> {
        if (!updateMutex.tryLock()) {
            return Result.failure(AvatarUpdateAlreadyInProgressException())
        }
        try {
            return updateLocked(onFileProgress, onImageProgress)
        } finally {
            updateMutex.unlock()
        }
    }

    /** Re-reads actual files, so this also repairs interrupted/partial prior runs. */
    suspend fun reconcileImages(
        onProgress: (ImageProgress) -> Unit = {}
    ): Result<UpdateOutcome> = updateMutex.withLock {
        resultOf { reconcileLocked(onProgress) }
    }

    private suspend fun updateLocked(
        onFileProgress: (Int, Int, String) -> Unit,
        onImageProgress: (ImageProgress) -> Unit
    ): Result<UpdateOutcome> {
        updater.recoverPendingUpdate().getOrElse { return Result.failure(it) }
        updater.updateAll(onFileProgress).getOrElse { return Result.failure(it) }
        // The committed catalog remains authoritative even when image work
        // below reports partial failure. No catalog rollback is attempted.
        return resultOf { reconcileLocked(onImageProgress) }
    }

    private suspend fun reconcileLocked(onProgress: (ImageProgress) -> Unit): UpdateOutcome {
        val requiredKeys = catalogProvider.getAvatars().mapTo(linkedSetOf()) { it.resourceKey }
        val result = imageStore.reconcile(requiredKeys) { completed, total, key ->
            onProgress(ImageProgress(completed, total, key))
        }
        return UpdateOutcome(
            downloadedKeys = result.downloadedKeys,
            deletedKeys = result.deletedKeys,
            failedKeys = result.failedKeys
        )
    }
}
