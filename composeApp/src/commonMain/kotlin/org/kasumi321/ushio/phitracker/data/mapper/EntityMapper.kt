package org.kasumi321.ushio.phitracker.data.mapper

import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.database.UserEntity
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object EntityMapper {
    fun Save.toRecordEntities(ownerPlayerId: String, updatedAt: Long): List<RecordEntity> {
        val entities = mutableListOf<RecordEntity>()
        for ((songId, songRecord) in gameRecord) {
            for ((difficulty, levelRecord) in songRecord.levels) {
                if (levelRecord != null) {
                    entities.add(
                        RecordEntity(
                            ownerPlayerId = ownerPlayerId,
                            songId = songId,
                            difficulty = difficulty.name,
                            score = levelRecord.score,
                            accuracy = levelRecord.accuracy,
                            isFullCombo = levelRecord.isFullCombo,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }
        return entities
    }

    fun List<RecordEntity>.toSongRecordMap(): Map<String, SongRecord> = groupBy { it.songId }.mapValues { (songId, records) ->
        val levels = mutableMapOf<Difficulty, LevelRecord?>()
        for (difficulty in Difficulty.entries) {
            val record = records.find { it.difficulty == difficulty.name }
            levels[difficulty] = record?.let {
                LevelRecord(score = it.score, accuracy = it.accuracy, isFullCombo = it.isFullCombo)
            }
        }
        SongRecord(songId = songId, levels = levels)
    }

    fun UserEntity.toUserProfile(): UserProfile = UserProfile(
        playerId = playerId,
        nickname = nickname,
        avatar = avatar,
        selfIntro = selfIntro,
        background = background,
        rks = rks,
        challengeModeRank = challengeModeRank,
        gameVersion = gameVersion,
        updatedAt = formatTimestamp(lastSyncAt)
    )

    fun UserProfile.toEntity(server: Server, lastSyncAt: Long): UserEntity = UserEntity(
        playerId = playerId,
        nickname = nickname,
        avatar = avatar,
        selfIntro = selfIntro,
        background = background,
        rks = rks,
        challengeModeRank = challengeModeRank,
        gameVersion = gameVersion,
        server = server.name,
        lastSyncAt = lastSyncAt
    )

    fun SyncSnapshotEntity.toDomain(): SyncSnapshot = SyncSnapshot(
        id = id,
        timestamp = timestamp,
        rks = rks,
        nickname = nickname,
        dataCount = dataCount,
        lastSyncedSongId = lastSyncedSongId,
        lastSyncedDifficulty = lastSyncedDifficulty,
        lastSyncedScore = lastSyncedScore,
        lastSyncedAccuracy = lastSyncedAccuracy
    )

    fun SongSyncHistoryEntity.toDomain(): SongSyncHistoryEntry = SongSyncHistoryEntry(
        id = id,
        snapshotId = snapshotId,
        songId = songId,
        difficulty = difficulty,
        score = score,
        accuracy = accuracy,
        isFullCombo = isFullCombo,
        timestamp = timestamp
    )
}

@OptIn(ExperimentalTime::class)
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

expect fun formatTimestamp(epochMillis: Long): String
