package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrentAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: CurrentAccountEntity)

    @Query("SELECT * FROM current_account LIMIT 1")
    suspend fun get(): CurrentAccountEntity?

    @Query("SELECT * FROM current_account")
    fun observe(): Flow<List<CurrentAccountEntity>>

    @Query("DELETE FROM current_account")
    suspend fun clear()

    /**
     * Replaces the single pointer row. A plain upsert would ADD a second row
     * when the account changes (playerId is the PK), so the row set is
     * cleared first — inside the caller's transaction this is atomic.
     */
    @Transaction
    suspend fun setCurrent(account: CurrentAccountEntity) {
        clear()
        upsert(account)
    }
}
