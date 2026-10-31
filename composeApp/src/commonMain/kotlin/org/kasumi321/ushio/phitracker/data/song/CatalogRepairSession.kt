package org.kasumi321.ushio.phitracker.data.song

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
import org.kasumi321.ushio.phitracker.data.logging.AppLogger

enum class CatalogRepairStatus {
    Idle,
    Running,
    Cancelling,
    Succeeded,
    Failed,
    Degraded
}

enum class CatalogRepairStartResult {
    Started,
    AlreadyRunning
}

enum class CatalogRepairStage {
    Checking,
    Files,
    Illustrations
}

/** The only entry allowed to present a terminal result for its run. */
enum class CatalogRepairEntryPoint {
    Recovery,
    Home,
    Onboarding,
    Settings
}

/** User-facing failure category; UI maps it to localized text, never to an exception. */
enum class CatalogRepairFailure {
    Network,
    Storage,
    Scene,
    Busy,
    Unknown
}

data class CatalogRepairState(
    val status: CatalogRepairStatus = CatalogRepairStatus.Idle,
    val stage: CatalogRepairStage = CatalogRepairStage.Checking,
    /**
     * True while the app must not render its navigation graph. Startup repair
     * keeps this set until it succeeds, degrades to the bundled catalog, or
     * fails; manual updates start non-blocking but re-block when they leave a
     * scene/storage state the provider must not read.
     */
    val blocking: Boolean = true,
    val completed: Int = 0,
    val total: Int = 0,
    val currentItem: String = "",
    val addedSongNames: List<String> = emptyList(),
    val partialIllustrationFailures: Int = 0,
    val failure: CatalogRepairFailure? = null,
    /** Identity of the current run and its terminal result. */
    val resultGeneration: Long = 0L,
    /** Only this entry may present the terminal message for the run. */
    val resultOwner: CatalogRepairEntryPoint = CatalogRepairEntryPoint.Recovery,
    /** Manual update/reset runs publish a result; startup recovery stays silent. */
    val resultAvailable: Boolean = false,
    /** Keeps dismissed results dismissed across recreation. */
    val resultDismissed: Boolean = false,
    /** True while the bundled catalog is served instead of the downloaded one. */
    val degraded: Boolean = false
)

/**
 * Application-scoped owner of catalog repair/reset downloads.
 *
 * Recovery used to run in the gate's composition scope and Settings ran its
 * reset in `viewModelScope`; both are cancelled when the screen goes away, and
 * a repair marker could then survive without anyone finishing the download.
 * This session holds its own [CoroutineScope] (like
 * [IllustrationPreloadSession]), serializes runs, exposes progress through
 * [state], and is the single entry point for the startup gate, Home,
 * Onboarding and Settings.
 *
 * The persisted repair/fallback markers remain the durability boundary: a
 * process death at any point restarts the same obligation on the next launch.
 */
