package org.kasumi321.ushio.phitracker.ui.login

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.platform.ApiCrypto
import org.kasumi321.ushio.phitracker.data.repository.QrLoginRepositoryImpl
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupFailure
import org.kasumi321.ushio.phitracker.domain.session.SessionCleanupRequiredException
import org.kasumi321.ushio.phitracker.domain.session.SessionCoordinator
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import org.kasumi321.ushio.phitracker.domain.session.SessionEstablished
import org.kasumi321.ushio.phitracker.domain.session.SessionRecoveryUnresolvedException
import org.kasumi321.ushio.phitracker.domain.session.SessionLogoutOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionNotLoggedInException
import org.kasumi321.ushio.phitracker.domain.session.SessionResetOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionResumeOutcome
import org.kasumi321.ushio.phitracker.domain.session.SessionTokenRejectedException
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_error_login_finalization
import phitracker.composeapp.generated.resources.login_error_previous_credential_cleanup
import phitracker.composeapp.generated.resources.login_error_previous_data_cleanup
import phitracker.composeapp.generated.resources.login_error_recovery_unresolved
import phitracker.composeapp.generated.resources.login_error_reset_pending
import phitracker.composeapp.generated.resources.login_error_storage_rebuild_required
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LoginViewModel maps SessionCoordinator outcomes onto UI state; the session
 * semantics themselves (mode selection, persistence order, inheritance) are
 * covered by SessionCoordinatorTest.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = viewModelLifecycle.tearDown(dispatcher)

    private fun viewModel(
        session: FakeSessionCoordinator = FakeSessionCoordinator(),
        qrLoginApi: TapTapQrLoginApi = TapTapQrLoginApi(HttpClient(MockEngine { respond("") }))
    ): LoginViewModel = viewModelLifecycle.track(
        LoginViewModel(session, QrLoginRepositoryImpl(qrLoginApi))
    )

    @Test
    fun resumeRequiringLoginWithoutTokenShowsNoError() = runTest(dispatcher) {
        val vm = viewModel(FakeSessionCoordinator(resumeOutcome = SessionResumeOutcome.RequiresLogin(hadToken = false)))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertFalse(vm.uiState.value.isLoggedIn)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun resumeWithUnusableTokenExplainsTheFallback() = runTest(dispatcher) {
        val vm = viewModel(FakeSessionCoordinator(resumeOutcome = SessionResumeOutcome.RequiresLogin(hadToken = true)))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertFalse(vm.uiState.value.isLoggedIn)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun resumedSessionLogsInFromBothSources() {
        listOf(SessionResumeOutcome.Resumed.Source.ONLINE, SessionResumeOutcome.Resumed.Source.LOCAL).forEach { source ->
            val vm = viewModel(
                FakeSessionCoordinator(resumeOutcome = SessionResumeOutcome.Resumed(profile = null, source = source, syncResult = null))
            )
            advanceUntilIdleFor(vm)
            assertTrue(vm.uiState.value.isLoggedIn)
            assertFalse(vm.uiState.value.isCheckingToken)
        }
    }

    @Test
    fun emptyTokenShowsInlineErrorWithoutCallingCoordinator() = runTest(dispatcher) {
        val session = FakeSessionCoordinator()
        val vm = viewModel(session)
        advanceUntilIdle()

        vm.login()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertNotNull(vm.uiState.value.error)
        assertTrue(session.loginCalls.isEmpty())
    }

    @Test
    fun manualTokenLoginSuccessLogsIn() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginResult = Result.success(established()))
        val vm = viewModel(session)
        advanceUntilIdle()

        vm.updateToken("  manual-token  ")
        vm.updateServer(Server.GLOBAL)
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf("manual-token" to Server.GLOBAL), session.loginCalls)
    }

    @Test
    fun manualTokenLoginFailureKeepsUserAtLoginWithError() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginResult = Result.failure(RuntimeException("sync unavailable")))
        val vm = viewModel(session)
        advanceUntilIdle()

        vm.updateToken("token")
        vm.login()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun rejectedTokenMapsToCredentialExpiredMessage() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(loginResult = Result.failure(SessionTokenRejectedException()))
        val vm = viewModel(session)
        advanceUntilIdle()

        vm.updateToken("reset-token")
        vm.login()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun qrLoginAdvancesFromChallengeThroughScanExchangeAndSuccessAtTwoSecondCadence() = runTest(dispatcher) {
        val firstPoll = CompletableDeferred<Unit>()
        val profileRequest = CompletableDeferred<Unit>()
        var pollAttempts = 0
        val session = FakeSessionCoordinator(loginResult = Result.success(established()))
        val api = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request -> when (request.url.encodedPath) {
                    "/oauth2/v1/device/code" -> respond(
                        """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    "/oauth2/v1/token" -> {
                        if (pollAttempts++ == 0) firstPoll.await()
                        respond(
                            if (pollAttempts == 1) {
                                """{"success":false,"data":{"error":"authorization_waiting"}}"""
                            } else {
                                """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                            },
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "/account/profile/v1" -> {
                        profileRequest.await()
                        respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "/1.1/users" -> respond(
                        """{"sessionToken":"qr-session"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    else -> error("Unexpected QR request: ${request.url}")
                } }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val vm = viewModel(session, api)
        advanceUntilIdle()

        vm.startQrLogin()
        assertEquals(QrStatus.Loading, vm.uiState.value.qrStatus)
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        assertEquals(30, vm.uiState.value.qrRemainingSeconds)

        firstPoll.complete(Unit)
        runCurrent()
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, pollAttempts)
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, pollAttempts)
        assertEquals(QrStatus.Exchanging, vm.uiState.value.qrStatus)

        profileRequest.complete(Unit)
        advanceUntilIdle()
        assertEquals(QrStatus.Success, vm.uiState.value.qrStatus, vm.uiState.value.qrError?.toString())
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(listOf("qr-session" to Server.CN), session.loginCalls)
    }

    @Test
    fun qrLoginExpiresOrFailsAndCanReturnToIdle() = runTest(dispatcher) {
        val expiredApi = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request ->
                if (request.url.encodedPath != "/oauth2/v1/device/code") error("Unexpected QR request")
                respond(
                    """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":0}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                ) }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val expiredVm = viewModel(FakeSessionCoordinator(), expiredApi)
        advanceUntilIdle()
        expiredVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Expired, expiredVm.uiState.value.qrStatus)
        assertEquals(0, expiredVm.uiState.value.qrRemainingSeconds)
        expiredVm.cancelQrLogin()
        assertEquals(QrStatus.Idle, expiredVm.uiState.value.qrStatus)

        val failingVm = viewModel(
            FakeSessionCoordinator(),
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    addHandler { error("request failure") }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        failingVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, failingVm.uiState.value.qrStatus)
        assertNotNull(failingVm.uiState.value.qrError)
    }

    @Test
    fun cancellingQrLoginWhilePollingLeavesIdleWithoutLogin() = runTest(dispatcher) {
        val pollStarted = CompletableDeferred<Unit>()
        val session = FakeSessionCoordinator(loginResult = Result.success(established()))
        val api = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request -> when (request.url.encodedPath) {
                    "/oauth2/v1/device/code" -> respond(
                        """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    "/oauth2/v1/token" -> {
                        pollStarted.complete(Unit)
                        awaitCancellation()
                    }
                    else -> error("Unexpected QR request: ${request.url}")
                } }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val vm = viewModel(session, api)
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertTrue(pollStarted.isCompleted)
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        vm.cancelQrLogin()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)
        runCurrent()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus, "cancelled QR login must not overwrite Idle with Error")
        assertEquals(null, vm.uiState.value.qrError)
        assertTrue(session.loginCalls.isEmpty())
    }

    @Test
    fun qrLoginExchangeFailureAndSessionFailureShowError() = runTest(dispatcher) {
        val exchangeVm = viewModel(
            FakeSessionCoordinator(),
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    reuseHandlers = true
                    addHandler { request -> when (request.url.encodedPath) {
                        "/oauth2/v1/device/code" -> respond(
                            """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/oauth2/v1/token" -> respond(
                            """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/account/profile/v1" -> respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/1.1/users" -> error("exchange unavailable")
                        else -> error("Unexpected QR request: ${request.url}")
                    } }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        exchangeVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, exchangeVm.uiState.value.qrStatus)

        val syncSession = FakeSessionCoordinator(loginResult = Result.failure(RuntimeException("sync unavailable")))
        val syncVm = viewModel(
            syncSession,
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    reuseHandlers = true
                    addHandler { request -> when (request.url.encodedPath) {
                        "/oauth2/v1/device/code" -> respond(
                            """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/oauth2/v1/token" -> respond(
                            """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/account/profile/v1" -> respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/1.1/users" -> respond(
                            """{"sessionToken":"qr-session"}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unexpected QR request: ${request.url}")
                    } }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        syncVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, syncVm.uiState.value.qrStatus)
        assertNotNull(syncVm.uiState.value.qrError)
        assertFalse(syncVm.uiState.value.isLoggedIn)
        assertEquals(listOf("qr-session" to Server.CN), syncSession.loginCalls)
    }

    @Test
    fun resetSuccessRestoresInteractiveLoggedOutStateAndNextLoginIsExplicit() = runTest(dispatcher) {
        val session = FakeSessionCoordinator(
            resumeOutcome = SessionResumeOutcome.Resumed(
                profile = null,
                source = SessionResumeOutcome.Resumed.Source.LOCAL,
                syncResult = null
            ),
            resetResult = Result.success(Unit)
        )
        val vm = viewModel(session)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sessionRestored)

        vm.resetLocalSession()
        advanceUntilIdle()

        val reset = vm.uiState.value
        assertFalse(reset.isLoading)
        assertFalse(reset.isCheckingToken)
        assertFalse(reset.isLoggedIn)
        assertFalse(reset.sessionRestored)
        assertNull(reset.error)
        assertEquals(QrStatus.Idle, reset.qrStatus)
        assertEquals(1, session.resumeCalls, "successful reset must not re-run resume into a cleanup error")
        assertEquals(1, session.resetCalls)

        vm.updateToken("new-token")
        vm.login()
        advanceUntilIdle()
        assertEquals(listOf("new-token" to Server.CN), session.loginCalls)
        assertFalse(vm.uiState.value.sessionRestored)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun resetFailureAndThrownRecoveryFailureBothReenableControls() = runTest(dispatcher) {
        val failedSession = FakeSessionCoordinator(resetResult = Result.failure(RuntimeException("disk full")))
        val failedVm = viewModel(failedSession)
        advanceUntilIdle()
        failedVm.resetLocalSession()
        advanceUntilIdle()
        assertFalse(failedVm.uiState.value.isLoading)
        assertFalse(failedVm.uiState.value.isCheckingToken)
        assertNotNull(failedVm.uiState.value.error)

        val thrownSession = FakeSessionCoordinator(resetThrowable = RuntimeException("recovery failed"))
        val thrownVm = viewModel(thrownSession)
        advanceUntilIdle()
        thrownVm.resetLocalSession()
        advanceUntilIdle()
        assertFalse(thrownVm.uiState.value.isLoading)
        assertFalse(thrownVm.uiState.value.isCheckingToken)
        assertNotNull(thrownVm.uiState.value.error)
    }

    @Test
    fun recoveryFailuresMapToSpecificActionableCopy() = runTest(dispatcher) {
        val cases = listOf(
            SessionCredentialsPersistenceException(storageResetRequired = true) to
                UiText.Res(Res.string.login_error_storage_rebuild_required),
            SessionRecoveryUnresolvedException() to
                UiText.Res(Res.string.login_error_recovery_unresolved),
            SessionCleanupRequiredException(SessionCleanupFailure.PreviousAccountData) to
                UiText.Res(Res.string.login_error_previous_data_cleanup),
            SessionCleanupRequiredException(SessionCleanupFailure.PreviousCredentials) to
                UiText.Res(Res.string.login_error_previous_credential_cleanup),
            SessionCleanupRequiredException(SessionCleanupFailure.LoginFinalization) to
                UiText.Res(Res.string.login_error_login_finalization)
        )

        cases.forEach { (failure, expected) ->
            val vm = viewModel(FakeSessionCoordinator(loginResult = Result.failure(failure)))
            advanceUntilIdle()
            vm.updateToken("token")
            vm.login()
            advanceUntilIdle()
            assertEquals(expected, vm.uiState.value.error)
        }
    }

    @Test
    fun startupResetObligationMapsToResetPendingCopy() = runTest(dispatcher) {
        val vm = viewModel(
            FakeSessionCoordinator(
                resumeOutcome = SessionResumeOutcome.RequiresLogin(
                    hadToken = true,
                    localResetPending = true
                )
            )
        )

        advanceUntilIdle()

        assertEquals(UiText.Res(Res.string.login_error_reset_pending), vm.uiState.value.error)
    }

    // ── fakes ──────────────────────────────────────────────────────────

    private fun established(): SessionEstablished = SessionEstablished(
        profile = UserProfile("id", "nick", "", "", "", 0f, 0, 0, ""),
        syncResult = SyncSaveResult(save = emptySave(), committedAt = 1L, changedEntryCount = 0, snapshotCreated = false)
    )

    private fun emptySave(): Save = Save(gameRecord = emptyMap(), gameProgress = fakeProgress, user = fakeUser, summary = null)

    // advanceUntilIdle is a TestScope extension; VM-only constructions used by
    // resumedSessionLogsInFromBothSources run inside runTest, so mirror it via
    // a small helper driven by the shared dispatcher.
    private fun advanceUntilIdleFor(vm: LoginViewModel) {
        dispatcher.scheduler.advanceUntilIdle()
    }

    private inner class FakeSessionCoordinator(
        var resumeOutcome: SessionResumeOutcome = SessionResumeOutcome.RequiresLogin(hadToken = false),
        var loginResult: Result<SessionEstablished> = Result.success(established()),
        var refreshResult: Result<SyncSaveResult> = Result.failure(SessionNotLoggedInException()),
        var logoutResult: Result<SessionLogoutOutcome> = Result.success(SessionLogoutOutcome.Completed),
        var resetResult: Result<Unit> = Result.success(Unit),
        var resetThrowable: Throwable? = null
    ) : SessionCoordinator {
        val loginCalls = mutableListOf<Pair<String, Server>>()
        var resumeCalls = 0
        var refreshCalls = 0
        var logoutCalls = 0
        var resetCalls = 0

        override suspend fun login(sessionToken: String, server: Server): Result<SessionEstablished> {
            loginCalls += sessionToken to server
            return loginResult
        }

        override suspend fun resumeSession(): SessionResumeOutcome {
            resumeCalls++
            return resumeOutcome
        }

        override suspend fun refresh(): Result<SyncSaveResult> {
            refreshCalls++
            return refreshResult
        }

        override suspend fun logout(): Result<SessionLogoutOutcome> {
            logoutCalls++
            return logoutResult
        }

        override suspend fun resetLocalSession(): Result<Unit> {
            resetCalls++
            resetThrowable?.let { throw it }
            return resetResult
        }

        override suspend fun resetSessionToken(): SessionResetOutcome = SessionResetOutcome.NotLoggedIn
    }

    private object FakeApiCrypto : ApiCrypto {
        override fun md5Hex(data: String): String = "fixed-md5"
        override fun hmacSha1(data: String, key: String): ByteArray = byteArrayOf(1, 2, 3)
    }

    private companion object {
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
}
