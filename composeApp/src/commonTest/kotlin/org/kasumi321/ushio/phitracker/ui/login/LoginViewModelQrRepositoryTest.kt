package org.kasumi321.ushio.phitracker.ui.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.domain.model.QrAuthorizationId
import org.kasumi321.ushio.phitracker.domain.model.QrChallengeId
import org.kasumi321.ushio.phitracker.domain.model.QrLoginChallenge
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionEstablished
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_qr_auth_failed
import phitracker.composeapp.generated.resources.login_qr_fetch_failed
import phitracker.composeapp.generated.resources.login_qr_status_failed
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelQrRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = viewModelLifecycle.tearDown(dispatcher)

    @Test
    fun qrHappyPathUsesTwoSecondCadenceThenLogsInWithExchangedToken() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginOk = true)
        val qr = FakeQrRepository(
            expiresAt = 30_000,
            polls = ArrayDeque(
                listOf(
                    QrLoginPollResult.Pending,
                    QrLoginPollResult.AuthorizationWaiting,
                    QrLoginPollResult.Authorized(QrAuthorizationId("authorization-opaque"))
                )
            )
        )
        val vm = viewModelLifecycle.track(
            LoginViewModel(session, qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()
        vm.updateServer(Server.GLOBAL)

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        assertEquals("https://qr.test/challenge", vm.uiState.value.qrCodeUrl)
        assertEquals(listOf(0L), qr.pollTimes)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(listOf(0L), qr.pollTimes)
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 2_000L), qr.pollTimes)
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(QrStatus.Success, vm.uiState.value.qrStatus)
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals("qr-session", vm.uiState.value.token)
        assertEquals(listOf("qr-session" to Server.GLOBAL), session.loginCalls)
        assertEquals(listOf(QrAuthorizationId("authorization-opaque")), qr.exchanges)
    }

    @Test
    fun qrExpiryUsesVirtualTimeAndEndsExpiredWithoutLogin() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginOk = true)
        val qr = FakeQrRepository(expiresAt = 4_000)
        val vm = viewModelLifecycle.track(
            LoginViewModel(session, qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(listOf(0L), qr.pollTimes)
        advanceTimeBy(3_999)
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(QrStatus.Expired, vm.uiState.value.qrStatus)
        assertEquals(0, vm.uiState.value.qrRemainingSeconds)
        assertEquals(listOf(0L, 2_000L), qr.pollTimes)
        assertTrue(session.loginCalls.isEmpty())
    }

    @Test
    fun cancellingQrLoginWhilePollingLeavesIdleInsteadOfError() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginOk = true)
        val qr = FakeQrRepository(expiresAt = 30_000, suspendPoll = true)
        val vm = viewModelLifecycle.track(
            LoginViewModel(session, qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(1, qr.pollTimes.size)
        vm.cancelQrLogin()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)
        runCurrent()

        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus, "cancelled QR login must not overwrite Idle with Error")
        assertEquals(null, vm.uiState.value.qrError)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, qr.pollTimes.size)
        assertTrue(session.loginCalls.isEmpty())
    }

    @Test
    fun cancelledOldQrLoginCannotOverwriteNewAttemptState() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginOk = true)
        val qr = FakeQrRepository(expiresAt = 30_000, suspendPoll = true, suspendRequestAttempt = 2)
        val vm = viewModelLifecycle.track(
            LoginViewModel(session, qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        vm.startQrLogin()
        assertEquals(QrStatus.Loading, vm.uiState.value.qrStatus)
        runCurrent()
        assertEquals(
            QrStatus.Loading,
            vm.uiState.value.qrStatus,
            "cancelled old QR login must not overwrite the newer attempt's Loading state"
        )

        vm.cancelQrLogin()
        runCurrent()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        vm.cancelQrLogin()
        runCurrent()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)
        assertTrue(session.loginCalls.isEmpty())
    }

    @Test
    fun qrRequestPollExchangeAndSessionFailuresMapToErrorWithoutCredentialLeak() = runTest(dispatcher) {
        suspend fun assertError(qr: FakeQrRepository, session: FakeSessionCoordinator = FakeSessionCoordinator(loginOk = true)) {
            val vm = viewModelLifecycle.track(
                LoginViewModel(session, qr) { testScheduler.currentTime }
            )
            advanceUntilIdle()
            vm.startQrLogin()
            advanceUntilIdle()
            assertEquals(QrStatus.Error, vm.uiState.value.qrStatus)
            latestQrError = vm.uiState.value.qrError
            val publicText = listOfNotNull(vm.uiState.value.qrError, vm.uiState.value.qrCodeUrl).joinToString()
            assertFalse("device-secret" in publicText)
            assertFalse("access-secret" in publicText)
        }

        assertError(FakeQrRepository(requestFailure = IllegalStateException("request DEVICE_UI_SECRET")))
        assertEquals(UiText.Res(Res.string.login_qr_fetch_failed), latestQrError)
        assertError(FakeQrRepository(pollFailure = IllegalStateException("poll ACCESS_UI_SECRET")))
        assertEquals(UiText.Res(Res.string.login_qr_status_failed), latestQrError)
        assertError(
            FakeQrRepository(
                polls = ArrayDeque(listOf(QrLoginPollResult.Authorized(QrAuthorizationId("auth")))),
                exchangeFailure = IllegalStateException("exchange SESSION_UI_SECRET")
            )
        )
        assertEquals(UiText.Res(Res.string.login_qr_auth_failed), latestQrError)
        val syncSession = FakeSessionCoordinator(loginOk = false)
        assertError(
            FakeQrRepository(polls = ArrayDeque(listOf(QrLoginPollResult.Authorized(QrAuthorizationId("auth"))))),
            syncSession
        )
        assertEquals(listOf("qr-session" to Server.CN), syncSession.loginCalls)
    }

    private inner class FakeQrRepository(
        private val expiresAt: Long = 30_000,
        private val polls: ArrayDeque<QrLoginPollResult> = ArrayDeque(),
        private val requestFailure: Throwable? = null,
        private val pollFailure: Throwable? = null,
        private val exchangeFailure: Throwable? = null,
        private val suspendPoll: Boolean = false,
        private val suspendRequestAttempt: Int? = null
    ) : QrLoginRepository {
        val pollTimes = mutableListOf<Long>()
        val exchanges = mutableListOf<QrAuthorizationId>()
        val requestedServers = mutableListOf<Server>()
        private var requestAttempts = 0

        override suspend fun requestChallenge(server: Server): QrLoginChallenge {
            requestAttempts += 1
            if (requestAttempts == suspendRequestAttempt) awaitCancellation()
            requestFailure?.let { throw it }
            requestedServers += server
            return QrLoginChallenge(QrChallengeId("challenge-opaque"), "https://qr.test/challenge", expiresAt)
        }

        override suspend fun poll(challengeId: QrChallengeId): QrLoginPollResult {
            pollTimes += dispatcher.scheduler.currentTime
            if (suspendPoll) awaitCancellation()
            pollFailure?.let { throw it }
            return if (polls.isEmpty()) QrLoginPollResult.Pending else polls.removeFirst()
        }

        override suspend fun exchangeForSessionToken(authorizationId: QrAuthorizationId): String {
            exchanges += authorizationId
            exchangeFailure?.let { throw it }
            return "qr-session"
        }
    }

    private var latestQrError: UiText? = null

    private inner class FakeSessionCoordinator(
        private val loginOk: Boolean
    ) : SessionCoordinator {
        val loginCalls = mutableListOf<Pair<String, Server>>()

        override suspend fun login(sessionToken: String, server: Server): Result<SessionEstablished> {
            loginCalls += sessionToken to server
            return if (loginOk) {
                Result.success(SessionEstablished(establishedProfile, establishedResult))
            } else {
                Result.failure(RuntimeException("network unavailable"))
            }
        }

        override suspend fun resumeSession(): SessionResumeOutcome = SessionResumeOutcome.RequiresLogin(hadToken = false)

        override suspend fun refresh(): Result<SyncSaveResult> = Result.failure(SessionNotLoggedInException())

        override suspend fun logout(): Result<SessionLogoutOutcome> = Result.success(SessionLogoutOutcome.Completed)

        override suspend fun resetSessionToken(): org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome =
            org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome.NotLoggedIn
    }

    private companion object {
        val fakeProgress = org.kasumi321.ushio.phitracker.domain.model.GameProgress(
            false, false, false, false, "", 0, 0, emptyList(), 0, 0, 0, 0,
            null, null, null, null, null
        )
        val establishedProfile = UserProfile("id", "nick", "", "", "", 0f, 0, 0, "")
        val establishedResult = SyncSaveResult(
            save = Save(emptyMap(), fakeProgress, UserSettings(false, "", "", ""), null),
            committedAt = 1L,
            changedEntryCount = 0,
            snapshotCreated = false
        )
    }
}
