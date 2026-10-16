package org.kasumi321.ushio.phitracker.data.logging

class LoggingState(
    val store: LogFileStore,
    val isDebugBuild: Boolean,
)

fun createLoggingState(isDebugBuild: Boolean): LoggingState {
    val fileSystem = LogPathProvider.fileSystem
    val runtimeDir = LogPathProvider.runtimeLogDir()
    val crashDir = LogPathProvider.crashLogDir()

    val store = LogFileStore(
        fileSystem = fileSystem,
        runtimeLogDir = runtimeDir,
        crashLogDir = crashDir,
    )

    AppLogger.init(
        LoggerConfig(
            isDebugBuild = isDebugBuild,
            logFileStore = store,
        )
    )

    CrashHookInstaller.install(store)

    val state = LoggingState(store = store, isDebugBuild = isDebugBuild)
    LoggingStateHolder.state = state
    return state
}
