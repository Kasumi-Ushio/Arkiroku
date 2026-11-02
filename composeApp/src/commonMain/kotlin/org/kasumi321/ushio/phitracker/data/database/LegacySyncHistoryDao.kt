package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class LegacySnapshotStats(
    val snapshotCount: Long,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?
)

@Dao
interface LegacySyncHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshots(snapshots: List<LegacySyncSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<LegacySongSyncHistoryEntity>)

    @Query("SELECT COUNT(*) AS snapshotCount, MIN(timestamp) AS oldestTimestamp, MAX(timestamp) AS newestTimestamp FROM legacy_sync_snapshots")
    fun observeSnapshotStats(): Flow<LegacySnapshotStats>

    @Query("SELECT COUNT(*) FROM legacy_song_sync_history")
    fun observeEntryCount(): Flow<Long>

    @Query("SELECT * FROM legacy_sync_snapshots ORDER BY id")
    suspend fun getSnapshots(): List<LegacySyncSnapshotEntity>

    @Query("SELECT * FROM legacy_song_sync_history ORDER BY id")
    suspend fun getEntries(): List<LegacySongSyncHistoryEntity>

    @Query("DELETE FROM legacy_sync_snapshots")
    suspend fun deleteAllSnapshots()

    @Query("DELETE FROM legacy_song_sync_history")
    suspend fun deleteAllEntries()
}
