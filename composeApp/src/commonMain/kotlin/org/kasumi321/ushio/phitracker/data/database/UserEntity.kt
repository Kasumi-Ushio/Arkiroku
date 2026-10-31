package org.kasumi321.ushio.phitracker.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val playerId: String,
    val nickname: String,
    val avatar: String,
    val selfIntro: String,
    val background: String,
    val rks: Float,
    val challengeModeRank: Int,
    val gameVersion: Int,
    val server: String,
    val lastSyncAt: Long,
    @ColumnInfo(defaultValue = "''") val moneyString: String = "",
    @ColumnInfo(defaultValue = "NULL") val avatarUri: String? = null
)
