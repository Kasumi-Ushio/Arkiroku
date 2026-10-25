package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SyncSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndGetId(snapshot: SyncSnapshotEntity): Long

    @Query("SELECT * FROM sync_snapshots WHERE ownerPlayerId = :owner ORDER BY timestamp DESC")
    fun getAll(owner: String): Flow<List<SyncSnapshotEntity>>

    @Query("SELECT * FROM sync_snapshots WHERE ownerPlayerId = :owner ORDER BY timestamp DESC")
    suspend fun getAllOnce(owner: String): List<SyncSnapshotEntity>

    @Query("SELECT * FROM sync_snapshots WHERE ownerPlayerId = :owner ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(owner: String): SyncSnapshotEntity?

    @Query("DELETE FROM sync_snapshots WHERE ownerPlayerId = :owner")
    suspend fun deleteByOwner(owner: String)
}
