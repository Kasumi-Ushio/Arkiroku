package org.kasumi321.ushio.phitracker.data.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import org.kasumi321.ushio.phitracker.domain.model.LegacySyncHistoryImportResult

internal class LegacySyncHistoryWriter(
    private val database: AppDatabase,
    private val legacyDao: LegacySyncHistoryDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val currentAccountDao: CurrentAccountDao
) {
    suspend fun import(expectedOwner: String): LegacySyncHistoryImportResult =
        database.useWriterConnection {
            it.immediateTransaction {
                checkCurrentOwner(expectedOwner)
                val snapshots = legacyDao.getSnapshots()
                val entries = legacyDao.getEntries()
                if (snapshots.isEmpty() && entries.isEmpty()) {
                    return@immediateTransaction LegacySyncHistoryImportResult(0, 0)
                }

                val snapshotIds = snapshots.mapTo(mutableSetOf()) { snapshot -> snapshot.id }
                check(snapshotIds.size == snapshots.size) { "Duplicate legacy snapshot id" }
                check(entries.all { entry -> entry.snapshotId in snapshotIds }) {
                    "Legacy history contains an orphan snapshot reference"
                }

                val snapshotIdMap = mutableMapOf<Long, Long>()
                snapshots.forEach { snapshot ->
                    val newId = syncSnapshotDao.insertAndGetId(
                        SyncSnapshotEntity(
                            ownerPlayerId = expectedOwner,
                            timestamp = snapshot.timestamp,
                            rks = snapshot.rks,
                            nickname = snapshot.nickname,
                            dataCount = snapshot.dataCount,
                            lastSyncedSongId = snapshot.lastSyncedSongId,
                            lastSyncedDifficulty = snapshot.lastSyncedDifficulty,
                            lastSyncedScore = snapshot.lastSyncedScore,
                            lastSyncedAccuracy = snapshot.lastSyncedAccuracy
                        )
                    )
                    snapshotIdMap[snapshot.id] = newId
                }
                songSyncHistoryDao.insertAll(
                    entries.map { entry ->
                        SongSyncHistoryEntity(
                            ownerPlayerId = expectedOwner,
                            snapshotId = snapshotIdMap.getValue(entry.snapshotId),
                            songId = entry.songId,
                            difficulty = entry.difficulty,
                            score = entry.score,
                            accuracy = entry.accuracy,
                            isFullCombo = entry.isFullCombo,
                            timestamp = entry.timestamp
                        )
                    }
                )
                legacyDao.deleteAllEntries()
                legacyDao.deleteAllSnapshots()
                LegacySyncHistoryImportResult(snapshots.size, entries.size)
            }
        }

    suspend fun discard(expectedOwner: String) {
        database.useWriterConnection {
            it.immediateTransaction {
                checkCurrentOwner(expectedOwner)
                legacyDao.deleteAllEntries()
                legacyDao.deleteAllSnapshots()
            }
        }
    }

    private suspend fun checkCurrentOwner(expectedOwner: String) {
        check(currentAccountDao.get()?.playerId == expectedOwner) {
            "Session superseded during legacy history operation"
        }
    }
}
