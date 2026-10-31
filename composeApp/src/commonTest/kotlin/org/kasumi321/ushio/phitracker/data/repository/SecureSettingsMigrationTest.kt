package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.SecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * api_token must live in the secure store. Old installs kept it in the plain
 * settings storage; the repository moves it once, deleting the plaintext copy
 * only after the secured value is readable back.
 */
class SecureSettingsMigrationTest {

    private class MemoryStorage : SecureKeyValueStorage {
        val map = mutableMapOf<String, String>()
        var writeShouldFail = false
        var removeValueOnFailedWrite = false
        var writeResult = StorageWriteResult.Persisted
        var removeResult = StorageWriteResult.Persisted

        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String): StorageWriteResult {
            if (writeShouldFail) {
                if (removeValueOnFailedWrite) map.remove(key)
                error("injected secure-store write failure")
            }
            if (writeResult != StorageWriteResult.Failed) map[key] = value
            return writeResult
        }
        override fun remove(key: String): StorageWriteResult {
            if (removeResult != StorageWriteResult.Failed) map.remove(key)
            return removeResult
        }
    }

    @Test
    fun legacyPlaintextTokenMovesToSecureStorageAndIsDeletedAfterwards() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "  secret-token  " }
        val secure = MemoryStorage()

        val repository = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        assertEquals("secret-token", secure.map["api_token"])
        assertNull(plain.map["api_token"], "the plaintext copy is dropped once the secured one is readable back")
        assertEquals("secret-token", repository.apiToken.first())
    }

    @Test
    fun failedSecureWriteKeepsTheLegacyCopyUsable() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "secret-token" }
        val secure = MemoryStorage().apply { writeShouldFail = true }

        SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        assertEquals("secret-token", plain.map["api_token"], "a failed secure write must not lose the only usable copy")
    }

    @Test
    fun alreadySecuredTokenDropsStalePlaintextWithoutRewriting() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "old-value" }
        val secure = MemoryStorage().apply { map["api_token"] = "current-value" }

        SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        assertEquals("current-value", secure.map["api_token"])
        assertNull(plain.map["api_token"])
    }

    @Test
    fun newWritesGoToSecureStorageOnly() = runTest {
        val plain = MemoryStorage()
        val secure = MemoryStorage()
        val repository = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        repository.setApiToken("new-token").getOrThrow()

        assertEquals("new-token", secure.map["api_token"])
        assertNull(plain.map["api_token"])
    }

    @Test
    fun ephemeralReadbackNeverDeletesLegacyTokenAndMigrationRetries() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "legacy-token" }
        val secure = MemoryStorage().apply { writeResult = StorageWriteResult.Ephemeral }

        SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        assertEquals("legacy-token", plain.map["api_token"])
        secure.writeResult = StorageWriteResult.Persisted
        SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)
        assertEquals("legacy-token", secure.map["api_token"])
        assertNull(plain.map["api_token"])
    }

    @Test
    fun ephemeralNewWriteRestoresPreviousValueInsteadOfPublishingIt() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "legacy-token" }
        val secure = MemoryStorage().apply { writeResult = StorageWriteResult.Ephemeral }
        val repository = SettingsRepositoryImpl(
            storage = plain,
            preloadStorage = MemoryStorage(),
            secureStorage = secure
        )

        val result = repository.setApiToken("new-token")

        assertEquals(true, result.isFailure)
        assertEquals("legacy-token", secure.map["api_token"])
        assertEquals("legacy-token", repository.apiToken.first())
        assertEquals("legacy-token", plain.map["api_token"])
    }

    @Test
    fun silentNewWriteFailureKeepsLegacyValueAndPublishedState() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "legacy-token" }
        val secure = MemoryStorage().apply { writeResult = StorageWriteResult.Failed }
        val repository = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        val result = repository.setApiToken("new-token")

        assertEquals(true, result.isFailure)
        assertEquals("legacy-token", plain.map["api_token"])
        assertNull(secure.map["api_token"])
        assertEquals("legacy-token", repository.apiToken.first())
    }

    @Test
    fun failedWriteImmediatelyRefreshesStateWhenStoredTokenDisappears() = runTest {
        val secure = MemoryStorage().apply {
            map["api_token"] = "current-token"
            writeShouldFail = true
            removeValueOnFailedWrite = true
        }
        val repository = SettingsRepositoryImpl(
            storage = MemoryStorage(),
            preloadStorage = MemoryStorage(),
            secureStorage = secure
        )
        assertEquals("current-token", repository.apiToken.first())

        val result = repository.setApiToken("new-token")

        assertEquals(true, result.isFailure)
        assertEquals("", repository.apiToken.first())
    }

    @Test
    fun legacyEncryptedTokenMovesDirectlyToSecureWithoutPlainCopy() = runTest {
        val plain = MemoryStorage()
        val legacySecure = MemoryStorage().apply { map["api_token"] = "legacy-encrypted-token" }
        val secure = MemoryStorage()

        val repository = SettingsRepositoryImpl(
            storage = plain,
            preloadStorage = MemoryStorage(),
            secureStorage = secure,
            legacySecureStorage = legacySecure
        )

        assertEquals("legacy-encrypted-token", secure.map["api_token"])
        assertNull(legacySecure.map["api_token"])
        assertNull(plain.map["api_token"])
        assertEquals("legacy-encrypted-token", repository.apiToken.first())
    }

    @Test
    fun legacyEncryptedTokenSurvivesEveryFailedWriteAndRetries() = runTest {
        listOf(StorageWriteResult.Failed, StorageWriteResult.Ephemeral).forEach { outcome ->
            val plain = MemoryStorage()
            val legacySecure = MemoryStorage().apply { map["api_token"] = "legacy-token" }
            val secure = MemoryStorage().apply { writeResult = outcome }

            val repository = SettingsRepositoryImpl(
                storage = plain,
                preloadStorage = MemoryStorage(),
                secureStorage = secure,
                legacySecureStorage = legacySecure
            )

            assertEquals("legacy-token", legacySecure.map["api_token"])
            assertEquals("legacy-token", repository.apiToken.first())
            assertNull(plain.map["api_token"])

            secure.writeResult = StorageWriteResult.Persisted
            SettingsRepositoryImpl(
                storage = plain,
                preloadStorage = MemoryStorage(),
                secureStorage = secure,
                legacySecureStorage = legacySecure
            )
            assertEquals("legacy-token", secure.map["api_token"])
            assertNull(legacySecure.map["api_token"])
        }
    }

    @Test
    fun legacyEncryptedTokenSurvivesThrownSecureWriteFailure() = runTest {
        val plain = MemoryStorage()
        val legacySecure = MemoryStorage().apply { map["api_token"] = "legacy-token" }
        val secure = MemoryStorage().apply { writeShouldFail = true }

        SettingsRepositoryImpl(
            storage = plain,
            preloadStorage = MemoryStorage(),
            secureStorage = secure,
            legacySecureStorage = legacySecure
        )

        assertEquals("legacy-token", legacySecure.map["api_token"])
        assertNull(plain.map["api_token"])
    }

    @Test
    fun persistedNewTokenIsSuccessfulEvenWhenLegacyCleanupNeedsRetry() = runTest {
        val plain = MemoryStorage().apply {
            map["api_token"] = "legacy-token"
            removeResult = StorageWriteResult.Failed
        }
        val secure = MemoryStorage()
        val repository = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        val result = repository.setApiToken("new-token")

        assertEquals(true, result.isSuccess)
        assertEquals("new-token", secure.map["api_token"])
        assertEquals("new-token", repository.apiToken.first())
        assertEquals("legacy-token", plain.map["api_token"])

        plain.removeResult = StorageWriteResult.Persisted
        val recreated = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)
        assertNull(plain.map["api_token"])
        assertEquals("new-token", recreated.apiToken.first())
    }

    @Test
    fun explicitEmptySecureTokenDoesNotResurrectLegacyValue() = runTest {
        val plain = MemoryStorage().apply { map["api_token"] = "legacy-token" }
        val secure = MemoryStorage().apply { map["api_token"] = "" }

        val repository = SettingsRepositoryImpl(storage = plain, preloadStorage = MemoryStorage(), secureStorage = secure)

        assertEquals("", secure.map["api_token"])
        assertEquals("", repository.apiToken.first())
        assertNull(plain.map["api_token"])
    }
}
