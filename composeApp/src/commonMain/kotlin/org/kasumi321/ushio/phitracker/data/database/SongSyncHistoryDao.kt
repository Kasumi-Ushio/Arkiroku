package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongSyncHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SongSyncHistoryEntity>)

    @Query("SELECT * FROM song_sync_history WHERE ownerPlayerId = :owner AND songId = :songId ORDER BY timestamp DESC")
    fun getBySongId(owner: String, songId: String): Flow<List<SongSyncHistoryEntity>>

    @Query("SELECT * FROM song_sync_history WHERE ownerPlayerId = :owner AND songId = :songId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBySongId(owner: String, songId: String, limit: Int = 3): List<SongSyncHistoryEntity>

    @Query("SELECT * FROM song_sync_history WHERE ownerPlayerId = :owner AND snapshotId = :snapshotId ORDER BY id DESC")
    suspend fun getBySnapshotId(owner: String, snapshotId: Long): List<SongSyncHistoryEntity>

    @Query("SELECT * FROM song_sync_history WHERE ownerPlayerId = :owner ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(owner: String, limit: Int = 5): List<SongSyncHistoryEntity>

    @Query("DELETE FROM song_sync_history WHERE ownerPlayerId = :owner")
    suspend fun deleteByOwner(owner: String)
}
