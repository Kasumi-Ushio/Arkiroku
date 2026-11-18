package org.kasumi321.ushio.phitracker.data.database

import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.createPlatformPaths
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class LegacySyncHistoryWriterIosTest {
    private val path = createPlatformPaths().filesDir + "/phi_tracker.db"

    @BeforeTest
    fun setUp() {
        // Keep production-factory coverage, but create the container directory
        // that an installed app would normally receive from iOS.
        check(
            NSFileManager.defaultManager.createDirectoryAtPath(
                createPlatformPaths().filesDir,
                true,
                null,
                null
            )
        ) { "Unable to create the iOS test Documents directory" }
    }

    @AfterTest
    fun tearDown() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        NSFileManager.defaultManager.removeItemAtPath("$path-wal", error = null)
        NSFileManager.defaultManager.removeItemAtPath("$path-shm", error = null)
    }

    @Test
    fun importRemapsIdsAndConsumesQuarantine() = runTest {
        tearDown()
        val database = createAppDatabase()
        try {
            database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))
            database.syncSnapshotDao().insertAndGetId(
                SyncSnapshotEntity(id = 1, ownerPlayerId = "owner-a", timestamp = 999, rks = 1f, nickname = "Existing", dataCount = 1, lastSyncedSongId = null, lastSyncedDifficulty = null, lastSyncedScore = null, lastSyncedAccuracy = null)
            )
            database.legacySyncHistoryDao().insertSnapshots(
                listOf(LegacySyncSnapshotEntity(7, 100, 15.5f, "Legacy", 1, "song.0", "IN", 950000, 95f))
            )
            database.legacySyncHistoryDao().insertEntries(
                listOf(LegacySongSyncHistoryEntity(9, 7, "song.0", "IN", 950000, 95f, false, 100))
            )
            val writer = LegacySyncHistoryWriter(
                database,
                database.legacySyncHistoryDao(),
                database.syncSnapshotDao(),
                database.songSyncHistoryDao(),
                database.currentAccountDao()
            )

            val result = writer.import("owner-a")

            assertEquals(1, result.importedSnapshotCount)
            assertEquals(1, result.importedEntryCount)
            val imported = database.syncSnapshotDao().getAllOnce("owner-a").single { it.timestamp == 100L }
            assertEquals(2L, imported.id)
            assertEquals(imported.id, database.songSyncHistoryDao().getRecent("owner-a", 1).single().snapshotId)
            assertTrue(database.legacySyncHistoryDao().getSnapshots().isEmpty())
            assertTrue(database.legacySyncHistoryDao().getEntries().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun orphanReferenceRollsBackAndKeepsQuarantine() = runTest {
        tearDown()
        val database = createAppDatabase()
        try {
            database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))
            database.legacySyncHistoryDao().insertSnapshots(
                listOf(LegacySyncSnapshotEntity(1, 100, 15.5f, "Legacy", 1, null, null, null, null))
            )
            database.legacySyncHistoryDao().insertEntries(
                listOf(LegacySongSyncHistoryEntity(2, 99, "song.0", "IN", 950000, 95f, false, 100))
            )
            val writer = LegacySyncHistoryWriter(
                database,
                database.legacySyncHistoryDao(),
                database.syncSnapshotDao(),
                database.songSyncHistoryDao(),
                database.currentAccountDao()
            )

            assertFailsWith<IllegalStateException> { writer.import("owner-a") }

            assertTrue(database.syncSnapshotDao().getAllOnce("owner-a").isEmpty())
            assertEquals(1, database.legacySyncHistoryDao().getSnapshots().size)
            assertEquals(1, database.legacySyncHistoryDao().getEntries().size)
        } finally {
            database.close()
        }
    }
}
