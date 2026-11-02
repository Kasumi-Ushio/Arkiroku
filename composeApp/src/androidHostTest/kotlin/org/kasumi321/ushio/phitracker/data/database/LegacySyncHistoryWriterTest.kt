package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LegacySyncHistoryWriterTest {
    private lateinit var database: AppDatabase
    private lateinit var writer: LegacySyncHistoryWriter

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            klass = AppDatabase::class.java
        ).setQueryCoroutineContext(Dispatchers.Default).build()
        writer = LegacySyncHistoryWriter(
            database,
            database.legacySyncHistoryDao(),
            database.syncSnapshotDao(),
            database.songSyncHistoryDao(),
            database.currentAccountDao()
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun importRemapsEveryIdConsumesQuarantineAndIsIdempotent() = runTest {
        activate("owner-a")
        database.syncSnapshotDao().insertAndGetId(
            SyncSnapshotEntity(id = 1, ownerPlayerId = "owner-a", timestamp = 999, rks = 1f, nickname = "Existing", dataCount = 1, lastSyncedSongId = null, lastSyncedDifficulty = null, lastSyncedScore = null, lastSyncedAccuracy = null)
        )
        database.songSyncHistoryDao().insertAll(
            listOf(SongSyncHistoryEntity(id = 1, ownerPlayerId = "owner-a", snapshotId = 1, songId = "existing.0", difficulty = "EZ", score = 1, accuracy = 1f, isFullCombo = false, timestamp = 999))
        )
        seedLegacyHistory()

        val stats = database.legacySyncHistoryDao().observeSnapshotStats().first()
        assertEquals(2, stats.snapshotCount)
        assertEquals(100, stats.oldestTimestamp)
        assertEquals(200, stats.newestTimestamp)
        assertEquals(2, database.legacySyncHistoryDao().observeEntryCount().first())

        val imported = writer.import("owner-a")

        assertEquals(2, imported.importedSnapshotCount)
        assertEquals(2, imported.importedEntryCount)
        val snapshots = database.syncSnapshotDao().getAllOnce("owner-a")
        assertEquals(3, snapshots.size)
        val first = snapshots.single { it.timestamp == 100L }
        val second = snapshots.single { it.timestamp == 200L }
        assertEquals(2L, first.id)
        assertEquals(3L, second.id)
        val entries = database.songSyncHistoryDao().getRecent("owner-a", 10)
        assertEquals(3, entries.size)
        assertEquals(setOf(first.id, second.id), entries.filter { it.timestamp != 999L }.map { it.snapshotId }.toSet())
        assertTrue(database.syncSnapshotDao().getAllOnce("owner-b").isEmpty())
        assertTrue(database.legacySyncHistoryDao().getSnapshots().isEmpty())
        assertTrue(database.legacySyncHistoryDao().getEntries().isEmpty())

        val repeated = writer.import("owner-a")
        assertEquals(0, repeated.importedSnapshotCount)
        assertEquals(0, repeated.importedEntryCount)
        assertEquals(3, database.syncSnapshotDao().getAllOnce("owner-a").size)
    }

    @Test
    fun orphanReferenceRollsBackFormalRowsAndKeepsQuarantine() = runTest {
        activate("owner-a")
        database.legacySyncHistoryDao().insertSnapshots(listOf(legacySnapshot(1, 100)))
        database.legacySyncHistoryDao().insertEntries(listOf(legacyEntry(2, 99)))

        assertFailsWith<IllegalStateException> { writer.import("owner-a") }

        assertTrue(database.syncSnapshotDao().getAllOnce("owner-a").isEmpty())
        assertEquals(1, database.legacySyncHistoryDao().getSnapshots().size)
        assertEquals(1, database.legacySyncHistoryDao().getEntries().size)
    }

    @Test
    fun ownerMismatchCannotImportOrDiscard() = runTest {
        activate("owner-a")
        seedLegacyHistory()

        assertFailsWith<IllegalStateException> { writer.import("owner-b") }
        assertFailsWith<IllegalStateException> { writer.discard("owner-b") }

        assertEquals(2, database.legacySyncHistoryDao().getSnapshots().size)
        assertEquals(2, database.legacySyncHistoryDao().getEntries().size)
        assertTrue(database.syncSnapshotDao().getAllOnce("owner-b").isEmpty())
    }

    @Test
    fun discardClearsOnlyQuarantineAndLogoutPreservesIt() = runTest {
        activate("owner-a")
        database.syncSnapshotDao().insertAndGetId(
            SyncSnapshotEntity(ownerPlayerId = "owner-a", timestamp = 999, rks = 1f, nickname = "Existing", dataCount = 1, lastSyncedSongId = null, lastSyncedDifficulty = null, lastSyncedScore = null, lastSyncedAccuracy = null)
        )
        seedLegacyHistory()
        val clearer = AccountDataClearer(
            database,
            database.recordDao(),
            database.userDao(),
            database.syncSnapshotDao(),
            database.songSyncHistoryDao(),
            database.currentAccountDao()
        )

        clearer.clearCurrentAccount()

        assertEquals(2, database.legacySyncHistoryDao().getSnapshots().size)
        assertEquals(2, database.legacySyncHistoryDao().getEntries().size)
        assertTrue(database.syncSnapshotDao().getAllOnce("owner-a").isEmpty())

        activate("owner-a")
        writer.discard("owner-a")

        assertTrue(database.legacySyncHistoryDao().getSnapshots().isEmpty())
        assertTrue(database.legacySyncHistoryDao().getEntries().isEmpty())
    }

    private suspend fun activate(owner: String) {
        database.currentAccountDao().setCurrent(CurrentAccountEntity(owner))
    }

    private suspend fun seedLegacyHistory() {
        database.legacySyncHistoryDao().insertSnapshots(
            listOf(legacySnapshot(1, 100), legacySnapshot(7, 200))
        )
        database.legacySyncHistoryDao().insertEntries(
            listOf(legacyEntry(1, 1), legacyEntry(8, 7))
        )
    }

    private fun legacySnapshot(id: Long, timestamp: Long) = LegacySyncSnapshotEntity(
        id = id,
        timestamp = timestamp,
        rks = 15.5f,
        nickname = "Legacy",
        dataCount = 1,
        lastSyncedSongId = "song-$id.0",
        lastSyncedDifficulty = "IN",
        lastSyncedScore = 950000,
        lastSyncedAccuracy = 95f
    )

    private fun legacyEntry(id: Long, snapshotId: Long) = LegacySongSyncHistoryEntity(
        id = id,
        snapshotId = snapshotId,
        songId = "song-$snapshotId.0",
        difficulty = "IN",
        score = 950000,
        accuracy = 95f,
        isFullCombo = false,
        timestamp = snapshotId * 100
    )
}
