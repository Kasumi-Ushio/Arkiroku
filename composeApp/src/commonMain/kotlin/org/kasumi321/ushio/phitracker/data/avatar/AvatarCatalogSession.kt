package org.kasumi321.ushio.phitracker.data.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.common.CatalogDownloadException
import org.kasumi321.ushio.phitracker.data.common.CatalogStorageException
import org.kasumi321.ushio.phitracker.data.common.StagedRollbackException
import org.kasumi321.ushio.phitracker.data.common.StagedUpdateBlockedException

enum class AvatarCatalogRunStatus {
    Idle,
    Checking,
    Ready,
    UpdatingCatalog,
    ReconcilingImages,
    PartiallySucceeded,
    Failed
}

enum class AvatarCatalogFailure {
    Network,
    Storage,
    Scene,
    Busy,
    Unknown
}

data class AvatarCatalogState(
    val status: AvatarCatalogRunStatus = AvatarCatalogRunStatus.Idle,
    val updateAvailable: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentAvatar: String = "",
    val failedDownloads: Int = 0,
    val failure: AvatarCatalogFailure? = null,
    val usingBundledFallback: Boolean = false
)

/**
 * Application-scoped avatar work. Opening the picker can always resume repair
 * without coupling the job to a ViewModel, and every failure remains local to
 * avatar functionality rather than blocking application startup.
 */
