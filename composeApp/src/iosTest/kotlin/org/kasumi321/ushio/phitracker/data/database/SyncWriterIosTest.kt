package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.repository.SyncCommitData
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalForeignApi::class)
class SyncWriterIosTest {
    @Test
    fun productionSyncWriterRollsBackAllFourTablesAfterRealHistoryInsert(): Unit = runTest {
        val path = NSTemporaryDirectory() + "phi-tracker-sync-${NSUUID().UUIDString}.db"
        val database = Room.databaseBuilder<AppDatabase>(path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            val recordDao = database.recordDao()
            val userDao = database.userDao()
            val snapshotDao = database.syncSnapshotDao()
            val historyDao = database.songSyncHistoryDao()
            val currentAccountDao = database.currentAccountDao()
            val originalUser = UserEntity("new-player", "Old", "", "", "", 10f, 1, 1, "CN", 100L)
            val originalRecord = RecordEntity("new-player", "old-song.0", "EZ", 800_000, 80f, false, 100L)
            userDao.insertOrUpdate(originalUser)
            recordDao.insertAll(listOf(originalRecord))
            currentAccountDao.setCurrent(CurrentAccountEntity("new-player"))
            val originalSnapshotId = snapshotDao.insertAndGetId(snapshot("new-player", 100L, "Old"))
            historyDao.insertAll(
                listOf(history("new-player", originalSnapshotId, "old-song.0", "EZ", 800_000, 80f, false, 100L))
            )
            val beforeUser = userDao.getUserOnce("new-player")
            val beforeRecords = recordDao.getAllRecordsOnce("new-player")
            val beforeSnapshots = snapshotDao.getAllOnce("new-player")
            val beforeHistory = historyDao.getRecent("new-player", 100)
            val throwingHistoryDao = object : SongSyncHistoryDao by historyDao {
                override suspend fun insertAll(entries: List<SongSyncHistoryEntity>) {
                    historyDao.insertAll(entries)
                    error("injected after real history insert")
                }
            }
            val writer = SyncWriter(database, recordDao, userDao, snapshotDao, throwingHistoryDao, currentAccountDao)

            assertFailsWith<IllegalStateException> {
                writer.commit(commitData(), SyncMode.Refresh, expectedCurrentOwner = "new-player")
            }

            assertEquals(beforeUser, userDao.getUserOnce("new-player"))
            assertEquals(beforeRecords, recordDao.getAllRecordsOnce("new-player"))
            assertEquals(beforeSnapshots, snapshotDao.getAllOnce("new-player"))
            assertEquals(beforeHistory, historyDao.getRecent("new-player", 100))
            assertEquals("new-player", currentAccountDao.get()?.playerId)
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            NSFileManager.defaultManager.removeItemAtPath("$path-wal", error = null)
            NSFileManager.defaultManager.removeItemAtPath("$path-shm", error = null)
        }
    }

    private fun commitData() = SyncCommitData(
        save = Save(
            gameRecord = emptyMap(),
            gameProgress = GameProgress(
                false, false, false, false, "", 0, 0, emptyList(),
                0, 0, 0, 0, null, null, null, null, null
            ),
            user = UserSettings(false, "", "", ""),
            summary = null
        ),
        committedAt = 200L,
        user = UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L),
        records = listOf(RecordEntity("new-player", "new-song.0", "IN", 950_000, 95f, true, 200L)),
        snapshot = snapshot("new-player", 200L, "New")
    )

    private fun snapshot(ownerPlayerId: String, timestamp: Long, nickname: String) = SyncSnapshotEntity(
        ownerPlayerId = ownerPlayerId,
        timestamp = timestamp,
        rks = 15f,
        nickname = nickname,
        dataCount = 1,
        lastSyncedSongId = "new-song.0",
        lastSyncedDifficulty = "IN",
        lastSyncedScore = 950_000,
        lastSyncedAccuracy = 95f
    )

    private fun history(
        ownerPlayerId: String,
        snapshotId: Long,
        songId: String,
        difficulty: String,
        score: Int,
        accuracy: Float,
        isFullCombo: Boolean,
        timestamp: Long
    ) = SongSyncHistoryEntity(
        ownerPlayerId = ownerPlayerId,
        snapshotId = snapshotId,
        songId = songId,
        difficulty = difficulty,
        score = score,
        accuracy = accuracy,
        isFullCombo = isFullCombo,
        timestamp = timestamp
    )
}
