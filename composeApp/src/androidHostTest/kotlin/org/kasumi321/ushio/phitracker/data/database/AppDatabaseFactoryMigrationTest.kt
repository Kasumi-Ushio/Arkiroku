package org.kasumi321.ushio.phitracker.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.kasumi321.ushio.phitracker.data.platform.AndroidPlatformContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppDatabaseFactoryMigrationTest {
    private lateinit var context: Context
    private val databaseName = "phi_tracker.db"

    @BeforeTest
    fun setUp() {
        AndroidPlatformContext.initialize(ApplicationProvider.getApplicationContext())
        context = AndroidPlatformContext.applicationContext!!
        context.deleteDatabase(databaseName)
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun productionFactoryMigratesV3ThroughV5() = runTest {
        seedV3Database()
        seedV3AccountRows()

        val database = createAppDatabase(androidx.sqlite.driver.AndroidSQLiteDriver())
        try {
            val user = database.userDao().getUserOnce("player-c")
            assertEquals("Current", user?.nickname)
            assertEquals("", user?.moneyString)
            assertNull(user?.avatarUri)
            assertEquals(listOf("song-c.0"), database.recordDao().getAllRecordsOnce("player-c").map { it.songId })
            assertEquals(0, database.syncSnapshotDao().getAllOnce("player-c").size)
            assertEquals(0, database.songSyncHistoryDao().getRecent("player-c", 10).size)
            assertEquals("player-c", database.currentAccountDao().get()?.playerId)
        } finally {
            database.close()
        }
    }

    @Test
    fun productionFactoryMigratesV1ThroughV5() = runTest {
        seedV3Database()
        seedV3AccountRows()
        setDatabaseVersion(1)

        val database = createAppDatabase(androidx.sqlite.driver.AndroidSQLiteDriver())
        try {
            assertEquals("Current", database.userDao().getUserOnce("player-c")?.nickname)
            assertEquals(1, database.recordDao().getRecordCount("player-c"))
            assertEquals(0, database.syncSnapshotDao().getAllOnce("player-c").size)
            assertEquals("player-c", database.currentAccountDao().get()?.playerId)
        } finally {
            database.close()
        }
    }

    @Test
    fun productionFactoryMigratesV2ThroughV5() = runTest {
        seedV3Database()
        seedV3AccountRows()
        setDatabaseVersion(2)

        val database = createAppDatabase(androidx.sqlite.driver.AndroidSQLiteDriver())
        try {
            assertEquals("Current", database.userDao().getUserOnce("player-c")?.nickname)
            assertEquals(1, database.recordDao().getRecordCount("player-c"))
            assertEquals(0, database.syncSnapshotDao().getAllOnce("player-c").size)
            assertEquals("player-c", database.currentAccountDao().get()?.playerId)
        } finally {
            database.close()
        }
    }

    @Test
    fun productionFactoryMigratesV4AndClearsAmbiguousHistory() = runTest {
        seedV4Database()

        val database = createAppDatabase(androidx.sqlite.driver.AndroidSQLiteDriver())
        try {
            val user = database.userDao().getUserOnce("player-v4")
            assertEquals("", user?.moneyString)
            assertNull(user?.avatarUri)
            assertEquals(1, database.recordDao().getRecordCount("player-v4"))
            assertEquals(0, database.syncSnapshotDao().getAllOnce("player-v4").size)
            assertEquals(0, database.songSyncHistoryDao().getRecent("player-v4", 10).size)
            assertEquals("player-v4", database.currentAccountDao().get()?.playerId)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedMigrationRollsBackAndLeavesTheV3FileIntact() = runTest {
        seedV3Database()
        val malformed = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        malformed.execSQL("ALTER TABLE `users` RENAME TO `users_complete`")
        malformed.execSQL("CREATE TABLE `users` (`playerId` TEXT NOT NULL PRIMARY KEY, `nickname` TEXT NOT NULL)")
        malformed.execSQL("INSERT INTO users VALUES ('player-c', 'Current')")
        malformed.execSQL("INSERT INTO records VALUES ('song-c.0', 'IN', 950000, 95.0, 1, 3)")
        malformed.close()

        val database = createAppDatabase(androidx.sqlite.driver.AndroidSQLiteDriver())
        val failure = runCatching { database.userDao().getUserOnce("player-c") }.exceptionOrNull()
        runCatching { database.close() }

        kotlin.test.assertNotNull(failure)
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        assertEquals(3, raw.version)
        raw.rawQuery("SELECT COUNT(*) FROM `records`", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        raw.rawQuery("SELECT COUNT(*) FROM `users_complete`", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        raw.close()
    }

    private fun setDatabaseVersion(version: Int) {
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        raw.version = version
        raw.close()
    }

    private fun seedV3AccountRows() {
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        raw.execSQL("INSERT INTO records VALUES ('song-c.0', 'IN', 950000, 95.0, 1, 3)")
        raw.execSQL("INSERT INTO users VALUES ('player-c', 'Current', '', '', '', 15.0, 1, 3, 'CN', 3)")
        raw.execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 12.0, 'Previous', 1, 'song-a.0', 'EZ', 900000, 90.0)")
        raw.execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 900000, 90.0, 0, 1)")
        raw.close()
    }

    private fun seedV3Database() {
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        raw.version = 3
        raw.execSQL(
            """CREATE TABLE `records` (
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`))"""
        )
        raw.execSQL(
            """CREATE TABLE `users` (
                `playerId` TEXT NOT NULL PRIMARY KEY, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL)"""
        )
        raw.execSQL(
            """CREATE TABLE `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL,
                `rks` REAL NOT NULL, `nickname` TEXT NOT NULL, `dataCount` INTEGER NOT NULL,
                `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT, `lastSyncedScore` INTEGER,
                `lastSyncedAccuracy` REAL)"""
        )
        raw.execSQL(
            """CREATE TABLE `song_sync_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `snapshotId` INTEGER NOT NULL,
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"""
        )
        raw.close()
    }

    private fun seedV4Database() {
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        raw.version = 4
        raw.execSQL(
            """CREATE TABLE `records` (
                `ownerPlayerId` TEXT NOT NULL, `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`ownerPlayerId`, `songId`, `difficulty`))"""
        )
        raw.execSQL(
            """CREATE TABLE `users` (
                `playerId` TEXT NOT NULL PRIMARY KEY, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL)"""
        )
        raw.execSQL(
            """CREATE TABLE `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerPlayerId` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL, `rks` REAL NOT NULL, `nickname` TEXT NOT NULL,
                `dataCount` INTEGER NOT NULL, `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT,
                `lastSyncedScore` INTEGER, `lastSyncedAccuracy` REAL)"""
        )
        raw.execSQL(
            """CREATE TABLE `song_sync_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerPlayerId` TEXT NOT NULL,
                `snapshotId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL)"""
        )
        raw.execSQL("CREATE TABLE `current_account` (`playerId` TEXT NOT NULL PRIMARY KEY)")
        raw.execSQL("CREATE INDEX `index_sync_snapshots_ownerPlayerId` ON `sync_snapshots` (`ownerPlayerId`)")
        raw.execSQL("CREATE INDEX `index_song_sync_history_ownerPlayerId_songId` ON `song_sync_history` (`ownerPlayerId`, `songId`)")
        raw.execSQL("INSERT INTO users VALUES ('player-v4', 'V4', '', '', '', 15.0, 1, 4, 'CN', 4)")
        raw.execSQL("INSERT INTO records VALUES ('player-v4', 'song-v4.0', 'IN', 950000, 95.0, 1, 4)")
        raw.execSQL("INSERT INTO sync_snapshots VALUES (4, 'player-v4', 4, 15.0, 'V4', 1, NULL, NULL, NULL, NULL)")
        raw.execSQL("INSERT INTO song_sync_history VALUES (9, 'player-v4', 4, 'song-v4.0', 'IN', 950000, 95.0, 1, 4)")
        raw.execSQL("INSERT INTO current_account VALUES ('player-v4')")
        raw.close()
    }
}
