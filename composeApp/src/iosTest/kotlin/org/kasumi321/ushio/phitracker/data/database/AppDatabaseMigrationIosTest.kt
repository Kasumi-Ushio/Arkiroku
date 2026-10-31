package org.kasumi321.ushio.phitracker.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.createPlatformPaths
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class AppDatabaseMigrationIosTest {
    private val path = createPlatformPaths().filesDir + "/phi_tracker.db"

    @AfterTest
    fun tearDown() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        NSFileManager.defaultManager.removeItemAtPath("$path-wal", error = null)
        NSFileManager.defaultManager.removeItemAtPath("$path-shm", error = null)
    }

    @Test
    fun productionFactoryMigratesV4ToV5() = runTest {
        tearDown()
        seedV4Database()

        val database = createAppDatabase()
        try {
            val user = database.userDao().getUserOnce("player-v4")
            assertEquals("V4", user?.nickname)
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
    fun productionFactoryMigratesV1ToV5() = runTest {
        assertV3SchemaMigrates(version = 1)
    }

    @Test
    fun productionFactoryMigratesV2ToV5() = runTest {
        assertV3SchemaMigrates(version = 2)
    }

    private suspend fun assertV3SchemaMigrates(version: Int) {
        tearDown()
        seedV3Database(version)
        val database = createAppDatabase()
        try {
            val user = database.userDao().getUserOnce("player-c")
            assertEquals("Current", user?.nickname)
            assertEquals("", user?.moneyString)
            assertNull(user?.avatarUri)
            assertEquals(1, database.recordDao().getRecordCount("player-c"))
            assertEquals(0, database.syncSnapshotDao().getAllOnce("player-c").size)
            assertEquals(0, database.songSyncHistoryDao().getRecent("player-c", 10).size)
            assertEquals("player-c", database.currentAccountDao().get()?.playerId)
        } finally {
            database.close()
        }
    }

    private fun seedV3Database(version: Int) {
        val connection = BundledSQLiteDriver().open(path)
        connection.execSQL("PRAGMA user_version = $version")
        connection.execSQL(
            """CREATE TABLE `records` (
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`, `difficulty`))"""
        )
        connection.execSQL(
            """CREATE TABLE `users` (
                `playerId` TEXT NOT NULL PRIMARY KEY, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL)"""
        )
        connection.execSQL(
            """CREATE TABLE `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL,
                `rks` REAL NOT NULL, `nickname` TEXT NOT NULL, `dataCount` INTEGER NOT NULL,
                `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT, `lastSyncedScore` INTEGER,
                `lastSyncedAccuracy` REAL)"""
        )
        connection.execSQL(
            """CREATE TABLE `song_sync_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `snapshotId` INTEGER NOT NULL,
                `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `score` INTEGER NOT NULL,
                `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"""
        )
        connection.execSQL("INSERT INTO users VALUES ('player-c', 'Current', '', '', '', 15.0, 1, 3, 'CN', 3)")
        connection.execSQL("INSERT INTO records VALUES ('song-c.0', 'IN', 950000, 95.0, 1, 3)")
        connection.execSQL("INSERT INTO sync_snapshots VALUES (3, 1, 12.0, 'Previous', 1, 'song-a.0', 'EZ', 900000, 90.0)")
        connection.execSQL("INSERT INTO song_sync_history VALUES (9, 3, 'song-a.0', 'EZ', 900000, 90.0, 0, 1)")
        connection.close()
    }

    private fun seedV4Database() {
        val connection = BundledSQLiteDriver().open(path)
        connection.execSQL("PRAGMA user_version = 4")
        connection.execSQL(
            """CREATE TABLE `records` (
                `ownerPlayerId` TEXT NOT NULL, `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`ownerPlayerId`, `songId`, `difficulty`))"""
        )
        connection.execSQL(
            """CREATE TABLE `users` (
                `playerId` TEXT NOT NULL PRIMARY KEY, `nickname` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                `selfIntro` TEXT NOT NULL, `background` TEXT NOT NULL, `rks` REAL NOT NULL,
                `challengeModeRank` INTEGER NOT NULL, `gameVersion` INTEGER NOT NULL,
                `server` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL)"""
        )
        connection.execSQL(
            """CREATE TABLE `sync_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerPlayerId` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL, `rks` REAL NOT NULL, `nickname` TEXT NOT NULL,
                `dataCount` INTEGER NOT NULL, `lastSyncedSongId` TEXT, `lastSyncedDifficulty` TEXT,
                `lastSyncedScore` INTEGER, `lastSyncedAccuracy` REAL)"""
        )
        connection.execSQL(
            """CREATE TABLE `song_sync_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerPlayerId` TEXT NOT NULL,
                `snapshotId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `difficulty` TEXT NOT NULL,
                `score` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `isFullCombo` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL)"""
        )
        connection.execSQL("CREATE TABLE `current_account` (`playerId` TEXT NOT NULL PRIMARY KEY)")
        connection.execSQL("CREATE INDEX `index_sync_snapshots_ownerPlayerId` ON `sync_snapshots` (`ownerPlayerId`)")
        connection.execSQL("CREATE INDEX `index_song_sync_history_ownerPlayerId_songId` ON `song_sync_history` (`ownerPlayerId`, `songId`)")
        connection.execSQL("INSERT INTO users VALUES ('player-v4', 'V4', '', '', '', 15.0, 1, 4, 'CN', 4)")
        connection.execSQL("INSERT INTO records VALUES ('player-v4', 'song-v4.0', 'IN', 950000, 95.0, 1, 4)")
        connection.execSQL("INSERT INTO sync_snapshots VALUES (4, 'player-v4', 4, 15.0, 'V4', 1, NULL, NULL, NULL, NULL)")
        connection.execSQL("INSERT INTO song_sync_history VALUES (9, 'player-v4', 4, 'song-v4.0', 'IN', 950000, 95.0, 1, 4)")
        connection.execSQL("INSERT INTO current_account VALUES ('player-v4')")
        connection.close()
    }
}
