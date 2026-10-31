package org.kasumi321.ushio.phitracker.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

expect fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun createAppDatabase(driver: SQLiteDriver = BundledSQLiteDriver()): AppDatabase = createDatabaseBuilder()
    .setDriver(driver)
    .setQueryCoroutineContext(Dispatchers.Default)
    .addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5
    )
    .build()
