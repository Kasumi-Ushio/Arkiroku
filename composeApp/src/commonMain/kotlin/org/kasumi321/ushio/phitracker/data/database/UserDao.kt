package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Query("SELECT * FROM users WHERE playerId = :playerId")
    fun getUser(playerId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE playerId = :playerId")
    suspend fun getUserOnce(playerId: String): UserEntity?

    @Query("DELETE FROM users WHERE playerId = :playerId")
    suspend fun deleteByPlayer(playerId: String)
}
