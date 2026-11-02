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
        CurrentAccountEntity::class,
        LegacySyncSnapshotEntity::class,
        LegacySongSyncHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun userDao(): UserDao
    abstract fun syncSnapshotDao(): SyncSnapshotDao
    abstract fun songSyncHistoryDao(): SongSyncHistoryDao
    abstract fun currentAccountDao(): CurrentAccountDao
    abstract fun legacySyncHistoryDao(): LegacySyncHistoryDao

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
         * Records retain the legacy last-synced-account attribution. Historical
         * rows have no reliable owner, so they move to quarantine instead of
         * being assigned to an arbitrary account or destroyed.
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
                createLegacyHistoryTables(connection)
                connection.execSQL("""
                    INSERT INTO `legacy_sync_snapshots` (
                        `id`, `timestamp`, `rks`, `nickname`, `dataCount`,
                        `lastSyncedSongId`, `lastSyncedDifficulty`, `lastSyncedScore`, `lastSyncedAccuracy`
                    )
                    SELECT `id`, `timestamp`, `rks`, `nickname`, `dataCount`,
                        `lastSyncedSongId`, `lastSyncedDifficulty`, `lastSyncedScore`, `lastSyncedAccuracy`
                    FROM `sync_snapshots`
                """.trimIndent())
                connection.execSQL("""
                    INSERT INTO `legacy_song_sync_history` (
                        `id`, `snapshotId`, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `timestamp`
                    )
                    SELECT `id`, `snapshotId`, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `timestamp`
                    FROM `song_sync_history`
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
                createLegacyHistoryTables(connection)
                connection.execSQL("""
                    INSERT INTO `legacy_sync_snapshots` (
                        `id`, `timestamp`, `rks`, `nickname`, `dataCount`,
                        `lastSyncedSongId`, `lastSyncedDifficulty`, `lastSyncedScore`, `lastSyncedAccuracy`
                    )
                    SELECT `id`, `timestamp`, `rks`, `nickname`, `dataCount`,
                        `lastSyncedSongId`, `lastSyncedDifficulty`, `lastSyncedScore`, `lastSyncedAccuracy`
                    FROM `sync_snapshots`
                """.trimIndent())
                connection.execSQL("""
                    INSERT INTO `legacy_song_sync_history` (
                        `id`, `snapshotId`, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `timestamp`
                    )
                    SELECT `id`, `snapshotId`, `songId`, `difficulty`, `score`, `accuracy`, `isFullCombo`, `timestamp`
                    FROM `song_sync_history`
                """.trimIndent())
                connection.execSQL("DELETE FROM `sync_snapshots`")
                connection.execSQL("DELETE FROM `song_sync_history`")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                createLegacyHistoryTables(connection)
            }
        }

        private fun createLegacyHistoryTables(connection: SQLiteConnection) {
            connection.execSQL("""
                CREATE TABLE IF NOT EXISTS `legacy_sync_snapshots` (
                    `id` INTEGER PRIMARY KEY NOT NULL,
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
                CREATE TABLE IF NOT EXISTS `legacy_song_sync_history` (
                    `id` INTEGER PRIMARY KEY NOT NULL,
                    `snapshotId` INTEGER NOT NULL,
                    `songId` TEXT NOT NULL,
                    `difficulty` TEXT NOT NULL,
                    `score` INTEGER NOT NULL,
                    `accuracy` REAL NOT NULL,
                    `isFullCombo` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
            """.trimIndent())
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_legacy_song_sync_history_snapshotId` ON `legacy_song_sync_history` (`snapshotId`)")
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
