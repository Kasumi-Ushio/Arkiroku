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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository

enum class IllustrationPreloadRunStatus {
    Idle,
    Running,
    Succeeded,
    Failed
}

data class IllustrationPreloadSessionState(
    val status: IllustrationPreloadRunStatus = IllustrationPreloadRunStatus.Idle,
    val completed: Int = 0,
    val total: Int = 0,
    val currentSong: String? = null
)

/**
 * Application-scoped owner of an illustration preload run. The coordinator is
 * stateless, so keeping the job in a screen view-model would cancel it as soon
 * as that screen is destroyed. The onboarding wizard can therefore leave the
 * preload step or finish entirely while the download keeps running; the state
 * flow lets a returning screen restore progress.
 *
 * Flags stay owned by the session: a fully successful run writes
 * `preloadDone` (and clears `illustrationPreloadDeclined`); [cancel] and
 * [decline] write `illustrationPreloadDeclined`. Leaving the step without
 * cancelling writes nothing.
 */
class IllustrationPreloadSession(
    private val coordinator: IllustrationPreloadCoordinator,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val mutableState = MutableStateFlow(IllustrationPreloadSessionState())
    val state: StateFlow<IllustrationPreloadSessionState> = mutableState.asStateFlow()

    private var job: Job? = null

    // Serializes the terminal flag writes against decline(), so a completion
    // racing a cancel can never clear illustrationPreloadDeclined afterwards.
    private val flagMutex = Mutex()

    // Invalidates terminal/persist work from a run that was cancelled or
    // superseded, so a late completion can never flip the flags after decline.
    private var generation = 0L

    fun start() {
        if (mutableState.value.status == IllustrationPreloadRunStatus.Running) return
        job?.cancel()
        val runGeneration = ++generation
        AppLogger.event("cache", "illustration_preload_started")
        mutableState.value = IllustrationPreloadSessionState(
            status = IllustrationPreloadRunStatus.Running
        )
        job = scope.launch {
            val result = coordinator.preloadLowRes { done, total, currentSong ->
                if (runGeneration != generation) return@preloadLowRes
                mutableState.update {
                    it.copy(completed = done, total = total, currentSong = currentSong)
                }
            }
            if (runGeneration != generation) return@launch
            if (result.isFullySuccessful) {
                flagMutex.withLock {
                    if (runGeneration != generation) return@launch
                    runCatching {
                        settingsRepository.setPreloadDone(true)
                        settingsRepository.setIllustrationPreloadDeclined(false)
                    }.onFailure { error ->
                        AppLogger.event(
                            "cache",
                            "illustration_preload_flag_write_failed",
                            mapOf("error" to (error.message ?: "unknown")),
                            throwable = error
                        )
                    }
                }
                if (runGeneration != generation) return@launch
                AppLogger.event(
                    "cache",
                    "illustration_preload_completed",
                    mapOf("total" to result.completed.toString())
                )
                mutableState.update {
                    it.copy(status = IllustrationPreloadRunStatus.Succeeded)
                }
            } else {
                AppLogger.event(
                    "cache",
                    "illustration_preload_failed",
                    mapOf(
                        "completed" to result.completed.toString(),
                        "failed" to result.failed.toString()
                    )
                )
                mutableState.update {
                    it.copy(status = IllustrationPreloadRunStatus.Failed)
                }
            }
        }
    }

    /** Cancel a running run (or dismiss a failed one) as "later". */
    fun cancel() {
        val status = mutableState.value.status
        if (status != IllustrationPreloadRunStatus.Running &&
            status != IllustrationPreloadRunStatus.Failed
        ) {
            return
        }
        decline()
    }

    /** Resolve the step as "later": stop any run and persist the declined marker. */
    fun decline() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = IllustrationPreloadSessionState()
        scope.launch {
            flagMutex.withLock {
                settingsRepository.setIllustrationPreloadDeclined(true)
            }
        }
    }
}
