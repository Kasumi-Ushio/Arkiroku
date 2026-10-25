package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row pointer naming the account (TapTap objectId) whose data the app
 * currently displays. It is written inside the same transaction as any
 * account-scoped data change, so the pointer can never disagree with the rows.
 */
@Entity(tableName = "current_account")
data class CurrentAccountEntity(
    @PrimaryKey val playerId: String
)
