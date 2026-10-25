package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity

/**
 * A single score row, scoped to the account that owns it. The owner is the
 * TapTap user objectId: it stays stable across sessionToken resets, so a
 * returning account with a rotated token keeps updating its own rows instead
 * of creating a second copy of its save.
 */
@Entity(tableName = "records", primaryKeys = ["ownerPlayerId", "songId", "difficulty"])
data class RecordEntity(
    val ownerPlayerId: String,
    val songId: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Float,
    val isFullCombo: Boolean,
    val updatedAt: Long
)
