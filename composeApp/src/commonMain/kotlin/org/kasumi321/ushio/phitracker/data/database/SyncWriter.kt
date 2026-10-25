package org.kasumi321.ushio.phitracker.data.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import org.kasumi321.ushio.phitracker.data.repository.SyncCommitData
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult

internal class SyncWriter(
    private val database: AppDatabase,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val currentAccountDao: CurrentAccountDao
) {
    /**
     * Commits one sync atomically: the owner's records and user row are
     * replaced, a snapshot plus per-song history is appended when the same
     * owner's previous records actually changed, and the current-account
     * pointer moves to the committed owner — all inside one transaction.
     *
     * [expectedCurrentOwner] is the pointer as observed when the session
     * operation began. A mismatch means another session change already won
     * the race, so this (late) network result must not write: the commit is
     * abandoned before touching any row. Refresh diffs are always computed
     * against the committing owner's own earlier records only — a save is
     * never diffed against another account's data.
     */
    suspend fun commit(data: SyncCommitData, mode: SyncMode, expectedCurrentOwner: String?): SyncSaveResult =
        database.useWriterConnection {
            it.immediateTransaction {
                val storedOwner = currentAccountDao.get()?.playerId
                if (storedOwner != expectedCurrentOwner) {
                    error("Session superseded during sync; commit abandoned")
                }

                val owner = data.user.playerId
                val oldRecords = if (mode == SyncMode.Refresh) {
                    recordDao.getAllRecordsOnce(owner)
                } else {
                    emptyList()
                }
                val changedEntries = if (oldRecords.isEmpty()) {
                    emptyList()
                } else {
                    data.changedEntriesComparedTo(oldRecords)
                }

                userDao.insertOrUpdate(data.user)
                recordDao.deleteByOwner(owner)
                recordDao.insertAll(data.records)

                if (changedEntries.isNotEmpty()) {
                    val snapshotId = syncSnapshotDao.insertAndGetId(data.snapshot)
                    songSyncHistoryDao.insertAll(changedEntries.map { entry -> entry.copy(snapshotId = snapshotId) })
                }

                currentAccountDao.setCurrent(CurrentAccountEntity(owner))

                SyncSaveResult(
                    save = data.save,
                    committedAt = data.committedAt,
                    changedEntryCount = changedEntries.size,
                    snapshotCreated = changedEntries.isNotEmpty()
                )
            }
        }
}

/**
 * Removes the current account's rows — records, user, snapshots, per-song
 * history — together with the current-account pointer, in one transaction, so
 * a half-cleaned logout state can never be observed.
 */
internal class AccountDataClearer(
    private val database: AppDatabase,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val currentAccountDao: CurrentAccountDao
) {
    /** Returns the cleared playerId, or null when no account was active. */
    suspend fun clearCurrentAccount(): String? = database.useWriterConnection {
        it.immediateTransaction {
            val owner = currentAccountDao.get()?.playerId ?: return@immediateTransaction null
            recordDao.deleteByOwner(owner)
            userDao.deleteByPlayer(owner)
            syncSnapshotDao.deleteByOwner(owner)
            songSyncHistoryDao.deleteByOwner(owner)
            currentAccountDao.clear()
            owner
        }
    }
}
