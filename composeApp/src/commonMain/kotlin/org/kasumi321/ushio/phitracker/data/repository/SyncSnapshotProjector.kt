package org.kasumi321.ushio.phitracker.data.repository

import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.database.UserEntity
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toEntity
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toRecordEntities
import org.kasumi321.ushio.phitracker.data.mapper.currentTimeMillis
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.formatMoneyString
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator

internal data class SyncCommitData(
    val save: Save,
    val committedAt: Long,
    val user: UserEntity,
    val records: List<RecordEntity>,
    val snapshot: SyncSnapshotEntity
) {
    fun changedEntriesComparedTo(oldRecords: List<RecordEntity>): List<SongSyncHistoryEntity> {
        val oldByKey = oldRecords.associateBy { it.songId to it.difficulty }
        return records.mapNotNull { record ->
            val old = oldByKey[record.songId to record.difficulty]
            if (old != null && old.score == record.score && old.accuracy == record.accuracy) {
                null
            } else {
                SongSyncHistoryEntity(
                    ownerPlayerId = user.playerId,
                    snapshotId = 0,
                    songId = record.songId,
                    difficulty = record.difficulty,
                    score = record.score,
                    accuracy = record.accuracy,
                    isFullCombo = record.isFullCombo,
                    timestamp = committedAt
                )
            }
        }
    }
}

internal class SyncSnapshotProjector(
    private val clock: () -> Long = ::currentTimeMillis
) {
    fun project(
        save: Save,
        profile: UserProfile,
        server: Server,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): SyncCommitData {
        val committedAt = clock()
        val records = save.toRecordEntities(profile.playerId, committedAt)
        val b30 = RksCalculator.getB30AndAllRecords(save.gameRecord, difficulties, songNames).first
        val topRecord = b30.firstOrNull()
        val rks = profile.rks.takeIf { it > 0f } ?: RksCalculator.calculateDisplayRks(b30)
        return SyncCommitData(
            save = save,
            committedAt = committedAt,
            user = profile.toEntity(server, committedAt).copy(
                moneyString = formatMoneyString(save.gameProgress.money)
            ),
            records = records,
            snapshot = SyncSnapshotEntity(
                ownerPlayerId = profile.playerId,
                timestamp = committedAt,
                rks = rks,
                nickname = profile.nickname,
                dataCount = records.map { it.songId }.distinct().size,
                lastSyncedSongId = topRecord?.songId,
                lastSyncedDifficulty = topRecord?.difficulty?.name,
                lastSyncedScore = topRecord?.score,
                lastSyncedAccuracy = topRecord?.accuracy
            )
        )
    }
}
