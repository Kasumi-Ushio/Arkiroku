package org.kasumi321.ushio.phitracker.data.platform

import cnames.structs.__CFDictionary
import cnames.structs.__CFString
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.repository.SettingsRepositoryImpl
import org.kasumi321.ushio.phitracker.data.session.SessionStateStore
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringCompare
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecValueData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureKeyValueStorageIosTest {
    @Test
    fun keychainPersistsUpdatesAndDeletesWithoutRemoveAddWindow() {
        val storage = createSecureKeyValueStorage("phi-tracker-test-${NSUUID().UUIDString}")

        assertEquals(StorageWriteResult.Persisted, storage.putString("credential", "old"))
        assertEquals("old", storage.getString("credential"))
        assertEquals(StorageWriteResult.Persisted, storage.putString("credential", "new"))
        assertEquals("new", storage.getString("credential"))
        assertEquals(StorageWriteResult.Persisted, storage.remove("credential"))
        assertNull(storage.getString("credential"))
    }

    @Test
    fun userDefaultsApiTokenMigratesToKeychainAndIsRemovedFromPlainSettings() = runTest {
        val name = "phi-tracker-migration-${NSUUID().UUIDString}"
        val legacy = createLegacySettingsStorage(name)!!
        legacy.putString("api_token", "legacy-token")
        val plain = createPlainKeyValueStorage(name)
        val secure = createSecureKeyValueStorage(name)

        val repository = SettingsRepositoryImpl(
            storage = plain,
            preloadStorage = createPlainKeyValueStorage("preload-$name"),
            secureStorage = secure,
            legacySecureStorage = legacy
        )

        assertEquals("legacy-token", secure.getString("api_token"))
        assertEquals("legacy-token", repository.apiToken.first())
        assertNull(legacy.getString("api_token"))
        assertNull(plain.getString("api_token"))
    }

    @Test
    fun emptyStringRoundTripsThroughKeychainAndCanBeRebuilt() {
        val service = "phi-tracker-empty-${NSUUID().UUIDString}"
        val storage = createSecureKeyValueStorage(service)
        try {
            // An explicitly cleared API token is a real value, not a read failure.
            assertEquals(StorageWriteResult.Persisted, storage.putString("api_token", ""))
            assertEquals("", storage.getString("api_token"))
            assertEquals("", createSecureKeyValueStorage(service).getString("api_token"))

            // Rebuilding the value after a clear keeps update-or-add working.
            assertEquals(StorageWriteResult.Persisted, storage.putString("api_token", "token-1"))
            assertEquals("token-1", storage.getString("api_token"))
            assertEquals(StorageWriteResult.Persisted, storage.putString("api_token", ""))
            assertEquals("", createSecureKeyValueStorage(service).getString("api_token"))

            assertEquals(StorageWriteResult.Persisted, storage.remove("api_token"))
            assertNull(storage.getString("api_token"))
            // Deleting an absent item stays an idempotent success.
            assertEquals(StorageWriteResult.Persisted, storage.remove("api_token"))
        } finally {
            storage.remove("api_token")
        }
    }

    @Test
    fun explicitlyEmptyApiTokenDoesNotResurrectLegacyPlaintextValue() = runTest {
        val name = "phi-tracker-empty-token-${NSUUID().UUIDString}"
        val legacy = createLegacySettingsStorage(name)!!
        legacy.putString("api_token", "legacy-token")
        val plain = createPlainKeyValueStorage(name)
        val secure = createSecureKeyValueStorage(name)
        assertEquals(StorageWriteResult.Persisted, secure.putString("api_token", ""))

        try {
            val repository = SettingsRepositoryImpl(
                storage = plain,
                preloadStorage = createPlainKeyValueStorage("preload-$name"),
                secureStorage = secure,
                legacySecureStorage = legacy
            )

            assertEquals("", secure.getString("api_token"))
            assertEquals("", repository.apiToken.first())
            assertNull(legacy.getString("api_token"))
            assertNull(plain.getString("api_token"))
            // A fresh instance still sees the authoritative empty value.
            assertEquals("", createSecureKeyValueStorage(name).getString("api_token"))
            assertEquals("", repository.apiToken.first())
        } finally {
            secure.remove("api_token")
            legacy.remove("api_token")
            plain.remove("api_token")
        }
    }

    @Test
    fun inconclusivePresenceLeavesTheLegacyIntentAndBlocksSessionRecovery() {
        val suiteName = "phi-tracker-legacy-unresolved-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.logout_pending"
        defaults.setObject("true", forKey = scopedKey)
        defaults.synchronize()

        try {
            val secure = RecordingStorage()
            val storage = createMigratingSessionStateStorage(
                name = name,
                secure = secure,
                defaults = defaults,
                keyExists = { null }
            )

            assertEquals(
                LegacySessionMigration.Unresolved(listOf("logout_pending")),
                storage.legacyMigration()
            )
            assertTrue(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
            assertTrue(secure.values.isEmpty(), "an inconclusive probe must not import anything")
            assertEquals("true", defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun explicitResetClearsSessionRestoresEmptyApiTokenAndProvesPersistence() {
        val service = "phi-tracker-reset-${NSUUID().UUIDString}"
        val storage = createSecureKeyValueStorage(service)
        val manager = TokenManager(storage)
        manager.saveToken("stale-token", org.kasumi321.ushio.phitracker.domain.model.Server.CN).getOrThrow()
        manager.savePlayerBinding("player-a").getOrThrow()
        storage.putString("api_token", "")

        try {
            manager.resetStorage().getOrThrow()

            assertNull(storage.getString("session_token"))
            assertNull(storage.getString("server"))
            assertNull(storage.getString("session_player_id"))
            assertEquals("", storage.getString("api_token"))
            val reopened = createSecureKeyValueStorage(service)
            assertEquals("", reopened.getString("api_token"))
            assertEquals(StorageWriteResult.Persisted, reopened.putString("probe", "value"))
            assertEquals("value", reopened.getString("probe"))
            assertEquals(StorageWriteResult.Persisted, reopened.remove("probe"))
            assertNull(reopened.getString("probe"))
        } finally {
            storage.resetStorage()
        }
    }

    @Test
    fun unresolvedMigrationCanBeRetriedInsideTheSameProcess() {
        val suiteName = "phi-tracker-legacy-reprobe-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.logout_pending"
        defaults.setObject("true", forKey = scopedKey)
        defaults.synchronize()
        var probeCount = 0

        try {
            val secure = RecordingStorage()
            val storage = createMigratingSessionStateStorage(
                name = name,
                secure = secure,
                defaults = defaults,
                keyExists = { if (probeCount++ == 0) null else false }
            )
            val store = SessionStateStore(storage)
            assertTrue(store.hasUnresolvedLegacyRecovery)

            store.retryUnresolvedLegacyRecovery()

            assertFalse(store.hasUnresolvedLegacyRecovery)
            assertEquals(LegacySessionMigration.Completed, storage.legacyMigration())
            assertEquals("true", secure.values["logout_pending"])
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun laterStartupImportsTheOldIntentOnceTheProbeWorks() {
        // The first startup cannot tell whether the Keychain already holds
        // the old intent, so it blocks sessions instead of deferring the
        // import to an unconditional replay. The next startup answers the
        // probe and imports the intent before any new login can be made.
        val suiteName = "phi-tracker-legacy-retry-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.logout_pending"
        defaults.setObject("true", forKey = scopedKey)
        defaults.synchronize()

        try {
            val secure = RecordingStorage()
            val first = createMigratingSessionStateStorage(
                name = name,
                secure = secure,
                defaults = defaults,
                keyExists = { null }
            )
            assertTrue(SessionStateStore(first).hasUnresolvedLegacyRecovery)

            val second = createMigratingSessionStateStorage(
                name = name,
                secure = secure,
                defaults = defaults,
                keyExists = { false }
            )

            assertEquals(LegacySessionMigration.Completed, second.legacyMigration())
            assertEquals("true", secure.values["logout_pending"])
            assertNull(defaults.stringForKey(scopedKey))
            val store = SessionStateStore(second)
            assertFalse(store.hasUnresolvedLegacyRecovery)
            assertTrue(store.isLogoutPending)
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun durableSessionValueWinsOverLegacyIntentAndStaleCopyIsRemoved() {
        val suiteName = "phi-tracker-legacy-conflict-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.pending_login_owner"
        defaults.setObject("owner-old", forKey = scopedKey)
        defaults.synchronize()

        try {
            val secure = RecordingStorage().apply { values["pending_login_owner"] = "owner-new" }
            val storage = createMigratingSessionStateStorage(name, secure, defaults)

            assertEquals(LegacySessionMigration.Completed, storage.legacyMigration())
            assertEquals("owner-new", secure.values["pending_login_owner"])
            assertNull(defaults.stringForKey(scopedKey))

            // A stale plaintext copy that survived cleanup must not come back
            // after the durable value was explicitly removed: the marker wins.
            defaults.setObject("owner-old", forKey = scopedKey)
            defaults.synchronize()
            secure.values.remove("pending_login_owner")

            val reopened = createMigratingSessionStateStorage(name, secure, defaults)
            assertEquals(LegacySessionMigration.Completed, reopened.legacyMigration())
            assertNull(secure.values["pending_login_owner"])
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun explicitEmptySessionValueBeatsLegacyIntent() {
        val suiteName = "phi-tracker-legacy-empty-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.logout_pending"
        defaults.setObject("true", forKey = scopedKey)
        defaults.synchronize()

        try {
            val secure = RecordingStorage().apply { values["logout_pending"] = "" }
            val storage = createMigratingSessionStateStorage(name, secure, defaults)

            assertEquals(LegacySessionMigration.Completed, storage.legacyMigration())
            assertEquals("", secure.values["logout_pending"])
            assertFalse(SessionStateStore(storage).isLogoutPending)
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun inertLegacyValuesDoNotBlockSessionRecovery() {
        val suiteName = "phi-tracker-legacy-inert-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        defaults.setObject("false", forKey = "$name.logout_pending")
        defaults.setObject("  ", forKey = "$name.pending_login_owner")
        defaults.synchronize()

        try {
            val storage = createMigratingSessionStateStorage(
                name = name,
                secure = RecordingStorage(),
                defaults = defaults,
                keyExists = { null }
            )

            assertEquals(LegacySessionMigration.NotRequired, storage.legacyMigration())
            assertFalse(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
            assertNull(defaults.stringForKey("$name.logout_pending"))
            assertNull(defaults.stringForKey("$name.pending_login_owner"))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun failedKeychainWriteIsUnresolvedInsteadOfDeferred() {
        val suiteName = "phi-tracker-legacy-write-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.pending_login_owner"
        defaults.setObject("owner-a", forKey = scopedKey)
        defaults.synchronize()

        try {
            val storage = createMigratingSessionStateStorage(
                name = name,
                secure = RecordingStorage(StorageWriteResult.Failed),
                defaults = defaults
            )

            assertEquals(
                LegacySessionMigration.Unresolved(listOf("pending_login_owner")),
                storage.legacyMigration()
            )
            assertTrue(SessionStateStore(storage).hasUnresolvedLegacyRecovery)
            assertEquals("owner-a", defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun discardingUnresolvedLegacyIntentsClearsDefaultsAndUnblocks() {
        val suiteName = "phi-tracker-legacy-discard-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.logout_pending"
        defaults.setObject("true", forKey = scopedKey)
        defaults.synchronize()

        try {
            val storage = createMigratingSessionStateStorage(
                name = name,
                secure = RecordingStorage(),
                defaults = defaults,
                keyExists = { null }
            )
            val store = SessionStateStore(storage)
            assertTrue(store.hasUnresolvedLegacyRecovery)

            store.discardUnresolvedLegacyRecovery().getOrThrow()

            assertFalse(store.hasUnresolvedLegacyRecovery)
            assertEquals(LegacySessionMigration.Completed, storage.legacyMigration())
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun legacyUserDefaultsSessionStateMigratesToKeychain() {
        val name = "phi-tracker-session-${NSUUID().UUIDString}"
        val defaults = NSUserDefaults.standardUserDefaults
        val keys = listOf("pending_login_owner", "logout_pending", "login_required")
        keys.forEach { defaults.setObject("true", forKey = "$name.$it") }
        defaults.setObject("owner-a", forKey = "$name.pending_login_owner")
        defaults.synchronize()

        val storage = createSessionStateStorage(name)
        try {
            val store = SessionStateStore(storage)
            assertEquals("owner-a", store.pendingLoginOwner)
            assertTrue(store.isLogoutPending)
            assertTrue(store.isLoginRequired)
            keys.forEach { assertNull(defaults.stringForKey("$name.$it")) }

            // A plaintext copy surviving cleanup must not resurrect a value
            // that was explicitly removed after migration.
            defaults.setObject("true", forKey = "$name.logout_pending")
            defaults.synchronize()
            assertEquals(StorageWriteResult.Persisted, storage.remove("logout_pending"))
            val reopened = createSessionStateStorage(name)
            assertNull(reopened.getString("logout_pending"))
            assertNull(defaults.stringForKey("$name.logout_pending"))
        } finally {
            clearDefaults(name)
            keys.forEach { storage.remove(it) }
        }
    }

    @Test
    fun failedSessionStateMigrationRetriesAndKeepsExplicitRemovalDeleted() {
        val suiteName = "phi-tracker-session-retry-${NSUUID().UUIDString}"
        val name = "session_state"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val scopedKey = "$name.pending_login_owner"
        defaults.setObject("owner-a", forKey = scopedKey)
        defaults.synchronize()

        try {
            val failing = RecordingStorage(StorageWriteResult.Failed)
            migrateSessionStateStorage(name, failing, defaults)
            assertEquals("owner-a", defaults.stringForKey(scopedKey))
            assertNull(failing.values["pending_login_owner"])

            val inconclusive = RecordingStorage()
            migrateSessionStateStorage(name, inconclusive, defaults, keyExists = { null })
            assertEquals("owner-a", defaults.stringForKey(scopedKey))
            assertNull(inconclusive.values["pending_login_owner"])

            val retrying = RecordingStorage()
            migrateSessionStateStorage(name, retrying, defaults)
            assertEquals("owner-a", retrying.values["pending_login_owner"])
            assertNull(defaults.stringForKey(scopedKey))

            // Simulate a stale plaintext copy that survived cleanup, then
            // remove the Keychain value explicitly. The marker must win.
            defaults.setObject("owner-a", forKey = scopedKey)
            defaults.synchronize()
            retrying.values.remove("pending_login_owner")
            migrateSessionStateStorage(name, retrying, defaults)
            assertNull(retrying.values["pending_login_owner"])
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun legacyKeychainItemWithoutAccessibilityIsUpgradedInPlace() {
        val service = "phi-tracker-accessibility-${NSUUID().UUIDString}"
        val account = "session_token"
        val value = "legacy-token"
        addKeychainItem(service, account, value)

        try {
            val storage = createSecureKeyValueStorage(service)
            assertEquals(value, storage.getString(account))
            assertTrue(isThisDeviceOnly(service, account))

            val reopened = createSecureKeyValueStorage(service)
            assertEquals(value, reopened.getString(account))
            assertTrue(isThisDeviceOnly(service, account))
        } finally {
            createSecureKeyValueStorage(service).remove(account)
        }
    }
}

private class RecordingStorage(
    private val writeResult: StorageWriteResult = StorageWriteResult.Persisted
) : SecureKeyValueStorage {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String): StorageWriteResult {
        if (writeResult == StorageWriteResult.Persisted) values[key] = value
        return writeResult
    }

    override fun remove(key: String): StorageWriteResult {
        if (writeResult == StorageWriteResult.Persisted) values.remove(key)
        return writeResult
    }
}

private fun SecureKeyValueStorage.legacyMigration(): LegacySessionMigration =
    assertIs<LegacySessionMigrationSource>(this).legacySessionMigration

private fun clearDefaults(name: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    val markerPrefix = "__keychain_session_migration.$name."
    val keys = defaults.dictionaryRepresentation().keys
        .filterIsInstance<String>()
        .filter { it.startsWith("$name.") || it.startsWith(markerPrefix) }
    keys.forEach(defaults::removeObjectForKey)
    defaults.synchronize()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun addKeychainItem(service: String, account: String, value: String) {
    val encoded = value.encodeToByteArray()
    val data = encoded.usePinned { pinned ->
        NSData.create(
            bytes = if (encoded.isEmpty()) null else pinned.addressOf(0),
            length = encoded.size.toULong()
        )
    }
    memScoped {
        val cfService = CFBridgingRetain(service)
        val cfAccount = CFBridgingRetain(account)
        val cfData = CFBridgingRetain(data)
        try {
            val attributes = dictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
                kSecValueData to cfData
            )
            val status = try {
                SecItemAdd(attributes, null)
            } finally {
                CFBridgingRelease(attributes)
            }
            assertEquals(errSecSuccess, status)
        } finally {
            CFBridgingRelease(cfData)
            CFBridgingRelease(cfAccount)
            CFBridgingRelease(cfService)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun isThisDeviceOnly(service: String, account: String): Boolean = memScoped {
    val result = alloc<CFTypeRefVar>()
    val cfService = CFBridgingRetain(service)
    val cfAccount = CFBridgingRetain(account)
    try {
        val query = dictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfAccount,
            kSecReturnAttributes to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val status = try {
            SecItemCopyMatching(query, result.ptr)
        } finally {
            CFBridgingRelease(query)
        }
        assertEquals(errSecSuccess, status)

        val raw = result.value ?: error("keychain attributes missing")
        try {
            val attributes = raw.reinterpret<__CFDictionary>()
            val accessibility = CFDictionaryGetValue(attributes, kSecAttrAccessible)
                ?.reinterpret<__CFString>()
                ?: error("keychain accessibility missing")
            CFStringCompare(
                accessibility,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                0uL
            ) == 0L
        } finally {
            CFBridgingRelease(raw)
        }
    } finally {
        CFBridgingRelease(cfAccount)
        CFBridgingRelease(cfService)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.dictionaryOf(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val keys = allocArrayOf(*items.map { it.first }.toTypedArray())
    val values = allocArrayOf(*items.map { it.second }.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        items.size.convert(),
        null,
        null
    )
}
