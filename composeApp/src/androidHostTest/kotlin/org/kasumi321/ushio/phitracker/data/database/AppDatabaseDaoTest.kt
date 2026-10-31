package org.kasumi321.ushio.phitracker.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.data.repository.SyncCommitData
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppDatabaseDaoTest {
    private lateinit var database: AppDatabase

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            klass = AppDatabase::class.java
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordDaoStatsUseRealRoomQueriesScopedToOwner(): Unit = runTest {
        val dao = database.recordDao()
        dao.insertAll(
            listOf(
                RecordEntity("owner-a", "song-a.0", "EZ", 1_000_000, 100f, true, 1L),
                RecordEntity("owner-a", "song-a.0", "HD", 0, 0f, false, 1L),
                RecordEntity("owner-a", "song-b.0", "EZ", 900_000, 95f, false, 1L),
                RecordEntity("owner-b", "song-c.0", "IN", 1_000_000, 100f, true, 1L)
            )
        )

        assertEquals(2, dao.getDistinctSongCount("owner-a"))
        assertEquals(2, dao.getClearCountByDifficulty("owner-a", "EZ"))
        assertEquals(1, dao.getTotalFcCount("owner-a"))
        assertEquals(1, dao.getTotalPhiCount("owner-a"))
        assertEquals(0, dao.getClearCountByDifficulty("owner-b", "EZ"))
        assertEquals(1, dao.getTotalPhiCount("owner-b"))
        assertEquals(listOf("song-c.0"), dao.getRecordsBySong("owner-b", "song-c.0").map { it.songId })
    }

    @Test
    fun recordDaoDeleteByOwnerLeavesOtherAccountsIntact(): Unit = runTest {
        val dao = database.recordDao()
        dao.insertAll(
            listOf(
                RecordEntity("owner-a", "song-a.0", "EZ", 1_000_000, 100f, true, 1L),
                RecordEntity("owner-b", "song-b.0", "EZ", 900_000, 95f, false, 1L)
            )
        )

        dao.deleteByOwner("owner-a")

        assertEquals(0, dao.getRecordCount("owner-a"))
        assertEquals(1, dao.getRecordCount("owner-b"))
    }

    @Test
    fun syncSnapshotDaoUsesRealRoomOrderingAndGeneratedIds(): Unit = runTest {
        val dao = database.syncSnapshotDao()
        val oldId = dao.insertAndGetId(snapshot("owner-a", timestamp = 1L, nickname = "old"))
        val newId = dao.insertAndGetId(snapshot("owner-a", timestamp = 2L, nickname = "new"))

        assertEquals(oldId + 1, newId)
        assertEquals("new", dao.getLatest("owner-a")?.nickname)
        assertEquals(listOf("new", "old"), dao.getAll("owner-a").first().map { it.nickname })
        assertEquals(0, dao.getAllOnce("owner-b").size)
    }

    @Test
    fun songSyncHistoryDaoUsesRealRoomFiltersAndOrdering(): Unit = runTest {
        val snapshotId = database.syncSnapshotDao().insertAndGetId(snapshot("owner-a", timestamp = 1L, nickname = "sync"))
        val dao = database.songSyncHistoryDao()
        val entries = listOf(
            history("owner-a", snapshotId, "song-a.0", "EZ", 900_000, 90f, false, 1L),
            history("owner-a", snapshotId, "song-a.0", "IN", 1_000_000, 100f, true, 3L),
            history("owner-a", snapshotId, "song-b.0", "HD", 950_000, 95f, true, 2L)
        )
        dao.insertAll(entries)

        assertEquals(listOf("IN", "EZ"), dao.getBySongId("owner-a", "song-a.0").first().map { it.difficulty })
        assertEquals(listOf("IN"), dao.getRecentBySongId("owner-a", "song-a.0", limit = 1).map { it.difficulty })
        assertEquals(listOf("HD", "IN", "EZ"), dao.getBySnapshotId("owner-a", snapshotId).map { it.difficulty })
        assertEquals(listOf("IN", "HD"), dao.getRecent("owner-a", limit = 2).map { it.difficulty })
        assertEquals(0, dao.getRecent("owner-b", limit = 2).size)
    }

    @Test
    fun syncWriterRollsBackAllFourTablesWhenHistoryInsertThrows(): Unit = runTest {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val dbName = "sync_writer_rollback_test.db"
        context.deleteDatabase(dbName)
        var rollbackDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            val recordDao = rollbackDatabase.recordDao()
            val userDao = rollbackDatabase.userDao()
            val snapshotDao = rollbackDatabase.syncSnapshotDao()
            val historyDao = rollbackDatabase.songSyncHistoryDao()
            val currentAccountDao = rollbackDatabase.currentAccountDao()
            val originalUser = UserEntity("new-player", "Old", "", "", "", 10f, 1, 1, "CN", 100L)
            val originalRecord = RecordEntity("new-player", "old-song.0", "EZ", 800_000, 80f, false, 100L)
            userDao.insertOrUpdate(originalUser)
            recordDao.insertAll(listOf(originalRecord))
            currentAccountDao.setCurrent(CurrentAccountEntity("new-player"))
            val originalSnapshot = snapshot("new-player", timestamp = 100L, nickname = "Old")
            val originalSnapshotId = snapshotDao.insertAndGetId(originalSnapshot)
            val originalHistory = history("new-player", originalSnapshotId, "old-song.0", "EZ", 800_000, 80f, false, 100L)
            historyDao.insertAll(listOf(originalHistory))
            val beforeUser = userDao.getUserOnce("new-player")
            val beforeRecords = recordDao.getAllRecordsOnce("new-player")
            val beforeSnapshots = snapshotDao.getAllOnce("new-player")
            val beforeHistory = historyDao.getRecent("new-player", limit = 100)
            val throwingHistoryDao = object : SongSyncHistoryDao by historyDao {
                override suspend fun insertAll(entries: List<SongSyncHistoryEntity>) {
                    historyDao.insertAll(entries)
                    error("injected after real history insert")
                }
            }
            val writer = SyncWriter(rollbackDatabase, recordDao, userDao, snapshotDao, throwingHistoryDao, currentAccountDao)
            val commitData = SyncCommitData(
                save = emptySave(),
                committedAt = 200L,
                user = UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L),
                records = listOf(RecordEntity("new-player", "new-song.0", "IN", 950_000, 95f, true, 200L)),
                snapshot = snapshot("new-player", timestamp = 200L, nickname = "New")
            )

            assertFailsWith<IllegalStateException> {
                writer.commit(commitData, SyncMode.Refresh, expectedCurrentOwner = "new-player")
            }

            rollbackDatabase.close()
            rollbackDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()

            assertEquals(beforeUser, rollbackDatabase.userDao().getUserOnce("new-player"))
            assertEquals(beforeRecords, rollbackDatabase.recordDao().getAllRecordsOnce("new-player"))
            assertEquals(beforeSnapshots, rollbackDatabase.syncSnapshotDao().getAllOnce("new-player"))
            assertEquals(beforeHistory, rollbackDatabase.songSyncHistoryDao().getRecent("new-player", limit = 100))
            assertEquals("new-player", rollbackDatabase.currentAccountDao().get()?.playerId)
        } finally {
            rollbackDatabase.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun syncWriterBootstrapReplacesPlayerDataWithoutSnapshotOrHistory(): Unit = runTest {
        val originalSnapshotId = database.syncSnapshotDao().insertAndGetId(snapshot("new-player", 100L, "Old"))
        database.songSyncHistoryDao().insertAll(
            listOf(history("new-player", originalSnapshotId, "old-song.0", "EZ", 800_000, 80f, false, 100L))
        )
        val beforeSnapshots = database.syncSnapshotDao().getAllOnce("new-player")
        val beforeHistory = database.songSyncHistoryDao().getRecent("new-player", 100)
        val writer = writer()

        val result = writer.commit(commitData(), SyncMode.Bootstrap, expectedCurrentOwner = null)

        assertEquals(UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L), database.userDao().getUserOnce("new-player"))
        assertEquals(commitData().records, database.recordDao().getAllRecordsOnce("new-player"))
        assertEquals(beforeSnapshots, database.syncSnapshotDao().getAllOnce("new-player"))
        assertEquals(beforeHistory, database.songSyncHistoryDao().getRecent("new-player", 100))
        assertEquals(0, result.changedEntryCount)
        assertEquals(false, result.snapshotCreated)
        assertEquals("new-player", database.currentAccountDao().get()?.playerId)
    }

    @Test
    fun syncWriterPreservesLocalAvatarWhileRefreshingRemoteProfile(): Unit = runTest {
        database.userDao().insertOrUpdate(
            UserEntity(
                "new-player", "Old", "remote-old", "", "", 10f, 1, 1, "CN", 100L,
                moneyString = "1 KiB", avatarUri = "file:///local-avatar.png"
            )
        )
        val data = commitData().copy(
            user = commitData().user.copy(avatar = "remote-new", moneyString = "2 MiB", avatarUri = null)
        )

        writer().commit(data, SyncMode.Refresh, expectedCurrentOwner = null)

        val user = database.userDao().getUserOnce("new-player")
        assertEquals("remote-new", user?.avatar)
        assertEquals("2 MiB", user?.moneyString)
        assertEquals("file:///local-avatar.png", user?.avatarUri)
    }

    @Test
    fun syncWriterUnchangedRefreshDoesNotCreateSnapshotOrHistory(): Unit = runTest {
        database.recordDao().insertAll(
            listOf(RecordEntity("new-player", "new-song.0", "IN", 950_000, 95f, false, 100L))
        )
        val data = commitData().copy(
            records = listOf(RecordEntity("new-player", "new-song.0", "IN", 950_000, 95f, true, 200L))
        )

        val result = writer().commit(data, SyncMode.Refresh, expectedCurrentOwner = null)

        assertEquals(0, result.changedEntryCount)
        assertEquals(false, result.snapshotCreated)
        assertEquals(true, database.recordDao().getAllRecordsOnce("new-player").single().isFullCombo)
        assertEquals(emptyList(), database.syncSnapshotDao().getAllOnce("new-player"))
        assertEquals(emptyList(), database.songSyncHistoryDao().getRecent("new-player", 100))
    }

    @Test
    fun syncWriterChangedRefreshCreatesOneSnapshotAndAssociatedHistory(): Unit = runTest {
        database.recordDao().insertAll(
            listOf(RecordEntity("new-player", "new-song.0", "IN", 900_000, 90f, false, 100L))
        )

        val result = writer().commit(commitData(), SyncMode.Refresh, expectedCurrentOwner = null)

        val snapshot = database.syncSnapshotDao().getAllOnce("new-player").single()
        val history = database.songSyncHistoryDao().getBySnapshotId("new-player", snapshot.id).single()
        assertEquals(1, result.changedEntryCount)
        assertEquals(true, result.snapshotCreated)
        assertEquals("new-song.0", history.songId)
        assertEquals(950_000, history.score)
        assertEquals(200L, history.timestamp)
    }

    @Test
    fun syncWriterAccountSwitchKeepsOtherOwnersDataAndWritesNoHistory(): Unit = runTest {
        // Account A has fully synced data (records + snapshot + history) and is
        // current. A Bootstrap commit as B must move the pointer, seed B's
        // rows, and leave A's data — including its history — completely alone,
        // without diffing B's save against A's records.
        database.recordDao().insertAll(
            listOf(RecordEntity("owner-a", "song-a.0", "EZ", 800_000, 80f, false, 100L))
        )
        val snapshotA = database.syncSnapshotDao().insertAndGetId(snapshot("owner-a", 100L, "A"))
        database.songSyncHistoryDao().insertAll(
            listOf(history("owner-a", snapshotA, "song-a.0", "EZ", 800_000, 80f, false, 100L))
        )
        database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))
        val dataB = commitData(
            user = UserEntity("owner-b", "Bee", "", "", "", 12f, 2, 2, "CN", 200L),
            records = listOf(RecordEntity("owner-b", "new-song.0", "IN", 950_000, 95f, true, 200L))
        )

        val result = writer().commit(dataB, SyncMode.Bootstrap, expectedCurrentOwner = "owner-a")

        assertEquals("owner-b", database.currentAccountDao().get()?.playerId)
        assertEquals(0, result.changedEntryCount)
        assertEquals(false, result.snapshotCreated)
        assertEquals(listOf("song-a.0"), database.recordDao().getAllRecordsOnce("owner-a").map { it.songId })
        assertEquals(1, database.syncSnapshotDao().getAllOnce("owner-a").size)
        assertEquals(0, database.songSyncHistoryDao().getRecent("owner-b", 100).size, "a switching commit never writes history")
    }

    @Test
    fun syncWriterRefreshDiffsOnlyAgainstTheSameOwnersRecords(): Unit = runTest {
        // A returning account (e.g. after a sessionToken reset, pointer on
        // another account) refreshes: the diff must run against its OWN old
        // records and continue its history — this is save inheritance.
        database.recordDao().insertAll(
            listOf(
                RecordEntity("owner-a", "song-a.0", "EZ", 800_000, 80f, false, 100L),
                RecordEntity("owner-p", "new-song.0", "IN", 900_000, 90f, false, 100L)
            )
        )
        database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))

        val result = writer().commit(
            commitData(
                user = UserEntity("owner-p", "Pee", "", "", "", 12f, 2, 2, "CN", 200L),
                records = listOf(RecordEntity("owner-p", "new-song.0", "IN", 950_000, 95f, true, 200L))
            ),
            SyncMode.Refresh,
            expectedCurrentOwner = "owner-a"
        )

        assertEquals(1, result.changedEntryCount)
        assertEquals(true, result.snapshotCreated)
        val snapshot = database.syncSnapshotDao().getAllOnce("owner-p").single()
        assertEquals(listOf("new-song.0"), database.songSyncHistoryDao().getBySnapshotId("owner-p", snapshot.id).map { it.songId })
        assertEquals(listOf("song-a.0"), database.recordDao().getAllRecordsOnce("owner-a").map { it.songId })
    }

    @Test
    fun syncWriterAbandonsCommitWhenThePointerMovedMeanwhile(): Unit = runTest {
        database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))
        val data = commitData(
            user = UserEntity("owner-b", "Bee", "", "", "", 12f, 2, 2, "CN", 200L),
            records = listOf(RecordEntity("owner-b", "new-song.0", "IN", 950_000, 95f, true, 200L))
        )

        // The session captured expected=owner-a, but a newer session change
        // already moved the pointer: this late network result must not write.
        database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-z"))

        assertFailsWith<IllegalStateException> {
            writer().commit(data, SyncMode.Bootstrap, expectedCurrentOwner = "owner-a")
        }
        assertEquals("owner-z", database.currentAccountDao().get()?.playerId)
        assertEquals(0, database.recordDao().getRecordCount("owner-b"))
        assertNull(database.userDao().getUserOnce("owner-b"))
    }

    @Test
    fun accountDataClearerRemovesOnlyTheCurrentAccountsRows(): Unit = runTest {
        seedTwoAccounts()
        val clearer = AccountDataClearer(
            database, database.recordDao(), database.userDao(),
            database.syncSnapshotDao(), database.songSyncHistoryDao(), database.currentAccountDao()
        )

        assertEquals("owner-a", clearer.clearCurrentAccount())

        assertEquals(0, database.recordDao().getRecordCount("owner-a"))
        assertNull(database.userDao().getUserOnce("owner-a"))
        assertEquals(0, database.syncSnapshotDao().getAllOnce("owner-a").size)
        assertEquals(0, database.songSyncHistoryDao().getRecent("owner-a", 100).size)
        assertEquals("owner-b", database.userDao().getUserOnce("owner-b")?.playerId)
        assertEquals(1, database.recordDao().getRecordCount("owner-b"))
        assertEquals(1, database.syncSnapshotDao().getAllOnce("owner-b").size)
        assertNull(database.currentAccountDao().get())

        assertNull(clearer.clearCurrentAccount(), "clearing with no active account is a no-op")
    }

    private suspend fun seedTwoAccounts() {
        database.recordDao().insertAll(
            listOf(
                RecordEntity("owner-a", "song-a.0", "EZ", 800_000, 80f, false, 100L),
                RecordEntity("owner-b", "song-b.0", "HD", 950_000, 95f, true, 100L)
            )
        )
        database.userDao().insertOrUpdate(UserEntity("owner-a", "Ay", "", "", "", 10f, 1, 1, "CN", 100L))
        database.userDao().insertOrUpdate(UserEntity("owner-b", "Bee", "", "", "", 12f, 2, 2, "CN", 100L))
        val snapshotA = database.syncSnapshotDao().insertAndGetId(snapshot("owner-a", 100L, "A"))
        database.songSyncHistoryDao().insertAll(
            listOf(history("owner-a", snapshotA, "song-a.0", "EZ", 800_000, 80f, false, 100L))
        )
        val snapshotB = database.syncSnapshotDao().insertAndGetId(snapshot("owner-b", 110L, "B"))
        database.songSyncHistoryDao().insertAll(
            listOf(history("owner-b", snapshotB, "song-b.0", "HD", 950_000, 95f, true, 110L))
        )
        database.currentAccountDao().setCurrent(CurrentAccountEntity("owner-a"))
    }

    // ── migrations ────────────────────────────────────────────────────

    @Test
    fun migrationFromV1ToV5AttributesSingleUserDataToThatAccount(): Unit = runTest {
        val dbName = "migration_v1_to_v5_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName), null
        )
        rawDb.version = 1
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `records` (
                `songId` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL,
                `isFullCombo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `playerId` TEXT NOT NULL,
                `nickname` TEXT NOT NULL,
                `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL,
                `background` TEXT NOT NULL,
                `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL,
                `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL,
                `lastSyncAt` INTEGER NOT NULL,
                PRIMARY KEY(`playerId`)
            )
        """.trimIndent())
        rawDb.execSQL("INSERT INTO records VALUES ('song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
        rawDb.execSQL("INSERT INTO records VALUES ('song-b.0', 'HD', 900000, 95.0, 0, 1)")
        rawDb.execSQL("INSERT INTO users VALUES ('player1', 'Alice', '', '', '', 15.0, 1, 1, '', 1)")
        rawDb.close()

        val roomDb = migrationDatabase(context, dbName)

        assertEquals(2, roomDb.recordDao().getRecordCount("player1"))
        assertEquals(2, roomDb.recordDao().getDistinctSongCount("player1"))
        assertEquals(1, roomDb.recordDao().getClearCountByDifficulty("player1", "EZ"))
        assertEquals("player1", roomDb.currentAccountDao().get()?.playerId)

        val user = roomDb.userDao().getUserOnce("player1")
        assertNotNull(user)
        assertEquals("Alice", user.nickname)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationFromV2ToV5KeepsRecordsAndDropsUnattributableSnapshots(): Unit = runTest {
        val dbName = "migration_v2_to_v5_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        val rawDb = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        rawDb.version = 2
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `records` (
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `playerId` TEXT NOT NULL, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`playerId`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL,
                `rks` REAL NOT NULL, `nickname` TEXT NOT NULL, `dataCount` INTEGER NOT NULL,
                `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT, `lastSyncedScore` INTEGER,
                `lastSyncedAccuracy` REAL
            )
        """.trimIndent())
        rawDb.execSQL("INSERT INTO records VALUES ('song-v2.0', 'HD', 900000, 95.0, 0, 2)")
        rawDb.execSQL("INSERT INTO users VALUES ('player-v2', 'V2', '', '', '', 15.0, 1, 2, 'CN', 2)")
        rawDb.execSQL("INSERT INTO sync_snapshots VALUES (7, 2, 15.0, 'V2', 1, 'song-v2.0', 'HD', 900000, 95.0)")
        rawDb.close()

        val roomDb = migrationDatabase(context, dbName)

        assertEquals("song-v2.0", roomDb.recordDao().getAllRecordsOnce("player-v2").single().songId)
        assertEquals("V2", roomDb.userDao().getUserOnce("player-v2")?.nickname)
        assertEquals(0, roomDb.syncSnapshotDao().getAllOnce("player-v2").size)
        assertEquals("player-v2", roomDb.currentAccountDao().get()?.playerId)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationFromV3SingleUserKeepsRecordsAndDropsUnattributableHistory(): Unit = runTest {
        val dbName = "migration_v3_single_user_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        seedRawV3Database(dbName) {
            execSQL("INSERT INTO records VALUES ('song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
            execSQL("INSERT INTO users VALUES ('player1', 'Alice', '', '', '', 15.0, 1, 1, 'CN', 1)")
            execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 15.0, 'Alice', 1, 'song-a.0', 'EZ', 1000000, 100.0)")
            execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
        }
        val roomDb = migrationDatabase(context, dbName)

        assertEquals(listOf("song-a.0"), roomDb.recordDao().getAllRecordsOnce("player1").map { it.songId })
        val user = roomDb.userDao().getUserOnce("player1")
        assertEquals("Alice", user?.nickname)
        assertEquals("", user?.moneyString)
        assertNull(user?.avatarUri)
        assertEquals(0, roomDb.syncSnapshotDao().getAllOnce("player1").size)
        assertEquals(0, roomDb.songSyncHistoryDao().getRecent("player1", 10).size)
        assertEquals("player1", roomDb.currentAccountDao().get()?.playerId)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationFromV3SingleUserNeverClaimsAnotherAccountsResidualHistory(): Unit = runTest {
        val dbName = "migration_v3_mixed_single_user_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        seedRawV3Database(dbName) {
            execSQL("INSERT INTO records VALUES ('song-c.0', 'IN', 950000, 95.0, 1, 3)")
            execSQL("INSERT INTO users VALUES ('player-c', 'Current', '', '', '', 15.0, 1, 3, 'CN', 3)")
            execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 12.0, 'Previous', 1, 'song-a.0', 'EZ', 900000, 90.0)")
            execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 900000, 90.0, 0, 1)")
        }

        val roomDb = migrationDatabase(context, dbName)

        assertEquals(listOf("song-c.0"), roomDb.recordDao().getAllRecordsOnce("player-c").map { it.songId })
        assertEquals(0, roomDb.syncSnapshotDao().getAllOnce("player-c").size)
        assertEquals(0, roomDb.songSyncHistoryDao().getRecent("player-c", 10).size)
        assertEquals("player-c", roomDb.currentAccountDao().get()?.playerId)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationFromV3DoubleUserKeepsLastSyncedAccountAndDropsMixedHistory(): Unit = runTest {
        // Two user rows are artifacts of the old REPLACE-upsert bug. Records
        // belong to the LAST synced account (B, greatest lastSyncAt); the
        // snapshots/history span both accounts and cannot be attributed, so
        // they are dropped rather than assigned to the wrong account.
        val dbName = "migration_v3_double_user_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        seedRawV3Database(dbName) {
            execSQL("INSERT INTO records VALUES ('song-b.0', 'HD', 950000, 95.0, 1, 2)")
            execSQL("INSERT INTO users VALUES ('player-a', 'Ay', '', '', '', 10.0, 1, 1, 'CN', 100)")
            execSQL("INSERT INTO users VALUES ('player-b', 'Bee', '', '', '', 12.0, 2, 2, 'CN', 200)")
            execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 10.0, 'Ay', 1, 'song-a.0', 'EZ', 800000, 80.0)")
            execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 800000, 80.0, 1, 1)")
        }
        val roomDb = migrationDatabase(context, dbName)

        assertEquals(listOf("song-b.0"), roomDb.recordDao().getAllRecordsOnce("player-b").map { it.songId })
        assertEquals(0, roomDb.recordDao().getRecordCount("player-a"))
        assertEquals("Bee", roomDb.userDao().getUserOnce("player-b")?.nickname)
        assertNull(roomDb.userDao().getUserOnce("player-a"), "stale user rows are removed")
        assertEquals(0, roomDb.syncSnapshotDao().getAllOnce("player-b").size, "mixed snapshots are dropped")
        assertEquals(0, roomDb.songSyncHistoryDao().getRecent("player-b", 10).size, "mixed history is dropped")
        assertEquals("player-b", roomDb.currentAccountDao().get()?.playerId)

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrationFromV3EmptyUsersDropsOrphanedData(): Unit = runTest {
        // Rows without any user row are orphans of the old clearData() gap
        // (it cleared records/users but never snapshots/history).
        val dbName = "migration_v3_empty_user_test.db"
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        seedRawV3Database(dbName) {
            execSQL("INSERT INTO records VALUES ('song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
            execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 15.0, 'Ghost', 1, 'song-a.0', 'EZ', 1000000, 100.0)")
            execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 1000000, 100.0, 1, 1)")
        }
        val roomDb = migrationDatabase(context, dbName)

        assertEquals(0, roomDb.recordDao().getRecordCount("player1"))
        assertEquals(0, roomDb.syncSnapshotDao().getAllOnce("player1").size)
        assertEquals(0, roomDb.songSyncHistoryDao().getRecent("player1", 10).size)
        assertNull(roomDb.currentAccountDao().get())

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    /** Creates a raw v3 database file with the v3 schema and the given rows. */
    private fun seedRawV3Database(dbName: String, seed: SQLiteDatabase.() -> Unit) {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        val rawDb = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        rawDb.version = 3
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `records` (
                `songId` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL,
                `isFullCombo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `playerId` TEXT NOT NULL,
                `nickname` TEXT NOT NULL,
                `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL,
                `background` TEXT NOT NULL,
                `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL,
                `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL,
                `lastSyncAt` INTEGER NOT NULL,
                PRIMARY KEY(`playerId`)
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `rks` REAL NOT NULL,
                `nickname` TEXT NOT NULL,
                `dataCount` INTEGER NOT NULL,
                `lastSyncedSongId` TEXT,
                `lastSyncedDifficulty` TEXT,
                `lastSyncedScore` INTEGER,
                `lastSyncedAccuracy` REAL
            )
        """.trimIndent())
        rawDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `song_sync_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `snapshotId` INTEGER NOT NULL,
                `songId` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL,
                `isFullCombo` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        rawDb.seed()
        rawDb.close()
    }

    private fun migrationDatabase(context: android.content.Context, dbName: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    // ── fixtures ──────────────────────────────────────────────────────

    private fun snapshot(ownerPlayerId: String, timestamp: Long, nickname: String): SyncSnapshotEntity =
        SyncSnapshotEntity(
            ownerPlayerId = ownerPlayerId,
            timestamp = timestamp,
            rks = 15.0f,
            nickname = nickname,
            dataCount = 1,
            lastSyncedSongId = "song-a.0",
            lastSyncedDifficulty = "IN",
            lastSyncedScore = 1_000_000,
            lastSyncedAccuracy = 100f
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
    ): SongSyncHistoryEntity = SongSyncHistoryEntity(
        ownerPlayerId = ownerPlayerId,
        snapshotId = snapshotId,
        songId = songId,
        difficulty = difficulty,
        score = score,
        accuracy = accuracy,
        isFullCombo = isFullCombo,
        timestamp = timestamp
    )

    private fun emptySave() = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            false, false, false, false, "", 0, 0, emptyList(),
            0, 0, 0, 0, null, null, null, null, null
        ),
        user = UserSettings(false, "", "", ""),
        summary = null
    )

    private fun writer() = SyncWriter(
        database,
        database.recordDao(),
        database.userDao(),
        database.syncSnapshotDao(),
        database.songSyncHistoryDao(),
        database.currentAccountDao()
    )

    private fun commitData(
        user: UserEntity = UserEntity("new-player", "New", "", "", "", 12f, 2, 2, "CN", 200L),
        records: List<RecordEntity> = listOf(RecordEntity("new-player", "new-song.0", "IN", 950_000, 95f, true, 200L))
    ): SyncCommitData = SyncCommitData(
        save = emptySave(),
        committedAt = 200L,
        user = user,
        records = records,
        snapshot = snapshot(user.playerId, 200L, "New")
    )
}
