package org.kasumi321.ushio.phitracker.data.platform

import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.session.SessionCredentialsPersistenceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenManagerTest {

    @Test
    fun saveTrimsTokenAndStoresBothKeys() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)

        manager.saveToken("  abc123  ", Server.GLOBAL)

        assertEquals("abc123", storage.map["session_token"])
        assertEquals("GLOBAL", storage.map["server"])
        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.GLOBAL, result?.second)
    }

    @Test
    fun getReturnsNullWhenNoTokenStored() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)

        assertNull(manager.getToken())
    }

    @Test
    fun getReturnsEmptyStringForExplicitEmptyToken() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = ""
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun serverDefaultsToCNWhenKeyMissing() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = "abc123"
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun serverDefaultsToCNWhenValueInvalid() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = "abc123"
        storage.map["server"] = "INVALID_SERVER"
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun clearRemovesBothKeysMakesGetReturnNull() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)
        manager.saveToken("abc123", Server.CN)

        manager.clearToken()

        assertNull(storage.map["session_token"])
        assertNull(storage.map["server"])
        assertNull(manager.getToken())
    }

    @Test
    fun playerBindingRoundTripsAndClearsTogetherWithTheToken() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)
        manager.saveToken("abc123", Server.CN).getOrThrow()
        manager.savePlayerBinding("player-1").getOrThrow()

        assertEquals("player-1", manager.getPlayerBinding())

        manager.clearToken().getOrThrow()

        assertNull(manager.getPlayerBinding(), "clearToken must also drop the token↔account binding")
    }

    @Test
    fun silentTokenWriteFailureRestoresPreviousCredentials() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "old-token"
            map["server"] = "CN"
            writeResult = StorageWriteResult.Failed
        }

        val result = TokenManager(storage).saveToken("new-token", Server.GLOBAL)

        assertTrue(result.isFailure)
        assertEquals("old-token", storage.map["session_token"])
        assertEquals("CN", storage.map["server"])
    }

    @Test
    fun ephemeralReadbackDoesNotProveCredentialPersistence() {
        val storage = FakeSecureKeyValueStorage().apply {
            writeResult = StorageWriteResult.Ephemeral
        }

        val result = TokenManager(storage).saveToken("new-token", Server.CN)

        assertTrue(result.isFailure)
        assertNull(storage.map["session_token"])
    }

    @Test
    fun failedBindingWriteRestoresPreviousBinding() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_player_id"] = "player-old"
            writeResult = StorageWriteResult.Failed
        }

        val result = TokenManager(storage).savePlayerBinding("player-new")

        assertTrue(result.isFailure)
        assertEquals("player-old", storage.map["session_player_id"])
    }

    @Test
    fun clearReportsSilentRemovalFailure() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "stale-token"
            map["session_player_id"] = "player-a"
            removeResult = StorageWriteResult.Failed
        }

        val result = TokenManager(storage).clearToken()

        assertTrue(result.isFailure)
        assertEquals("stale-token", storage.map["session_token"])
    }

    @Test
    fun clearVerifiesEveryCredentialKeyIndependently() {
        listOf("session_token", "server", "session_player_id").forEach { failedKey ->
            val storage = FakeSecureKeyValueStorage().apply {
                map["session_token"] = "stale-token"
                map["server"] = "CN"
                map["session_player_id"] = "player-a"
                removeResultsByKey[failedKey] = StorageWriteResult.Failed
            }

            val result = TokenManager(storage).clearToken()

            assertTrue(result.isFailure, "removal must fail when $failedKey is not deleted")
            assertTrue(storage.map[failedKey] != null)
            assertEquals(failedKey != "session_token", storage.map["session_token"] == null)
            assertEquals(failedKey != "server", storage.map["server"] == null)
            assertEquals(failedKey != "session_player_id", storage.map["session_player_id"] == null)
        }
    }

    @Test
    fun resetStorageClearsSessionButPreservesExplicitEmptyApiToken() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "stale-token"
            map["server"] = "CN"
            map["session_player_id"] = "player-a"
            map["api_token"] = ""
        }

        val result = TokenManager(storage).resetStorage()

        assertTrue(result.isSuccess)
        assertEquals(1, storage.resetCalls)
        assertNull(storage.map["session_token"])
        assertNull(storage.map["server"])
        assertNull(storage.map["session_player_id"])
        assertEquals("", storage.map["api_token"])
    }

    @Test
    fun resetStorageRecoversApiTokenFromReadableLegacySource() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "stale-token"
        }

        val result = TokenManager(storage, recoverLegacyApiToken = { "legacy-api-token" }).resetStorage()

        assertTrue(result.isSuccess)
        assertEquals("legacy-api-token", storage.map["api_token"])
        assertNull(storage.map["session_token"])
    }

    @Test
    fun explicitEmptyPrimaryApiTokenDoesNotResurrectLegacyValue() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["api_token"] = ""
        }

        TokenManager(storage, recoverLegacyApiToken = { "stale-api-token" }).resetStorage().getOrThrow()

        assertEquals("", storage.map["api_token"])
    }

    @Test
    fun ephemeralCredentialWriteRequiresExplicitStorageRebuild() {
        val storage = FakeSecureKeyValueStorage().apply {
            writeResult = StorageWriteResult.Ephemeral
        }

        val result = TokenManager(storage).saveToken("token", Server.CN)
        val error = result.exceptionOrNull() as SessionCredentialsPersistenceException

        assertTrue(error.storageResetRequired)
    }

    @Test
    fun resetFailureIsNotReportedAsSuccessfulRecovery() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "stale-token"
            resetResult = Result.failure(IllegalStateException("keychain unavailable"))
        }

        val result = TokenManager(storage).resetStorage()

        assertTrue(result.isFailure)
        assertEquals("stale-token", storage.map["session_token"])
    }

    @Test
    fun apiTokenRestoreFailureFailsResetWithoutResurrectingSession() {
        val storage = FakeSecureKeyValueStorage().apply {
            map["session_token"] = "stale-token"
            map["server"] = "CN"
            map["api_token"] = ""
            writeResultsByKey["api_token"] = StorageWriteResult.Failed
        }

        val result = TokenManager(storage).resetStorage()

        assertTrue(result.isFailure)
        assertNull(storage.map["session_token"])
        assertNull(storage.map["server"])
        assertNull(storage.map["api_token"])
    }
}

open class FakeSecureKeyValueStorage : SecureKeyValueStorage {
    val map = mutableMapOf<String, String>()
    var writeResult = StorageWriteResult.Persisted
    var removeResult = StorageWriteResult.Persisted
    val removeResultsByKey = mutableMapOf<String, StorageWriteResult>()
    val writeResultsByKey = mutableMapOf<String, StorageWriteResult>()
    var resetResult: Result<Unit> = Result.success(Unit)
    var resetCalls = 0

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String): StorageWriteResult {
        val result = writeResultsByKey[key] ?: writeResult
        if (result != StorageWriteResult.Failed) map[key] = value
        return result
    }

    override fun remove(key: String): StorageWriteResult {
        val result = removeResultsByKey[key] ?: removeResult
        if (result != StorageWriteResult.Failed) map.remove(key)
        return result
    }

    override fun resetStorage(): Result<Unit> {
        resetCalls++
        if (resetResult.isSuccess) map.clear()
        return resetResult
    }
}
