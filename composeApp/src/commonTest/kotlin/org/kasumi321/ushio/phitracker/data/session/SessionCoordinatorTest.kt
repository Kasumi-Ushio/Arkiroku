package org.kasumi321.ushio.phitracker.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    private fun coordinator(repo: FakeSessionRepository) = SessionCoordinatorImpl(repo)

    // ── login: persistence order and inheritance ─────────────────────

    @Test
    fun loginPersistsTokenOnlyAfterSuccessfulSync() = runTest {
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val result = coordinator(repo).login("token-1", Server.CN)

        assertTrue(result.isSuccess)
        // Order: validate → (mode chosen by owner footprint) → sync → persist.
        assertEquals(listOf(Op.Validate, Op.Sync, Op.SaveToken), repo.ops)
        assertEquals("P1", repo.savedBinding)
        assertEquals(listOf("token-1" to Server.CN), repo.storedTokens.toList())
    }

    @Test
    fun loginReusesExistingAccountWithRefreshAndInheritsItsHistory() = runTest {
        // The account already has local rows (a previous login or a
        // sessionToken reset): the returning login must diff via Refresh so
        // the score history continues, not bootstrap a second copy.
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) },
            ownersWithRecords = setOf("P1")
        )
        val result = coordinator(repo).login("rotated-token", Server.CN)

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncOp("rotated-token", SyncMode.Refresh, expectedOwner = null)), repo.syncOps)
        assertEquals("P1", repo.savedBinding)
    }

    @Test
    fun loginValidationFailurePersistsNothing() = runTest {
        val repo = fake(
            validate = { Result.failure(SessionTokenRejectedException()) },
            sync = { Result.success(syncResult()) }
        )
        val result = coordinator(repo).login("bad", Server.CN)

        assertTrue(result.isFailure)
        assertIs<SessionTokenRejectedException>(result.exceptionOrNull())
        assertTrue(repo.storedTokens.isEmpty())
        assertNull(repo.savedBinding)
        assertTrue(repo.syncOps.isEmpty())
    }

    @Test
    fun loginSyncFailureLeavesPreviousSessionStateUntouched() = runTest {
        // Account A is logged in (token + pointer + rows). A login attempt as
        // B that fails mid-sync must not persist B's token nor touch A's data.
        val repo = fake(
            savedToken = "token-a" to Server.CN,
            pointer = "A",
            validate = { Result.success(profile("B")) },
            sync = { Result.failure(RuntimeException("network unavailable")) }
        )
        val result = coordinator(repo).login("token-b", Server.CN)

        assertTrue(result.isFailure)
        assertTrue(repo.storedTokens.isEmpty(), "a failed login must not persist any token")
        assertEquals("A", repo.pointer)
        assertTrue(repo.clearCalls == 0)
    }

    @Test
    fun loginRollsBackCommittedRowsWhenCredentialStoreDropsTheWrite() = runTest {
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) },
            tokenReadback = null // degraded secure store: write silently lost
        )
        val result = coordinator(repo).login("token-1", Server.CN)

        assertTrue(result.isFailure)
        assertIs<SessionCredentialsPersistenceException>(result.exceptionOrNull())
        assertEquals(1, repo.clearCalls)
    }

    // ── resume ────────────────────────────────────────────────────────

    @Test
    fun resumeWithoutTokenRequiresLogin() = runTest {
        val outcome = coordinator(fake()).resumeSession()
        val requires = assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
        assertFalse(requires.hadToken)
    }

    @Test
    fun resumeOnlineRefreshesAndWritesTheBinding() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val outcome = coordinator(repo).resumeSession()

        val resumed = assertIs<SessionResumeOutcome.Resumed>(outcome)
        assertEquals(SessionResumeOutcome.Resumed.Source.ONLINE, resumed.source)
        assertEquals(listOf(SyncOp("token-1", SyncMode.Refresh, expectedOwner = "P1")), repo.syncOps)
        assertEquals("P1", repo.savedBinding)
    }

    @Test
    fun resumeOfflineKeepsSessionOnlyForTheBoundAccount() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(RuntimeException("offline")) }
        )
        val outcome = coordinator(repo).resumeSession()
        assertEquals(SessionResumeOutcome.Resumed.Source.LOCAL, assertIs<SessionResumeOutcome.Resumed>(outcome).source)

        // The stored binding names another account: the cached rows cannot be
        // proven to belong to this token, so no local session is restored.
        val mismatch = coordinator(
            repo.copy(boundPlayerId = "OTHER")
        ).resumeSession()
        assertIs<SessionResumeOutcome.RequiresLogin>(mismatch)
    }

    @Test
    fun resumeLegacyInstallWithoutBindingTrustsLocalDataOnce() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = null,
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(RuntimeException("offline")) }
        )
        val outcome = coordinator(repo).resumeSession()
        assertEquals(SessionResumeOutcome.Resumed.Source.LOCAL, assertIs<SessionResumeOutcome.Resumed>(outcome).source)
    }

    @Test
    fun resumeRejectedTokenRequiresLoginButKeepsLocalData() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(SessionTokenRejectedException()) }
        )
        val outcome = coordinator(repo).resumeSession()

        val requires = assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
        assertTrue(requires.hadToken)
        assertEquals(0, repo.clearCalls)
    }

    @Test
    fun resumeSyncFailureFallsBackToTrustedLocalSession() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.failure(RuntimeException("flaky network")) }
        )
        val outcome = coordinator(repo).resumeSession()
        assertEquals(SessionResumeOutcome.Resumed.Source.LOCAL, assertIs<SessionResumeOutcome.Resumed>(outcome).source)
    }

    @Test
    fun resumeOfflineWithoutLocalDataRequiresLogin() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = null,
            boundPlayerId = null,
            validate = { Result.failure(RuntimeException("offline")) }
        )
        val outcome = coordinator(repo).resumeSession()
        assertTrue(assertIs<SessionResumeOutcome.RequiresLogin>(outcome).hadToken)
    }

    // ── refresh ───────────────────────────────────────────────────────

    @Test
    fun refreshWithoutSessionFailsWithoutTouchingAnything() = runTest {
        val repo = fake()
        val result = coordinator(repo).refresh()

        assertTrue(result.isFailure)
        assertIs<SessionNotLoggedInException>(result.exceptionOrNull())
        assertTrue(repo.syncOps.isEmpty())
    }

    @Test
    fun refreshPassesCurrentPointerAsExpectedOwner() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            sync = { Result.success(syncResult()) }
        )
        val result = coordinator(repo).refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncOp("token-1", SyncMode.Refresh, expectedOwner = "P1")), repo.syncOps)
    }

    // ── logout: cancellation boundary ────────────────────────────────

    @Test
    fun logoutCancelsInFlightRefreshBeforeClearingAndLateResultNeverCommits() = runTest {
        val syncStarted = CompletableDeferred<Unit>()
        val releaseSync = CompletableDeferred<Unit>()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            sync = {
                syncStarted.complete(Unit)
                releaseSync.await()
                Result.success(syncResult())
            }
        )
        val session = coordinator(repo)

        val refreshJob = launch { session.refresh() }
        syncStarted.await()
        assertFalse(repo.commitCompleted)

        val logoutResult = session.logout()
        assertTrue(logoutResult.isSuccess)
        refreshJob.join()
        assertTrue(refreshJob.isCancelled)

        // The cancelled network result must not land as a commit after logout.
        releaseSync.complete(Unit)
        advanceUntilIdle()
        assertFalse(repo.commitCompleted, "a cancelled refresh must never mark its commit completed")
        assertEquals(1, repo.clearCalls)
        assertNull(repo.pointer)
    }

    @Test
    fun logoutFailureSurfacesAndKeepsData() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            clearFails = true
        )
        val result = coordinator(repo).logout()

        assertTrue(result.isFailure)
        assertEquals(1, repo.clearCalls)
        assertEquals("P1", repo.pointer, "a failed clear must keep the current account's data")
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun profile(playerId: String) = UserProfile(
        playerId = playerId, nickname = "Nick", avatar = "", selfIntro = "",
        background = "", rks = 15f, challengeModeRank = 1, gameVersion = 3, updatedAt = "remote"
    )

    private fun syncResult() = SyncSaveResult(save = minimalSave(), committedAt = 1L, changedEntryCount = 0, snapshotCreated = false)

    private fun minimalSave(): Save = Save(gameRecord = emptyMap(), gameProgress = fakeProgress, user = fakeUser, summary = null)

    private fun fake(
        savedToken: Pair<String, Server>? = null,
        pointer: String? = null,
        boundPlayerId: String? = null,
        ownersWithRecords: Set<String> = emptySet(),
        tokenReadback: Pair<String, Server>? = UNSET_READBACK,
        clearFails: Boolean = false,
        validate: suspend () -> Result<UserProfile> = { Result.failure(RuntimeException("offline")) },
        sync: suspend () -> Result<SyncSaveResult> = { Result.failure(RuntimeException("offline")) }
    ): FakeSessionRepository = FakeSessionRepository(
        savedToken = savedToken,
        initialPointer = pointer,
        initialBoundPlayerId = boundPlayerId,
        ownersWithRecords = ownersWithRecords,
        tokenReadback = tokenReadback,
        clearFails = clearFails,
        validateBehavior = validate,
        syncBehavior = sync
    )

    private companion object {
        // Distinguishes "no readback override" from an actual null readback.
        val UNSET_READBACK = Pair("__unset__", Server.CN)
        val fakeProgress = org.kasumi321.ushio.phitracker.domain.model.GameProgress(
            isFirstRun = false, legacyChapterFinished = false, alreadyShowCollectionTip = false,
            alreadyShowAutoUnlockINTip = false, completed = "", songUpdateInfo = 0,
            challengeModeRank = 0, money = emptyList(), unlockFlagOfSpasmodic = 0,
            unlockFlagOfIgallta = 0, unlockFlagOfRrharil = 0, flagOfSongRecordKey = 0,
            randomVersionUnlocked = null, chapter8UnlockBegin = null,
            chapter8UnlockSecondPhase = null, chapter8Passed = null, chapter8SongUnlocked = null
        )
        val fakeUser = org.kasumi321.ushio.phitracker.domain.model.UserSettings(
            showPlayerId = false, selfIntro = "", avatar = "", background = ""
        )
    }

    internal enum class Op { Validate, Sync, SaveToken }

    internal data class SyncOp(val token: String, val mode: SyncMode, val expectedOwner: String?)

    internal class FakeSessionRepository(
        private val savedToken: Pair<String, Server>?,
        initialPointer: String?,
        initialBoundPlayerId: String?,
        private val ownersWithRecords: Set<String>,
        // When UNSET_READBACK, getSessionToken returns the actually stored token.
        private val tokenReadback: Pair<String, Server>?,
        private val clearFails: Boolean,
        private val validateBehavior: suspend () -> Result<UserProfile>,
        private val syncBehavior: suspend () -> Result<SyncSaveResult>
    ) : PhigrosRepository {
        val ops = mutableListOf<Op>()
        val syncOps = mutableListOf<SyncOp>()
        val storedTokens = mutableListOf<Pair<String, Server>>()
        var savedBinding: String? = null
        var pointer: String? = initialPointer
        private var boundPlayerId: String? = initialBoundPlayerId
        var clearCalls = 0
        var commitCompleted = false

        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            ops += Op.Validate
            return validateBehavior()
        }

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> = syncSave(sessionToken, server, mode, expectedCurrentOwner = pointer)

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode,
            expectedCurrentOwner: String?
        ): Result<SyncSaveResult> {
            ops += Op.Sync
            syncOps += SyncOp(sessionToken, mode, expectedCurrentOwner)
            return syncBehavior().also {
                if (it.isSuccess) {
                    // The commit moved the pointer to the syncing account.
                    commitCompleted = true
                    pointer = "P1"
                }
            }
        }

        override fun getCachedSave(): Flow<Save?> = flowOf(null)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)

        override suspend fun saveSessionToken(token: String, server: Server) {
            ops += Op.SaveToken
            storedTokens += token to server
        }

        override suspend fun getSessionToken(): Pair<String, Server>? =
            if (tokenReadback == UNSET_READBACK) storedTokens.lastOrNull() ?: savedToken else tokenReadback

        override suspend fun clearData() {
            clearCurrentAccountData().getOrThrow()
        }

        override suspend fun clearCurrentAccountData(): Result<Unit> = runCatching {
            clearCalls++
            if (clearFails) error("database clear failed")
            pointer = null
            storedTokens.clear()
            savedBinding = null
        }

        override suspend fun clearTokenSync() = Unit
        override suspend fun getCurrentAccountOwner(): String? = pointer
        override suspend fun hasLocalRecords(ownerPlayerId: String): Boolean = ownerPlayerId in ownersWithRecords
        override suspend fun getBoundPlayerId(): String? = boundPlayerId
        override suspend fun saveBoundPlayerId(playerId: String) {
            savedBinding = playerId
            boundPlayerId = playerId
        }

        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
        override suspend fun getTotalFullComboCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(emptyList())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = emptyList()

        override suspend fun apiTest(): Result<JsonObject> = offline()
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun getSongApiDetail(key: ApiDetailCacheKey): Result<SongApiDetail> = offline()
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> = offline()
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> = offline()
        override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> = offline()
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> = offline()
        override suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>> = offline()
        override suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData> = offline()
        override suspend fun getMyChartTagVotes(songId: String, difficulty: Difficulty, platform: String, platformId: String, apiUserId: String, apiToken: String?): Result<Set<String>> = offline()
        override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> = offline()
        override suspend fun voteChartTags(songId: String, difficulty: Difficulty, primaryTags: List<String>, secondaryTags: List<String>, platform: String, platformId: String, apiUserId: String, apiToken: String): Result<Unit> = offline()
        override suspend fun proposeSongAlias(songId: String, alias: String, note: String?): Result<Unit> = offline()
        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> = offline()
        override suspend fun fetchGameUpdateInfo(): Result<GameUpdateInfo> = offline()

        private fun offline(): Result<Nothing> = Result.failure(UnsupportedOperationException())

        fun copy(boundPlayerId: String?): FakeSessionRepository = FakeSessionRepository(
            savedToken = savedToken,
            initialPointer = pointer,
            initialBoundPlayerId = boundPlayerId,
            ownersWithRecords = ownersWithRecords,
            tokenReadback = tokenReadback,
            clearFails = clearFails,
            validateBehavior = validateBehavior,
            syncBehavior = syncBehavior
        )
    }
}
