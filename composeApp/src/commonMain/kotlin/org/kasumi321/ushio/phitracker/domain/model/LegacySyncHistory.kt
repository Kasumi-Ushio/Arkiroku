package org.kasumi321.ushio.phitracker.domain.model

data class LegacySyncHistorySummary(
    val snapshotCount: Long,
    val entryCount: Long,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?
)

data class LegacySyncHistoryImportResult(
    val importedSnapshotCount: Int,
    val importedEntryCount: Int
)
