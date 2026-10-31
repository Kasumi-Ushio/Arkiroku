package org.kasumi321.ushio.phitracker.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
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
import org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigration
import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigrationSource
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupRequiredException
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionRecoveryUnresolvedException
import org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome
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

    private fun coordinator(
        repo: FakeSessionRepository,
        storage: org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage =
            org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
    ) = SessionCoordinatorImpl(repo, SessionStateStore(storage))

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
    fun loginDeactivatesCommittedPointerWhenCredentialStoreDropsTheWrite() = runTest {
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) },
            tokenReadback = null // degraded secure store: write silently lost
        )
        val result = coordinator(repo).login("token-1", Server.CN)

        assertTrue(result.isFailure)
        assertIs<SessionCredentialsPersistenceException>(result.exceptionOrNull())
        assertEquals(1, repo.deactivateCalls)
        assertEquals(0, repo.clearCalls)
        assertNull(repo.pointer)
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
    fun resumeLegacyInstallWithoutBindingRequiresOnlineValidation() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = null,
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(RuntimeException("offline")) }
        )
        val outcome = coordinator(repo).resumeSession()
        assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
    }

    @Test
    fun legacyInstallCanUseOfflineResumeAfterBindingIsPersistedOnline() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = null,
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        assertIs<SessionResumeOutcome.Resumed>(coordinator(repo).resumeSession())
        assertEquals("P1", repo.getBoundPlayerId())

        val offline = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(RuntimeException("offline")) }
        )
        val outcome = coordinator(offline).resumeSession()
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
            boundPlayerId = "P1",
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
            boundPlayerId = "P1",
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

    @Test
    fun logoutCredentialFailureKeepsLogoutIntentAndBlocksOnlineResume() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            clearTokenFails = true,
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )

        val logout = coordinator(repo, storage).logout()

        assertEquals(SessionLogoutOutcome.CredentialsCleanupPending, logout.getOrThrow())
        assertNull(repo.pointer)
        assertTrue(storage.map["logout_pending"]?.toBooleanStrictOrNull() == true)
        val resumed = coordinator(repo, storage).resumeSession()
        val requires = assertIs<SessionResumeOutcome.RequiresLogin>(resumed)
        assertTrue(requires.credentialCleanupPending)
        assertFalse(Op.Validate in repo.ops)
    }

    @Test
    fun processDeathAfterRowsClearedCannotResurrectStaleToken() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["logout_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = null,
            boundPlayerId = "P1",
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )

        val outcome = coordinator(repo, storage).resumeSession()

        val requires = assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
        assertFalse(requires.credentialCleanupPending)
        assertNull(repo.getSessionToken())
        assertNull(storage.map["logout_pending"])
        assertFalse(Op.Validate in repo.ops)
    }

    @Test
    fun cancellationAfterSyncBeforeCredentialsDeactivatesCommittedPointer() = runTest {
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = {
                kotlin.coroutines.coroutineContext.job.cancel()
                Result.success(syncResult())
            }
        )

        val loginJob = launch { coordinator(repo).login("token-1", Server.CN) }
        loginJob.join()

        assertTrue(loginJob.isCancelled)
        assertEquals(1, repo.deactivateCalls)
        assertNull(repo.pointer)
        assertTrue(repo.storedTokens.isEmpty())
    }

    @Test
    fun processDeathAfterSyncBeforeCredentialsRecoversWithoutGuestExposure() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["pending_login_owner"] = "P1"
        }
        val repo = fake(pointer = "P1", ownersWithRecords = setOf("P1"))

        val outcome = coordinator(repo, storage).resumeSession()

        assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
        assertEquals(1, repo.deactivateCalls)
        assertNull(repo.pointer)
        assertNull(storage.map["pending_login_owner"])
    }

    @Test
    fun rejectedTokenCannotBeUsedAfterSkippingLogin() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "rejected-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.failure(SessionTokenRejectedException()) }
        )

        val rejected = coordinator(repo, storage).resumeSession()

        assertIs<SessionResumeOutcome.RequiresLogin>(rejected)
        assertNull(repo.pointer)
        assertNull(repo.getSessionToken())
        assertTrue(storage.map["login_required"]?.toBooleanStrictOrNull() == true)

        val afterSkip = coordinator(repo, storage).resumeSession()
        assertIs<SessionResumeOutcome.RequiresLogin>(afterSkip)
        assertEquals(1, repo.ops.count { it == Op.Validate })
    }

    @Test
    fun logoutCancellationAfterDatabaseCommitStillCompletesCredentialCleanup() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            cancelAfterClear = true
        )

        val logoutJob = launch { coordinator(repo, storage).logout() }
        logoutJob.join()

        assertNull(repo.getSessionToken())
        assertNull(storage.map["logout_pending"])
    }

    @Test
    fun cancellingOuterLogoutWhileRowsClearIsSuspendedStillFinishesCleanup() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val clearStarted = CompletableDeferred<Unit>()
        val releaseClear = CompletableDeferred<Unit>()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            clearBlock = {
                clearStarted.complete(Unit)
                releaseClear.await()
            }
        )
        val logoutJob = launch { coordinator(repo, storage).logout() }
        clearStarted.await()

        logoutJob.cancel()
        releaseClear.complete(Unit)
        logoutJob.join()

        assertTrue(logoutJob.isCancelled)
        assertNull(repo.pointer)
        assertNull(repo.getSessionToken())
        assertNull(storage.map["logout_pending"])
    }

    @Test
    fun divergentIntentRemovalBlocksLoginUntilExplicitLocalReset() = runTest {
        val storage = DivergentSessionStateStorage().apply {
            map["login_required"] = "true"
            map["logout_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        val blocked = session.login("new-token", Server.CN)

        assertIs<SessionCleanupRequiredException>(blocked.exceptionOrNull())
        assertTrue(repo.ops.isEmpty())
        assertNull(storage.map["logout_pending"], "the fake mirrors Android's in-memory removal")

        session.resetLocalSession().getOrThrow()
        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertEquals(1, repo.clearCalls, "only the interrupted session may be cleared")
        assertNull(storage.map["login_required"])
    }

    @Test
    fun successfulRefreshEstablishesMissingBinding() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = null,
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )

        val result = coordinator(repo).refresh()

        assertTrue(result.isSuccess)
        assertEquals("P1", repo.getBoundPlayerId())
        assertEquals("P1", repo.savedBinding)
        assertEquals(listOf(Op.Validate, Op.Sync), repo.ops)
    }

    // ── sessionToken reset ────────────────────────────────────────────

    @Test
    fun resetStoresTheNewTokenAndKeepsDataAndBinding() = runTest {
        val repo = fake(
            savedToken = "old-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            reset = { Result.success("new-token") }
        )
        val outcome = coordinator(repo).resetSessionToken()

        assertEquals("P1", assertIs<SessionResetOutcome.Success>(outcome).playerId)
        assertEquals("new-token", repo.storedTokens.lastOrNull()?.first)
        // Inheritance: nothing about the account's local footprint changes.
        assertEquals(0, repo.clearCalls)
        assertEquals("P1", repo.getBoundPlayerId())
        assertNull(repo.savedBinding, "reset must not rewrite an already valid binding")
    }

    @Test
    fun resetNetworkFailureReportsUnknownAndKeepsTheLocalToken() = runTest {
        val repo = fake(
            savedToken = "old-token" to Server.CN,
            pointer = "P1",
            reset = { Result.failure(RuntimeException("network unavailable")) }
        )
        val outcome = coordinator(repo).resetSessionToken()

        assertIs<SessionResetOutcome.OutcomeUnknown>(outcome)
        assertTrue(repo.storedTokens.isEmpty(), "an unknown outcome must not replace the stored token")
    }

    @Test
    fun resetRejectedTokenReportsRejectedWithoutTouchingData() = runTest {
        val repo = fake(
            savedToken = "old-token" to Server.CN,
            pointer = "P1",
            reset = { Result.failure(SessionTokenRejectedException()) }
        )
        val outcome = coordinator(repo).resetSessionToken()

        assertIs<SessionResetOutcome.Rejected>(outcome)
        assertEquals(0, repo.clearCalls)
        assertTrue(repo.storedTokens.isEmpty())
    }

    @Test
    fun resetWithoutSessionReportsNotLoggedIn() = runTest {
        val outcome = coordinator(fake()).resetSessionToken()
        assertIs<SessionResetOutcome.NotLoggedIn>(outcome)
    }

    @Test
    fun loginRequiredDoesNotBypassPendingLogoutCleanup() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["login_required"] = "true"
            map["logout_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "OLD",
            boundPlayerId = "OLD",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertEquals(1, repo.clearCalls)
        assertEquals("P1", repo.pointer)
        assertEquals("new-token", repo.getSessionToken()?.first)
        assertEquals("P1", repo.getBoundPlayerId())
        assertNull(storage.map["login_required"])
        assertNull(storage.map["logout_pending"])
        assertNull(storage.map["pending_login_owner"])

        val resumed = session.resumeSession()
        assertIs<SessionResumeOutcome.Resumed>(resumed)
        assertEquals(1, repo.clearCalls, "restart must not apply the old logout to the new account")
        assertEquals("P1", repo.pointer)
        assertEquals("new-token", repo.getSessionToken()?.first)
    }

    @Test
    fun resumeSyncRejectedInvalidatesInsteadOfFallingBackToLocal() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.failure(SessionTokenRejectedException()) }
        )

        val outcome = coordinator(repo, storage).resumeSession()

        assertIs<SessionResumeOutcome.RequiresLogin>(outcome)
        assertNull(repo.pointer)
        assertNull(repo.getSessionToken())
        assertTrue(storage.map["login_required"]?.toBooleanStrictOrNull() == true)
        assertEquals(0, repo.clearCalls, "rejected credentials must not delete inheritable local rows")
    }

    @Test
    fun refreshRejectedInvalidatesRuntimeSessionAndLaterSkipCannotResume() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            sync = { Result.failure(SessionTokenRejectedException()) }
        )
        val session = coordinator(repo, storage)

        val result = session.refresh()

        assertIs<SessionTokenRejectedException>(result.exceptionOrNull())
        assertNull(repo.pointer)
        assertNull(repo.getSessionToken())
        assertTrue(storage.map["login_required"]?.toBooleanStrictOrNull() == true)
        assertIs<SessionResumeOutcome.RequiresLogin>(session.resumeSession())
        assertFalse(Op.Validate in repo.ops)
    }

    @Test
    fun refreshRejectsMismatchedBindingBeforeSync() = runTest {
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "OTHER",
            ownersWithRecords = setOf("P1"),
            sync = { Result.success(syncResult()) }
        )

        val result = coordinator(repo).refresh()

        assertIs<SessionNotLoggedInException>(result.exceptionOrNull())
        assertTrue(repo.syncOps.isEmpty())
        assertNull(repo.pointer)
    }

    @Test
    fun localResetCreatesInertTerminalStateEvenWhenCredentialRemovalFails() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["logout_pending"] = "true"
            map["pending_login_owner"] = "P1"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            clearTokenFails = true
        )

        coordinator(repo, storage).resetLocalSession().getOrThrow()

        assertNull(repo.pointer)
        assertEquals(1, repo.resetStorageCalls)
        assertNull(storage.map["logout_pending"])
        assertNull(storage.map["pending_login_owner"])
        assertTrue(storage.map["login_required"]?.toBooleanStrictOrNull() == true)
        assertIs<SessionResumeOutcome.RequiresLogin>(coordinator(repo, storage).resumeSession())
    }

    @Test
    fun failedCredentialRebuildKeepsResetPendingWithoutDeletingScoresUntilLogin() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage()
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            clearTokenFails = true,
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        ).apply {
            resetStorageResult = Result.failure(IllegalStateException("keychain unavailable"))
        }
        val session = coordinator(repo, storage)

        assertTrue(session.resetLocalSession().isFailure)
        assertTrue(storage.map["reset_pending"]?.toBooleanStrictOrNull() == true)
        assertNull(repo.pointer)

        val resumed = assertIs<SessionResumeOutcome.RequiresLogin>(session.resumeSession())
        assertTrue(resumed.localResetPending)
        assertEquals(0, repo.clearCalls, "reset recovery must not delete account rows")
        assertTrue(storage.map["reset_pending"]?.toBooleanStrictOrNull() == true)

        repo.resetStorageResult = Result.success(Unit)
        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertNull(storage.map["reset_pending"])
        assertNull(storage.map["login_required"])
        assertEquals("new-token", repo.getSessionToken()?.first)
        assertEquals(0, repo.clearCalls)
    }

    @Test
    fun resetPendingSupersedesOldLogoutAndPreservesAllAccountDataForRelogin() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["logout_pending"] = "true"
            map["reset_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        val resumed = assertIs<SessionResumeOutcome.RequiresLogin>(session.resumeSession())

        assertFalse(resumed.localResetPending)
        assertEquals(0, repo.clearCalls, "reset must not clear records, user, snapshots or per-song history")
        assertTrue(repo.hasLocalRecords("P1"))
        assertNull(storage.map["logout_pending"])
        assertNull(storage.map["reset_pending"])

        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertEquals(SyncMode.Refresh, repo.syncOps.single().mode)
        assertEquals(0, repo.clearCalls)
        assertTrue(repo.hasLocalRecords("P1"))
    }

    @Test
    fun failureAfterResetIntentBeforeDeactivateStillCannotReplayOldLogout() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["logout_pending"] = "true"
            removeResultsByKey["logout_pending"] =
                org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult.Failed
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        assertTrue(session.resetLocalSession().isFailure)
        assertEquals("P1", repo.pointer)
        assertTrue(storage.map["reset_pending"]?.toBooleanStrictOrNull() == true)
        assertTrue(storage.map["logout_pending"]?.toBooleanStrictOrNull() == true)
        assertEquals(0, repo.clearCalls)

        assertIs<SessionNotLoggedInException>(session.refresh().exceptionOrNull())
        assertEquals(SessionResetOutcome.NotLoggedIn, session.resetSessionToken())
        assertTrue(repo.ops.isEmpty(), "reset_pending must fail closed before validation or sync")
        assertEquals(0, repo.resetCalls, "reset_pending must block upstream token rotation")
        assertEquals("P1", repo.pointer)

        storage.removeResultsByKey["logout_pending"] =
            org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult.Persisted
        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertEquals(0, repo.clearCalls)
        assertTrue(repo.hasLocalRecords("P1"))
        assertEquals(SyncMode.Refresh, repo.syncOps.single().mode)
    }

    @Test
    fun explicitLogoutSupersedesResetAndStillClearsAccountData() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["reset_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1")
        )

        val result = coordinator(repo, storage).logout()

        assertEquals(SessionLogoutOutcome.Completed, result.getOrThrow())
        assertEquals(1, repo.clearCalls)
        assertNull(storage.map["reset_pending"])
        assertNull(storage.map["logout_pending"])
    }

    @Test
    fun cancellationDuringResetRecoveryDoesNotRebuildCredentialStorage() = runTest {
        val storage = org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage().apply {
            map["reset_pending"] = "true"
        }
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            cancelOnClearToken = true
        )

        val resume = launch { coordinator(repo, storage).resumeSession() }
        resume.join()

        assertTrue(resume.isCancelled)
        assertEquals(0, repo.resetStorageCalls)
        assertEquals(0, repo.clearCalls)
        assertTrue(storage.map["reset_pending"]?.toBooleanStrictOrNull() == true)
    }

    // ── legacy recovery blocking ─────────────────────────────────────

    @Test
    fun unresolvedLegacyRecoveryBlocksLoginBeforeAnyMutation() = runTest {
        val storage = UnresolvedLegacyStorage()
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )

        val result = coordinator(repo, storage).login("token-1", Server.CN)

        assertIs<SessionRecoveryUnresolvedException>(result.exceptionOrNull())
        assertTrue(repo.ops.isEmpty())
        assertTrue(repo.storedTokens.isEmpty())
        assertNull(repo.savedBinding)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun unresolvedLegacyRecoveryBlocksResumeAndRefreshWithoutTouchingTheStoredSession() = runTest {
        val storage = UnresolvedLegacyStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        val resumed = session.resumeSession()
        assertTrue(assertIs<SessionResumeOutcome.RequiresLogin>(resumed).hadToken)
        assertTrue(repo.ops.isEmpty())
        assertEquals("P1", repo.pointer)
        assertEquals("token-1", repo.getSessionToken()?.first)

        assertIs<SessionNotLoggedInException>(session.refresh().exceptionOrNull())
        assertTrue(repo.syncOps.isEmpty())
    }

    @Test
    fun localResetDiscardsUnresolvedLegacyRecoveryAndRestoresLogin() = runTest {
        val storage = UnresolvedLegacyStorage()
        val repo = fake(
            savedToken = "old-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            validate = { Result.success(profile("P2")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        assertIs<SessionRecoveryUnresolvedException>(
            session.login("new-token", Server.CN).exceptionOrNull()
        )

        session.resetLocalSession().getOrThrow()

        assertTrue(storage.discarded)
        assertFalse(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
        assertTrue(session.login("new-token", Server.CN).isSuccess)
    }

    @Test
    fun failedLegacyDiscardKeepsResetAndLoginClosed() = runTest {
        val storage = UnresolvedLegacyStorage(discardResult = false)
        val repo = fake(
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) }
        )
        val session = coordinator(repo, storage)

        assertTrue(session.resetLocalSession().isFailure)
        assertTrue(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
        assertIs<SessionRecoveryUnresolvedException>(
            session.login("token-1", Server.CN).exceptionOrNull()
        )
    }

    @Test
    fun resetFailureDuringLegacyDiscardBlocksSessionOperationsUntilCompensation() = runTest {
        val storage = UnresolvedLegacyStorage(discardResult = false)
        val repo = fake(
            savedToken = "stale-token" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            ownersWithRecords = setOf("P1"),
            validate = { Result.success(profile("P1")) },
            sync = { Result.success(syncResult()) },
            reset = { Result.success("rotated-token") }
        )
        val session = coordinator(repo, storage)

        assertTrue(session.resetLocalSession().isFailure)
        assertTrue(storage.map["reset_pending"]?.toBooleanStrictOrNull() == true)
        assertEquals("P1", repo.pointer)
        assertIs<SessionNotLoggedInException>(session.refresh().exceptionOrNull())
        assertEquals(SessionResetOutcome.NotLoggedIn, session.resetSessionToken())
        assertTrue(repo.ops.isEmpty())
        assertEquals(0, repo.resetCalls)

        storage.discardResult = true
        session.resetLocalSession().getOrThrow()
        val login = session.login("new-token", Server.CN)

        assertTrue(login.isSuccess)
        assertEquals(SyncMode.Refresh, repo.syncOps.single().mode)
        assertEquals(0, repo.clearCalls)
        assertTrue(repo.hasLocalRecords("P1"))
        assertNull(storage.map["reset_pending"])
    }

    @Test
    fun logoutDiscardsUnresolvedLegacyRecoveryBeforeCompleting() = runTest {
        val storage = UnresolvedLegacyStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1"
        )

        val outcome = coordinator(repo, storage).logout().getOrThrow()

        assertEquals(SessionLogoutOutcome.Completed, outcome)
        assertTrue(storage.discarded)
        assertFalse(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
        assertNull(storage.map["logout_pending"])
        assertNull(repo.getSessionToken())
    }

    @Test
    fun sessionTokenResetIsRefusedWhileLegacyRecoveryIsUnresolved() = runTest {
        val storage = UnresolvedLegacyStorage()
        val repo = fake(
            savedToken = "token-1" to Server.CN,
            pointer = "P1",
            boundPlayerId = "P1",
            reset = { Result.success("rotated-token") }
        )

        assertEquals(SessionResetOutcome.NotLoggedIn, coordinator(repo, storage).resetSessionToken())
        assertTrue(repo.ops.isEmpty())
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun profile(playerId: String) = UserProfile(
        playerId = playerId, nickname = "Nick", avatar = "", selfIntro = "",
        background = "", rks = 15f, challengeModeRank = 1, gameVersion = 3, updatedAt = "remote"
    )

    private fun syncResult() = SyncSaveResult(save = minimalSave(), committedAt = 1L, changedEntryCount = 0, snapshotCreated = false)

    private fun minimalSave(): Save = Save(gameRecord = emptyMap(), gameProgress = fakeProgress, user = fakeUser, summary = null)

    private class DivergentSessionStateStorage : org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage() {
        private var failNextLogoutClear = true

        override fun remove(key: String): org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult {
            if (key == "logout_pending" && failNextLogoutClear) {
                failNextLogoutClear = false
                map.remove(key)
                return org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult.Failed
            }
            return super.remove(key)
        }
    }

    private fun fake(
        savedToken: Pair<String, Server>? = null,
        pointer: String? = null,
        boundPlayerId: String? = null,
        ownersWithRecords: Set<String> = emptySet(),
        tokenReadback: Pair<String, Server>? = UNSET_READBACK,
        clearFails: Boolean = false,
        clearTokenFails: Boolean = false,
        cancelOnClearToken: Boolean = false,
        cancelAfterClear: Boolean = false,
        clearBlock: suspend () -> Unit = {},
        tokenSaveFails: Boolean = false,
        bindingSaveFails: Boolean = false,
        validate: suspend () -> Result<UserProfile> = { Result.failure(RuntimeException("offline")) },
        sync: suspend () -> Result<SyncSaveResult> = { Result.failure(RuntimeException("offline")) },
        reset: suspend () -> Result<String> = { Result.failure(RuntimeException("offline")) }
    ): FakeSessionRepository = FakeSessionRepository(
        savedToken = savedToken,
        initialPointer = pointer,
        initialBoundPlayerId = boundPlayerId,
        ownersWithRecords = ownersWithRecords,
        tokenReadback = tokenReadback,
        clearFails = clearFails,
        clearTokenFails = clearTokenFails,
        cancelOnClearToken = cancelOnClearToken,
        cancelAfterClear = cancelAfterClear,
        clearBlock = clearBlock,
        tokenSaveFails = tokenSaveFails,
        bindingSaveFails = bindingSaveFails,
        validateBehavior = validate,
        syncBehavior = sync,
        resetBehavior = reset
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
        private var savedToken: Pair<String, Server>?,
        initialPointer: String?,
        initialBoundPlayerId: String?,
        private val ownersWithRecords: Set<String>,
        // When UNSET_READBACK, getSessionToken returns the actually stored token.
        private val tokenReadback: Pair<String, Server>?,
        private val clearFails: Boolean,
        private val clearTokenFails: Boolean,
        private val cancelOnClearToken: Boolean,
        private val cancelAfterClear: Boolean,
        private val clearBlock: suspend () -> Unit,
        private val tokenSaveFails: Boolean,
        private val bindingSaveFails: Boolean,
        private val validateBehavior: suspend () -> Result<UserProfile>,
        private val syncBehavior: suspend () -> Result<SyncSaveResult>,
        private val resetBehavior: suspend () -> Result<String>
    ) : PhigrosRepository {
        val ops = mutableListOf<Op>()
        val syncOps = mutableListOf<SyncOp>()
        val storedTokens = mutableListOf<Pair<String, Server>>()
        var savedBinding: String? = null
        var pointer: String? = initialPointer
        private var boundPlayerId: String? = initialBoundPlayerId
        var clearCalls = 0
        var deactivateCalls = 0
        var clearTokenCalls = 0
        var resetStorageCalls = 0
        var resetCalls = 0
        var resetStorageResult: Result<Unit> = Result.success(Unit)
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
            if (tokenSaveFails) error("injected token save failure")
            storedTokens += token to server
        }

        override suspend fun getSessionToken(): Pair<String, Server>? =
            if (tokenReadback == UNSET_READBACK) storedTokens.lastOrNull() ?: savedToken else tokenReadback

        override suspend fun clearData() {
            clearCurrentAccountData().getOrThrow()
        }

        override suspend fun clearCurrentAccountData(): Result<Unit> = runCatching {
            clearCurrentAccountRows().getOrThrow()
            clearTokenSync()
        }

        override suspend fun clearCurrentAccountRows(): Result<Unit> = runCatching {
            clearCalls++
            if (clearFails) error("database clear failed")
            clearBlock()
            pointer = null
            if (cancelAfterClear) kotlin.coroutines.coroutineContext.job.cancel()
        }

        override suspend fun deactivateCurrentAccount() {
            deactivateCalls++
            pointer = null
        }

        override suspend fun clearTokenSync() {
            clearTokenCalls++
            if (cancelOnClearToken) {
                kotlin.coroutines.coroutineContext.job.cancel()
                throw kotlinx.coroutines.CancellationException("cancelled during reset recovery")
            }
            if (clearTokenFails) error("injected token removal failure")
            savedToken = null
            storedTokens.clear()
            savedBinding = null
            boundPlayerId = null
        }

        override suspend fun resetCredentialStorage(): Result<Unit> {
            resetStorageCalls++
            if (resetStorageResult.isSuccess) {
                savedToken = null
                storedTokens.clear()
                savedBinding = null
                boundPlayerId = null
            }
            return resetStorageResult
        }

        override suspend fun getCurrentAccountOwner(): String? = pointer
        override suspend fun hasLocalRecords(ownerPlayerId: String): Boolean = ownerPlayerId in ownersWithRecords
        override suspend fun getBoundPlayerId(): String? = boundPlayerId
        override suspend fun saveBoundPlayerId(playerId: String) {
            if (bindingSaveFails) error("injected binding save failure")
            savedBinding = playerId
            boundPlayerId = playerId
        }

        override suspend fun resetSessionToken(
            sessionToken: String,
            server: Server,
            userObjectId: String
        ): Result<String> {
            resetCalls++
            return resetBehavior()
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
            clearTokenFails = clearTokenFails,
            cancelOnClearToken = cancelOnClearToken,
            cancelAfterClear = cancelAfterClear,
            clearBlock = clearBlock,
            tokenSaveFails = tokenSaveFails,
            bindingSaveFails = bindingSaveFails,
            validateBehavior = validateBehavior,
            syncBehavior = syncBehavior,
            resetBehavior = resetBehavior
        )
    }
}

private class UnresolvedLegacyStorage(
    var discardResult: Boolean = true
) : FakeSecureKeyValueStorage(), LegacySessionMigrationSource {
    var discarded = false

    private var migration: LegacySessionMigration =
        LegacySessionMigration.Unresolved(listOf("logout_pending"))

    override val legacySessionMigration: LegacySessionMigration
        get() = migration

    override fun retryLegacySessionMigration() = Unit

    override fun discardLegacySessionIntents(): Boolean {
        if (!discardResult) return false
        discarded = true
        migration = LegacySessionMigration.Completed
        return true
    }
}
