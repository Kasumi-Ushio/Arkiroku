package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal class B30ExportRunState<T : Any> {
    var result by mutableStateOf<T?>(null)
        private set
    var isGenerating by mutableStateOf(true)
        private set
    var generationFailed by mutableStateOf(false)
        private set
    var retryToken by mutableIntStateOf(0)
        private set

    private var generationCounter = 0

    fun beginRun(): Int {
        generationCounter++
        result = null
        isGenerating = true
        generationFailed = false
        return generationCounter
    }

    fun completeRun(runId: Int, runResult: Result<T>): Boolean {
        if (runId != generationCounter) return false
        result = runResult.getOrNull()
        generationFailed = runResult.isFailure
        isGenerating = false
        return true
    }

    fun retry() {
        retryToken++
    }
}

internal suspend fun <D, T : Any> runB30ExportEffect(
    data: D?,
    state: B30ExportRunState<T>,
    onStarted: () -> Unit = {},
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
    runner: suspend (D) -> T
): Boolean {
    if (data == null) return false
    val runId = state.beginRun()
    onStarted()
    val runResult = runCatching { runner(data) }
    runResult.exceptionOrNull()?.let { failure ->
        if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
        onFailure(failure)
    }
    currentCoroutineContext().ensureActive()
    if (!state.completeRun(runId, runResult)) return false
    runResult.getOrNull()?.let(onSuccess)
    return true
}
