package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "legacy_song_sync_history", indices = [Index("snapshotId")])
data class LegacySongSyncHistoryEntity(
    @PrimaryKey val id: Long,
    val snapshotId: Long,
    val songId: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Float,
    val isFullCombo: Boolean,
    val timestamp: Long
)