class CatalogRepairSession(
    private val coordinator: SongDataUpdateCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val mutableState = MutableStateFlow(CatalogRepairState())
    val state: StateFlow<CatalogRepairState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var startupChecked = false
    private var generation = 0L

    /** Run once per process; the gate calls this before rendering navigation. */
    fun ensureStartupRepair() {
        if (startupChecked) return
        startupChecked = true
        launchStartupRepair()
    }

    /** Retry the blocking startup repair after a failure. */
    fun retry() = launchStartupRepair(forceBlocking = true)

    /** Full catalog update (downloads both datasets + illustrations). */
    fun startUpdate(owner: CatalogRepairEntryPoint = CatalogRepairEntryPoint.Settings) =
        launchUpdate(owner)

    /** Reset the retained scene and redownload everything (Settings/gate). */
    fun startResetAndRedownload(owner: CatalogRepairEntryPoint = CatalogRepairEntryPoint.Settings) =
        launchReset(owner)

    /**
     * Explicit user choice when the download cannot finish: serve the bundled
     * catalog read-only. Scores/settings are untouched; the repair obligation
     * stays pending and a later update restores the downloaded catalog.
     */
    fun useBundledFallback() = launchFallback()

    private fun launchStartupRepair(forceBlocking: Boolean = false): CatalogRepairStartResult {
        val blocking = forceBlocking || mutableState.value.blocking
        val run = beginRun(blocking, owner = CatalogRepairEntryPoint.Recovery)
            ?: return CatalogRepairStartResult.AlreadyRunning
        job = scope.launch {
            if (run != generation) return@launch
            AppLogger.event("data", "catalog_repair_started", mapOf("blocking" to blocking.toString()))
            if (coordinator.isUsingBundledCatalogFallback()) {
                // Idempotent: finishes a quarantine interrupted by process death
                // before anyone reads the dataset.
                val finalized = coordinator.useBundledCatalogFallback()
                finalized.fold(
                    onSuccess = { finishDegraded(run) },
                    onFailure = { finishFailure(run, it) }
                )
                return@launch
            }
            val recovered = coordinator.recoverPendingUpdate()
            if (recovered.isFailure) {
                finishFailure(run, recovered.exceptionOrNull())
                return@launch
            }
            if (coordinator.hasPendingRepair()) {
                val updated = coordinator.update(
                    syncIllustrations = true,
                    onFileProgress = { completed, total, name ->
                        publishProgress(run, CatalogRepairStage.Files, completed, total, name)
                    },
                    onIllustrationProgress = { progress ->
                        publishProgress(
                            run,
                            CatalogRepairStage.Illustrations,
                            progress.completed,
                            progress.total,
                            progress.currentSongName
                        )
                    }
                )
                if (updated.isFailure) {
                    val error = updated.exceptionOrNull()
                    if (error is IllustrationSyncPartialException) {
                        finishSuccess(run, error.addedSongNames, error.failures)
                    } else {
                        finishFailure(run, error)
                    }
                    return@launch
                }
            }
            // A marker or scene surviving the update must never be reported as
            // ready: the repair obligation is not discharged until the update
            // engine itself confirms a full commit.
            if (coordinator.hasPendingRepair() || coordinator.hasPendingRecovery()) {
                finishFailure(run, CatalogStorageException())
                return@launch
            }
            finishSuccess(run, emptyList(), 0)
        }
        return CatalogRepairStartResult.Started
    }

    private fun launchUpdate(owner: CatalogRepairEntryPoint): CatalogRepairStartResult {
        val run = beginRun(blocking = false, owner = owner, publishResult = true)
            ?: return CatalogRepairStartResult.AlreadyRunning
        job = scope.launch {
            if (run != generation) return@launch
            AppLogger.event("data", "catalog_update_started")
            val result = coordinator.update(
                syncIllustrations = true,
                onFileProgress = { completed, total, name ->
                    publishProgress(run, CatalogRepairStage.Files, completed, total, name)
                },
                onIllustrationProgress = { progress ->
                    publishProgress(
                        run,
                        CatalogRepairStage.Illustrations,
                        progress.completed,
                        progress.total,
                        progress.currentSongName
                    )
                }
            )
            handleUpdateResult(run, result)
        }
        return CatalogRepairStartResult.Started
    }

    private fun launchReset(owner: CatalogRepairEntryPoint): CatalogRepairStartResult {
        val blocking = mutableState.value.blocking
        val run = beginRun(
            blocking = blocking,
            owner = owner,
            publishResult = true,
            blockOnInterruption = true
        ) ?: return CatalogRepairStartResult.AlreadyRunning
        job = scope.launch {
            if (run != generation) return@launch
            AppLogger.event("data", "catalog_reset_started", mapOf("blocking" to blocking.toString()))
            val result = coordinator.resetPendingUpdate(
                syncIllustrations = true,
                onFileProgress = { completed, total, name ->
                    publishProgress(run, CatalogRepairStage.Files, completed, total, name)
                },
                onIllustrationProgress = { progress ->
                    publishProgress(
                        run,
                        CatalogRepairStage.Illustrations,
                        progress.completed,
                        progress.total,
                        progress.currentSongName
                    )
                }
            )
            handleUpdateResult(run, result, forceBlockingOnFailure = true)
        }
        return CatalogRepairStartResult.Started
    }

    private fun launchFallback(): CatalogRepairStartResult {
        val run = beginRun(
            blocking = true,
            owner = CatalogRepairEntryPoint.Recovery,
            replaceRunning = true,
            blockOnInterruption = true
        ) ?: return CatalogRepairStartResult.AlreadyRunning
        job = scope.launch {
            if (run != generation) return@launch
            AppLogger.event("data", "catalog_fallback_started")
            coordinator.useBundledCatalogFallback().fold(
                onSuccess = { finishDegraded(run) },
                onFailure = { finishFailure(run, it) }
            )
        }
        return CatalogRepairStartResult.Started
    }

    private suspend fun handleUpdateResult(
        run: Long,
        result: Result<SongDataUpdateCoordinator.UpdateOutcome>,
        forceBlockingOnFailure: Boolean = false
    ) {
        if (run != generation) return
        val error = result.exceptionOrNull()
        when {
            error is IllustrationSyncPartialException -> {
                // Both datasets are committed; only artwork is missing.
                finishSuccess(run, error.addedSongNames, error.failures)
            }

            error != null -> finishFailure(run, error, forceBlockingOnFailure)
            coordinator.hasPendingRepair() || coordinator.hasPendingRecovery() -> {
                finishFailure(run, CatalogStorageException(), forceBlockingOnFailure)
            }

            else -> finishSuccess(
                run,
                result.getOrNull()?.addedSongNames.orEmpty(),
                partialIllustrationFailures = 0
            )
        }
    }

    private var cancelMustBlock = false

    private fun beginRun(
        blocking: Boolean,
        owner: CatalogRepairEntryPoint,
        replaceRunning: Boolean = false,
        publishResult: Boolean = false,
        blockOnInterruption: Boolean = false
    ): Long? {
        when (mutableState.value.status) {
            CatalogRepairStatus.Cancelling -> return null
            CatalogRepairStatus.Running -> {
                if (!replaceRunning) return null
                job?.cancel()
            }
            else -> Unit
        }
        val run = ++generation
        cancelMustBlock = blockOnInterruption
        mutableState.value = CatalogRepairState(
            status = CatalogRepairStatus.Running,
            stage = CatalogRepairStage.Checking,
            blocking = blocking,
            resultGeneration = run,
            resultOwner = owner,
            resultAvailable = publishResult,
            degraded = coordinator.isUsingBundledCatalogFallback()
        )
        return run
    }

    /** Stops the active request without allowing an older coroutine to publish later. */
    fun cancelRunning() {
        val current = mutableState.value
        if (current.status != CatalogRepairStatus.Running) return
        val run = ++generation
        val recoveryOnEntry = runCatching { coordinator.hasPendingRecovery() }.getOrDefault(true)
        val blockingOnEntry = current.blocking || cancelMustBlock || recoveryOnEntry
        val runningJob = job
        mutableState.value = current.copy(
            status = CatalogRepairStatus.Cancelling,
            stage = CatalogRepairStage.Checking,
            blocking = blockingOnEntry,
            completed = 0,
            total = 0,
            currentItem = "",
            addedSongNames = emptyList(),
            partialIllustrationFailures = 0,
            failure = null,
            resultGeneration = run,
            resultAvailable = false,
            resultDismissed = false,
            degraded = coordinator.isUsingBundledCatalogFallback()
        )
        runningJob?.cancel()
        job = scope.launch {
            var finalFailure: Throwable? = null
            val finalBlocking = try {
                runningJob?.join()
                val repairRequired = blockingOnEntry || coordinator.hasPendingRecovery()
                if (repairRequired) {
                    coordinator.markRepairRequired().onFailure {
                        finalFailure = it
                        AppLogger.w(TAG, it) { "failed to persist the repair obligation after cancellation" }
                    }
                }
                repairRequired || coordinator.hasPendingRecovery()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                finalFailure = error
                AppLogger.w(TAG, error) { "failed to verify catalog state after cancellation" }
                true
            }
            val degraded = coordinator.isUsingBundledCatalogFallback()
            mutableState.update { state ->
                if (state.resultGeneration != run || state.status != CatalogRepairStatus.Cancelling) {
                    state
                } else {
                    state.copy(
                        status = if (finalBlocking) CatalogRepairStatus.Failed else CatalogRepairStatus.Idle,
                        blocking = finalBlocking,
                        failure = if (finalFailure != null) CatalogRepairFailure.Storage else null,
                        degraded = degraded
                    )
                }
            }
        }
    }

    private fun publishProgress(
        run: Long,
        stage: CatalogRepairStage,
        completed: Int,
        total: Int,
        currentItem: String
    ) {
        mutableState.update { current ->
            if (current.resultGeneration != run || current.status != CatalogRepairStatus.Running) {
                current
            } else {
                current.copy(
                    stage = stage,
                    completed = completed,
                    total = total,
                    currentItem = currentItem
                )
            }
        }
    }

    /** Acknowledges only the current terminal result; the next run is a new event. */
    fun dismissResult() {
        val status = mutableState.value.status
        if (status == CatalogRepairStatus.Running || status == CatalogRepairStatus.Cancelling) return
        mutableState.update { it.copy(resultDismissed = true) }
    }

    private fun finishSuccess(run: Long, addedSongNames: List<String>, partialIllustrationFailures: Int) {
        if (run != generation) return
        val degraded = coordinator.isUsingBundledCatalogFallback()
        AppLogger.event(
            "data",
            "catalog_repair_completed",
            mapOf(
                "partialIllustrations" to partialIllustrationFailures.toString(),
                "degraded" to degraded.toString()
            )
        )
        mutableState.update { current ->
            if (current.resultGeneration != run || current.status != CatalogRepairStatus.Running) {
                current
            } else {
                current.copy(
                    status = CatalogRepairStatus.Succeeded,
                    blocking = false,
                    stage = CatalogRepairStage.Checking,
                    completed = 0,
                    total = 0,
                    currentItem = "",
                    addedSongNames = addedSongNames,
                    partialIllustrationFailures = partialIllustrationFailures,
                    failure = null,
                    degraded = degraded
                )
            }
        }
    }

    private fun finishDegraded(run: Long) {
        if (run != generation) return
        val degraded = coordinator.isUsingBundledCatalogFallback()
        AppLogger.event("data", "catalog_repair_degraded")
        mutableState.update { current ->
            if (current.resultGeneration != run || current.status != CatalogRepairStatus.Running) {
                current
            } else {
                current.copy(
                    status = CatalogRepairStatus.Degraded,
                    blocking = false,
                    stage = CatalogRepairStage.Checking,
                    completed = 0,
                    total = 0,
                    currentItem = "",
                    addedSongNames = emptyList(),
                    partialIllustrationFailures = 0,
                    failure = null,
                    degraded = degraded
                )
            }
        }
    }

    private suspend fun finishFailure(
        run: Long,
        error: Throwable?,
        forceBlocking: Boolean = false
    ) {
        if (run != generation) return
        val failure = error.toCatalogRepairFailure()
        // Scene/rollback failures and every reset failure may leave mixed files
        // or no trusted recovery scene. Never rely on the entry state's flag.
        val blocking = forceBlocking ||
            mutableState.value.blocking ||
            failure == CatalogRepairFailure.Scene ||
            failure == CatalogRepairFailure.Storage
        // A partially failed run may have committed (and discharged the marker
        // of) one dataset. While the gate is blocking, both datasets must stay
        // unrecovered, so re-persist the obligation for a later launch.
        if (blocking) {
            coordinator.markRepairRequired().onFailure { markerFailure ->
                AppLogger.w(TAG, markerFailure) { "failed to persist the repair obligation" }
            }
        }
        // markRepairRequired can suspend while a fallback/reset replaces this
        // run. The conditional StateFlow update below is the final commit gate.
        val degraded = coordinator.isUsingBundledCatalogFallback()
        AppLogger.w(TAG, error ?: IllegalStateException("catalog repair failed")) {
            "catalog repair failed (${failure.name})"
        }
        AppLogger.event(
            "data",
            "catalog_repair_failed",
            mapOf(
                "kind" to failure.name.lowercase(),
                "blocking" to blocking.toString(),
                "degraded" to degraded.toString()
            )
        )
        mutableState.update { current ->
            if (current.resultGeneration != run || current.status != CatalogRepairStatus.Running) {
                current
            } else {
                current.copy(
                    status = CatalogRepairStatus.Failed,
                    stage = CatalogRepairStage.Checking,
                    blocking = blocking,
                    completed = 0,
                    total = 0,
                    currentItem = "",
                    addedSongNames = emptyList(),
                    partialIllustrationFailures = 0,
                    failure = failure,
                    degraded = degraded
                )
            }
        }
    }

    private companion object {
        const val TAG = "CatalogRepairSession"
    }
}

/** Maps an internal failure to the only categories user-facing copy knows about. */
fun Throwable?.toCatalogRepairFailure(): CatalogRepairFailure = when (this) {
    is CatalogDownloadException -> CatalogRepairFailure.Network
    is StagedUpdateBlockedException -> CatalogRepairFailure.Scene
    is StagedRollbackException -> CatalogRepairFailure.Storage
    is CatalogStorageException -> CatalogRepairFailure.Storage
    is UpdateAlreadyInProgressException -> CatalogRepairFailure.Busy
    else -> CatalogRepairFailure.Unknown
}
