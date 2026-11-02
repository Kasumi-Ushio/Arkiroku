package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "legacy_sync_snapshots")
data class LegacySyncSnapshotEntity(
    @PrimaryKey val id: Long,
    val timestamp: Long,
    val rks: Float,
    val nickname: String,
    val dataCount: Int,
    val lastSyncedSongId: String?,
    val lastSyncedDifficulty: String?,
    val lastSyncedScore: Int?,
    val lastSyncedAccuracy: Float?
)