class AvatarCatalogSession(
    private val coordinator: AvatarDataUpdateCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val mutableState = MutableStateFlow(AvatarCatalogState())
    val state: StateFlow<AvatarCatalogState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var generation = 0L

    /** Recover retained catalog work and reconcile missing/obsolete images. */
    fun ensureReady() {
        val run = beginRun(AvatarCatalogRunStatus.Checking) ?: return
        job = scope.launch {
            try {
                val usingFallback = coordinator.isUsingBundledFallback()
                if (usingFallback) {
                    // Finish a quarantine interrupted by process death before
                    // the file-first provider reads the catalog.
                    coordinator.useBundledFallback().getOrElse { fail(run, it); return@launch }
                } else {
                    coordinator.recoverPendingUpdate().getOrElse { fail(run, it); return@launch }
                }
                if (run != generation) return@launch
                if (coordinator.hasPendingRepair() && !usingFallback) {
                    publish(run, AvatarCatalogRunStatus.UpdatingCatalog)
                    val outcome = coordinator.update(
                        onFileProgress = { completed, total, name ->
                            publishProgress(run, AvatarCatalogRunStatus.UpdatingCatalog, completed, total, name)
                        },
                        onImageProgress = { progress ->
                            publishProgress(
                                run,
                                AvatarCatalogRunStatus.ReconcilingImages,
                                progress.completed,
                                progress.total,
                                progress.currentAvatarKey
                            )
                        }
                    ).getOrElse { fail(run, it); return@launch }
                    finishOutcome(run, outcome)
                } else {
                    publish(run, AvatarCatalogRunStatus.ReconcilingImages)
                    val outcome = coordinator.reconcileImages { progress ->
                        publishProgress(
                            run,
                            AvatarCatalogRunStatus.ReconcilingImages,
                            progress.completed,
                            progress.total,
                            progress.currentAvatarKey
                        )
                    }.getOrElse { fail(run, it); return@launch }
                    finishOutcome(run, outcome)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(run, error)
            }
        }
    }

    fun checkForUpdates() {
        val run = beginRun(AvatarCatalogRunStatus.Checking) ?: return
        job = scope.launch {
            try {
                coordinator.checkUpstreamChanged().fold(
                    onSuccess = { changed ->
                        mutableState.update {
                            if (run != generation) it else it.copy(
                                status = AvatarCatalogRunStatus.Ready,
                                updateAvailable = changed,
                                failure = null,
                                usingBundledFallback = coordinator.isUsingBundledFallback()
                            )
                        }
                    },
                    onFailure = { fail(run, it) }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(run, error)
            }
        }
    }

    fun update() {
        val run = beginRun(AvatarCatalogRunStatus.UpdatingCatalog) ?: return
        job = scope.launch {
            try {
                val result = coordinator.update(
                    onFileProgress = { completed, total, name ->
                        publishProgress(run, AvatarCatalogRunStatus.UpdatingCatalog, completed, total, name)
                    },
                    onImageProgress = { progress ->
                        publishProgress(
                            run,
                            AvatarCatalogRunStatus.ReconcilingImages,
                            progress.completed,
                            progress.total,
                            progress.currentAvatarKey
                        )
                    }
                )
                result.fold(
                    onSuccess = { finishOutcome(run, it) },
                    onFailure = { fail(run, it) }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(run, error)
            }
        }
    }

    fun useBundledFallback() {
        job?.cancel()
        val run = beginRun(AvatarCatalogRunStatus.Checking, replaceRunning = true) ?: return
        job = scope.launch {
            try {
                coordinator.useBundledFallback().fold(
                    onSuccess = {
                        mutableState.update {
                            if (run != generation) it else it.copy(
                                status = AvatarCatalogRunStatus.Ready,
                                updateAvailable = true,
                                failedDownloads = 0,
                                failure = null,
                                usingBundledFallback = true
                            )
                        }
                    },
                    onFailure = { fail(run, it) }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(run, error)
            }
        }
    }

    fun dismissResult() {
        mutableState.update {
            it.copy(
                status = if (it.status == AvatarCatalogRunStatus.Failed ||
                    it.status == AvatarCatalogRunStatus.PartiallySucceeded
                ) AvatarCatalogRunStatus.Ready else it.status,
                failedDownloads = 0,
                failure = null
            )
        }
    }

    private fun beginRun(
        status: AvatarCatalogRunStatus,
        replaceRunning: Boolean = false
    ): Long? {
        if (!replaceRunning && job?.isActive == true) return null
        generation++
        val run = generation
        mutableState.update {
            it.copy(
                status = status,
                completed = 0,
                total = 0,
                currentAvatar = "",
                failedDownloads = 0,
                failure = null,
                usingBundledFallback = coordinator.isUsingBundledFallback()
            )
        }
        return run
    }

    private fun publish(run: Long, status: AvatarCatalogRunStatus) {
        mutableState.update {
            if (run != generation) it else it.copy(status = status)
        }
    }

    private fun publishProgress(
        run: Long,
        status: AvatarCatalogRunStatus,
        completed: Int,
        total: Int,
        currentAvatar: String
    ) {
        mutableState.update {
            if (run != generation) it else it.copy(
                status = status,
                completed = completed,
                total = total,
                currentAvatar = currentAvatar
            )
        }
    }

    private fun finishOutcome(run: Long, outcome: AvatarDataUpdateCoordinator.UpdateOutcome) {
        mutableState.update {
            if (run != generation) it else it.copy(
                status = if (outcome.isPartial) {
                    AvatarCatalogRunStatus.PartiallySucceeded
                } else {
                    AvatarCatalogRunStatus.Ready
                },
                updateAvailable = false,
                failedDownloads = outcome.failedKeys.size,
                failure = null,
                usingBundledFallback = coordinator.isUsingBundledFallback()
            )
        }
        if (run == generation && !coordinator.isUsingBundledFallback() &&
            (coordinator.hasPendingRecovery() || coordinator.hasPendingRepair())
        ) {
            fail(run, CatalogStorageException())
        }
    }

    private fun fail(run: Long, error: Throwable?) {
        mutableState.update {
            if (run != generation) it else it.copy(
                status = AvatarCatalogRunStatus.Failed,
                failure = classifyFailure(error),
                usingBundledFallback = coordinator.isUsingBundledFallback()
            )
        }
    }

    private fun classifyFailure(error: Throwable?): AvatarCatalogFailure = when (error) {
        is CatalogDownloadException -> AvatarCatalogFailure.Network
        is CatalogStorageException -> AvatarCatalogFailure.Storage
        is StagedRollbackException, is StagedUpdateBlockedException -> AvatarCatalogFailure.Scene
        is AvatarUpdateAlreadyInProgressException -> AvatarCatalogFailure.Busy
        else -> AvatarCatalogFailure.Unknown
    }
}
