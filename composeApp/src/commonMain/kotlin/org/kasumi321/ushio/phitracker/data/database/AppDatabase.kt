package org.kasumi321.ushio.phitracker.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        RecordEntity::class,
        UserEntity::class,
        SyncSnapshotEntity::class,
        SongSyncHistoryEntity::class,
        CurrentAccountEntity::class
    ],
    version = 5,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun userDao(): UserDao
    abstract fun syncSnapshotDao(): SyncSnapshotDao
    abstract fun songSyncHistoryDao(): SongSyncHistoryDao
    abstract fun currentAccountDao(): CurrentAccountDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("""
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
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("""
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
            }
        }

        /**
         * v3 → v4: scope records / sync snapshots / per-song history to the
         * account that owns them (ownerPlayerId == TapTap objectId) and add the
         * single-row `current_account` pointer.
         *
         * Attribution of pre-v4 (single-account) rows, by users-table shape:
         * - users empty: every remaining data row is an orphan left by the old
         *   clearData() (it never removed snapshots/history) — drop them all.
         * - exactly one user row: records belong to that playerId; legacy
         *   snapshots/history are still dropped because earlier logout and
         *   account-switch paths could leave another account's rows mixed in.
         * - two or more user rows (artifacts of the old REPLACE-upsert bug):
         *   records were replaced wholesale on every commit, so they belong to
         *   the LAST synced account — the users row with the greatest
         *   lastSyncAt (ties broken by playerId for determinism); stale user
         *   rows and all un-attributable snapshots/history are dropped.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                val userCount = connection.prepare("SELECT COUNT(*) FROM `users`").use { stmt ->
                    if (stmt.step()) stmt.getLong(0) else 0L
                }
                val chosenOwner: String? = if (userCount == 0L) {
                    null
                } else {
                    connection.prepare(
                        "SELECT `playerId` FROM `users` ORDER BY `lastSyncAt` DESC, `playerId` DESC LIMIT 1"
                    ).use { stmt -> if (stmt.step()) stmt.getText(0) else null }
                }

                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `records_v4` (
                        `ownerPlayerId` TEXT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `isFullCombo` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerPlayerId`, `songId`, `difficulty`)
                    )
                """.trimIndent())
                if (chosenOwner != null) {
                    connection.prepare(
                        "INSERT INTO `records_v4` (`ownerPlayerId`, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `updatedAt`) " +
                            "SELECT ?, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `updatedAt` FROM `records`"
                    ).use { stmt ->
                        stmt.bindText(1, chosenOwner)
                        stmt.step()
                    }
                }
                connection.execSQL("DROP TABLE IF EXISTS `records`")
                connection.execSQL("ALTER TABLE `records_v4` RENAME TO `records`")

                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_snapshots_v4` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerPlayerId` TEXT NOT NULL,
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
                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_sync_history_v4` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerPlayerId` TEXT NOT NULL,
                        `snapshotId` INTEGER NOT NULL,
                        `songId` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `isFullCombo` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                connection.execSQL("DROP TABLE IF EXISTS `sync_snapshots`")
                connection.execSQL("ALTER TABLE `sync_snapshots_v4` RENAME TO `sync_snapshots`")
                connection.execSQL("DROP TABLE IF EXISTS `song_sync_history`")
                connection.execSQL("ALTER TABLE `song_sync_history_v4` RENAME TO `song_sync_history`")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_snapshots_ownerPlayerId` ON `sync_snapshots` (`ownerPlayerId`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_sync_history_ownerPlayerId_songId` ON `song_sync_history` (`ownerPlayerId`, `songId`)")

                if (chosenOwner != null && userCount > 1L) {
                    connection.prepare("DELETE FROM `users` WHERE `playerId` != ?").use { stmt ->
                        stmt.bindText(1, chosenOwner)
                        stmt.step()
                    }
                }

                connection.execSQL("CREATE TABLE IF NOT EXISTS `current_account` (`playerId` TEXT NOT NULL, PRIMARY KEY(`playerId`))")
                if (chosenOwner != null) {
                    connection.prepare("INSERT INTO `current_account` (`playerId`) VALUES (?)").use { stmt ->
                        stmt.bindText(1, chosenOwner)
                        stmt.step()
                    }
                }
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `users` ADD COLUMN `moneyString` TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE `users` ADD COLUMN `avatarUri` TEXT DEFAULT NULL")
                connection.execSQL("DELETE FROM `sync_snapshots`")
                connection.execSQL("DELETE FROM `song_sync_history`")
            }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
