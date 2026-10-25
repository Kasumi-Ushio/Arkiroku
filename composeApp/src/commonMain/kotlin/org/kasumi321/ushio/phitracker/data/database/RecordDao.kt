package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    @Query("SELECT * FROM records WHERE ownerPlayerId = :owner ORDER BY accuracy DESC")
    fun getAllRecords(owner: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE ownerPlayerId = :owner")
    suspend fun getAllRecordsOnce(owner: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE ownerPlayerId = :owner AND songId = :songId")
    suspend fun getRecordsBySong(owner: String, songId: String): List<RecordEntity>

    @Query("DELETE FROM records WHERE ownerPlayerId = :owner")
    suspend fun deleteByOwner(owner: String)

    @Query("SELECT COUNT(*) FROM records WHERE ownerPlayerId = :owner")
    suspend fun getRecordCount(owner: String): Int

    @Query("SELECT COUNT(DISTINCT songId) FROM records WHERE ownerPlayerId = :owner")
    suspend fun getDistinctSongCount(owner: String): Int

    @Query("SELECT COUNT(*) FROM records WHERE ownerPlayerId = :owner AND difficulty = :difficulty AND score > 0")
    suspend fun getClearCountByDifficulty(owner: String, difficulty: String): Int

    @Query("SELECT COUNT(*) FROM records WHERE ownerPlayerId = :owner AND isFullCombo = 1")
    suspend fun getTotalFcCount(owner: String): Int

    @Query("SELECT COUNT(*) FROM records WHERE ownerPlayerId = :owner AND accuracy >= 100.0")
    suspend fun getTotalPhiCount(owner: String): Int
}
