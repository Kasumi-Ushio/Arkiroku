package org.kasumi321.ushio.phitracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val playerId: String,
    val nickname: String,
    val avatar: String,
    val selfIntro: String,
    val background: String,
    val rks: Float,
    val challengeModeRank: Int,
    val gameVersion: Int,
    val updatedAt: String,
    val moneyString: String = "",
    val avatarUri: String? = null
)

@Serializable
data class GameProgress(
    val isFirstRun: Boolean,
    val legacyChapterFinished: Boolean,
    val alreadyShowCollectionTip: Boolean,
    val alreadyShowAutoUnlockINTip: Boolean,
    val completed: String,
    val songUpdateInfo: Int,
    val challengeModeRank: Int,
    val money: List<Int>,
    val unlockFlagOfSpasmodic: Int,
    val unlockFlagOfIgallta: Int,
    val unlockFlagOfRrharil: Int,
    val flagOfSongRecordKey: Int,
    val randomVersionUnlocked: Int?,
    val chapter8UnlockBegin: Boolean?,
    val chapter8UnlockSecondPhase: Boolean?,
    val chapter8Passed: Boolean?,
    val chapter8SongUnlocked: Int?
)

fun formatMoneyString(money: List<Int>): String {
    val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB")
    return money.withIndex()
        .reversed()
        .filter { it.value > 0 }
        .joinToString(" ") { "${it.value}${units.getOrElse(it.index) { "" }}" }
}

@Serializable
data class UserSettings(
    val showPlayerId: Boolean,
    val selfIntro: String,
    val avatar: String,
    val background: String
)

@Serializable
data class Summary(
    val saveVersion: Int,
    val challengeModeRank: Int,
    val rks: Float,
    val gameVersion: Int,
    val avatar: String,
    val progress: List<Int>
)

@Serializable
data class Save(
    val gameRecord: Map<String, SongRecord>,
    val gameProgress: GameProgress,
    val user: UserSettings,
    val summary: Summary?
)
