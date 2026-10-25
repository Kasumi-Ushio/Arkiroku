package org.kasumi321.ushio.phitracker.data.repository

import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncSnapshotProjectorTest {
    private val committedAt = 1_234_567L
    private val difficulties = mapOf(
        "song-a.0" to mapOf(Difficulty.IN to 12f),
        "song-b.0" to mapOf(Difficulty.HD to 10f)
    )
    private val names = mapOf("song-a.0" to "Song A", "song-b.0" to "Song B")

    @Test
    fun usesPositiveProfileRksAndProjectsTopB30Metadata(): Unit {
        val data = projector().project(
            save = saveWithRecords(),
            profile = profile(rks = 15.75f),
            server = Server.CN,
            difficulties = difficulties,
            songNames = names
        )

        assertEquals(15.75f, data.snapshot.rks)
        assertEquals(2, data.snapshot.dataCount)
        assertEquals("song-a.0", data.snapshot.lastSyncedSongId)
        assertEquals("IN", data.snapshot.lastSyncedDifficulty)
        assertEquals(1_000_000, data.snapshot.lastSyncedScore)
        assertEquals(100f, data.snapshot.lastSyncedAccuracy)
    }

    @Test
    fun fallsBackToRksCalculatedFromNewSave(): Unit {
        val data = projector().project(
            save = saveWithRecords(),
            profile = profile(rks = 0f),
            server = Server.CN,
            difficulties = difficulties,
            songNames = names
        )

        val expectedB30 = RksCalculator.getB30AndAllRecords(
            saveWithRecords().gameRecord,
            difficulties,
            names
        ).first
        assertTrue(data.snapshot.rks > 0f)
        assertEquals(RksCalculator.calculateDisplayRks(expectedB30), data.snapshot.rks)
    }

    @Test
    fun emptyB30MetadataIsSafe(): Unit {
        val data = projector().project(
            save = emptySave(),
            profile = profile(rks = 0f),
            server = Server.CN,
            difficulties = emptyMap(),
            songNames = emptyMap()
        )

        assertEquals(0f, data.snapshot.rks)
        assertEquals(0, data.snapshot.dataCount)
        assertNull(data.snapshot.lastSyncedSongId)
        assertNull(data.snapshot.lastSyncedDifficulty)
        assertNull(data.snapshot.lastSyncedScore)
        assertNull(data.snapshot.lastSyncedAccuracy)
    }

    @Test
    fun reusesOneClockValueAcrossEveryProjectedEntity(): Unit {
        val data = projector().project(
            save = saveWithRecords(),
            profile = profile(rks = 15f),
            server = Server.GLOBAL,
            difficulties = difficulties,
            songNames = names
        )
        val changedEntries = data.changedEntriesComparedTo(
            listOf(RecordEntity("player", "song-a.0", "IN", 900_000, 90f, false, 1L))
        )

        assertEquals(committedAt, data.committedAt)
        assertEquals(committedAt, data.user.lastSyncAt)
        assertTrue(data.records.all { it.updatedAt == committedAt })
        assertEquals(committedAt, data.snapshot.timestamp)
        assertTrue(changedEntries.all { it.timestamp == committedAt })
    }

    @Test
    fun diffIgnoresFullComboOnlyChangesAndDeletions(): Unit {
        val data = projector().project(
            save = saveWithRecords(),
            profile = profile(rks = 15f),
            server = Server.CN,
            difficulties = difficulties,
            songNames = names
        )
        val oldRecords = listOf(
            RecordEntity("player", "song-a.0", "IN", 1_000_000, 100f, false, 1L),
            RecordEntity("player", "song-b.0", "HD", 950_000, 95f, false, 1L),
            RecordEntity("player", "deleted.0", "EZ", 800_000, 80f, false, 1L)
        )

        assertTrue(data.changedEntriesComparedTo(oldRecords).isEmpty())
    }

    @Test
    fun diffIncludesAbsentScoreAndAccuracyChanges(): Unit {
        val data = projector().project(
            save = saveWithRecords(),
            profile = profile(rks = 15f),
            server = Server.CN,
            difficulties = difficulties,
            songNames = names
        )
        val oldRecords = listOf(
            RecordEntity("player", "song-a.0", "IN", 900_000, 100f, true, 1L),
            RecordEntity("player", "song-b.0", "HD", 950_000, 90f, false, 1L)
        )

        val changed = data.changedEntriesComparedTo(oldRecords)

        assertEquals(setOf("song-a.0" to "IN", "song-b.0" to "HD"), changed.map { it.songId to it.difficulty }.toSet())
        val absentData = data.copy(
            records = data.records + RecordEntity("player", "song-c.0", "EZ", 700_000, 75f, false, committedAt)
        )
        assertTrue(absentData.changedEntriesComparedTo(oldRecords).any { it.songId == "song-c.0" })
    }

    private fun projector() = SyncSnapshotProjector(clock = { committedAt })

    private fun profile(rks: Float) = UserProfile(
        playerId = "player", nickname = "Alice", avatar = "avatar", selfIntro = "intro",
        background = "background", rks = rks, challengeModeRank = 42, gameVersion = 3,
        updatedAt = "remote"
    )

    private fun saveWithRecords(): Save = emptySave().copy(
        gameRecord = mapOf(
            "song-a.0" to SongRecord(
                "song-a.0",
                mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            ),
            "song-b.0" to SongRecord(
                "song-b.0",
                mapOf(Difficulty.HD to LevelRecord(950_000, 95f, false))
            )
        )
    )

    private fun emptySave() = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            false, false, false, false, "", 0, 0, emptyList(),
            0, 0, 0, 0, null, null, null, null, null
        ),
        user = UserSettings(false, "", "", ""),
        summary = null
    )
}
