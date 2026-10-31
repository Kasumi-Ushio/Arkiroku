@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.UnsafeNumber::class)

package org.kasumi321.ushio.phitracker.data.platform

import cnames.structs.__CFArray
import cnames.structs.__CFDictionary
import cnames.structs.__CFString
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
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
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Credentials always go to the Keychain — never the plaintext plist. */
actual fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage =
    KeychainStorage(service = name)

actual fun createPlainKeyValueStorage(name: String): SecureKeyValueStorage =
    UserDefaultsStorage(name = name, defaults = NSUserDefaults.standardUserDefaults)

actual fun createSessionStateStorage(name: String): SecureKeyValueStorage {
    val storage = KeychainStorage(service = name)
    return createMigratingSessionStateStorage(
        name = name,
        secure = storage,
        defaults = NSUserDefaults.standardUserDefaults,
        // Presence must distinguish "definitely missing" from "lookup failed";
        // a failed read falls back to the legacy value and would overwrite a
        // newer Keychain value.
        keyExists = storage::containsKey
    )
}

/**
 * Wires the Keychain-backed storage to the legacy NSUserDefaults migration.
 * The migration result stays attached to the returned storage so the session
 * coordinator can refuse login/resume while a legacy recovery intent is
 * unresolved. Explicit injection of the secure store and defaults keeps that
 * failure mode testable without touching the real Keychain.
 */
internal fun createMigratingSessionStateStorage(
    name: String,
    secure: SecureKeyValueStorage,
    defaults: NSUserDefaults,
    keyExists: (String) -> Boolean? = { secure.getString(it) != null }
): SecureKeyValueStorage {
    val migrator = SessionStateMigrator(name, secure, defaults, keyExists)
    migrator.run()
    return MigratingSessionStateStorage(secure, migrator)
}

actual fun createLegacySettingsStorage(name: String): SecureKeyValueStorage? =
    createPlainKeyValueStorage(name)

/**
 * Moves recovery intents written by earlier builds from the plaintext
 * NSUserDefaults domain into the Keychain and reports whether the import can
 * be trusted.
 *
 * A value is copied only when the Keychain has no item for that key. The
 * per-key defaults marker is written after the Keychain write reads back
 * identically; only then is the plaintext copy deleted. If the Keychain
 * already holds the key, the plaintext copy is stale and is deleted after
 * recording the marker without overwriting the Keychain value. An established
 * marker keeps a later explicit [SecureKeyValueStorage.remove] from being
 * undone by a stale plaintext copy.
 *
 * Anything short of a durable marker — an inconclusive presence lookup, a
 * failed Keychain write or a failed marker write — leaves the legacy intent
 * live and is reported as [LegacySessionMigration.Unresolved]. Callers must
 * not establish a session in that state: a later startup would import the old
 * intent over the new account and delete its data.
 */
internal class SessionStateMigrator(
    private val name: String,
    private val secure: SecureKeyValueStorage,
    private val defaults: NSUserDefaults,
    private val keyExists: (String) -> Boolean? = { secure.getString(it) != null }
) {
    var state: LegacySessionMigration = LegacySessionMigration.NotRequired
        private set

    fun run() {
        val unresolved = mutableListOf<String>()
        var resolvedAny = false
        for (key in SessionRecoveryKeys.ALL) {
            val scopedKey = "$name.$key"
            val value = legacyValue(scopedKey) ?: continue
            if (!SessionRecoveryKeys.carriesRecoveryIntent(key, value)) {
                // A cleared flag or blank owner carries no recovery work; it
                // must never be replayed as an intent.
                removeLegacySessionValue(scopedKey)
                continue
            }
            resolvedAny = true
            val marker = "$SESSION_MIGRATION_MARKER_PREFIX.$name.$key"
            if (defaults.boolForKey(marker)) {
                removeLegacySessionValue(scopedKey)
                continue
            }
            val exists = runCatching { keyExists(key) }.getOrNull()
            if (exists == null) {
                AppLogger.w(
                    "SessionStorage",
                    "Keychain presence for '$name/$key' was inconclusive; blocking session recovery"
                )
                unresolved += key
                continue
            }
            if (exists) {
                if (recordSessionMigration(marker)) {
                    removeLegacySessionValue(scopedKey)
                } else {
                    unresolved += key
                }
                continue
            }
            val persisted = runCatching {
                secure.putString(key, value) == StorageWriteResult.Persisted &&
                    secure.getString(key) == value
            }.getOrDefault(false)
            if (!persisted) {
                AppLogger.w("SessionStorage", "Keychain migration for '$name/$key' was not persisted")
                unresolved += key
                continue
            }
            if (recordSessionMigration(marker)) {
                removeLegacySessionValue(scopedKey)
            } else {
                unresolved += key
            }
        }
        state = when {
            unresolved.isNotEmpty() -> LegacySessionMigration.Unresolved(unresolved)
            resolvedAny -> LegacySessionMigration.Completed
            else -> LegacySessionMigration.NotRequired
        }
    }

    /**
     * Explicit session reset/logout supersedes the legacy intents. Removing
     * them is what stops a later startup from replaying the old intent; the
     * state only clears when the removal was actually persisted.
     */
    fun discard(): Boolean {
        for (key in SessionRecoveryKeys.ALL) {
            defaults.removeObjectForKey("$name.$key")
        }
        if (!defaults.synchronize()) {
            AppLogger.w("SessionStorage", "Legacy session intent cleanup for '$name' was deferred")
            return false
        }
        state = LegacySessionMigration.Completed
        return true
    }

    /** Older builds wrote strings; a bool-typed flag is accepted as well. */
    private fun legacyValue(scopedKey: String): String? = when (val raw = defaults.objectForKey(scopedKey)) {
        is String -> raw
        is NSNumber -> if (raw.boolValue) "true" else "false"
        is Boolean -> if (raw) "true" else "false"
        else -> null
    }

    private fun recordSessionMigration(marker: String): Boolean {
        defaults.setBool(true, forKey = marker)
        if (defaults.synchronize()) return true
        AppLogger.w("SessionStorage", "Keychain migration marker for '$name' was not persisted")
        return false
    }

    private fun removeLegacySessionValue(scopedKey: String) {
        defaults.removeObjectForKey(scopedKey)
        if (!defaults.synchronize()) {
            AppLogger.w("SessionStorage", "Legacy session value cleanup for '$name' was deferred")
        }
    }
}

/**
 * The session-state storage seen by the rest of the app: all reads/writes go
 * to the Keychain, while the migration outcome stays inspectable so session
 * orchestration can fail closed.
 */
private class MigratingSessionStateStorage(
    secure: SecureKeyValueStorage,
    private val migrator: SessionStateMigrator
) : SecureKeyValueStorage by secure, LegacySessionMigrationSource {
    override val legacySessionMigration: LegacySessionMigration
        get() = migrator.state

    override fun retryLegacySessionMigration() = migrator.run()

    override fun discardLegacySessionIntents(): Boolean = migrator.discard()
}

/** Convenience wrapper for tests that only need the migration outcome. */
internal fun migrateSessionStateStorage(
    name: String,
    secure: SecureKeyValueStorage,
    defaults: NSUserDefaults,
    keyExists: (String) -> Boolean? = { secure.getString(it) != null }
): LegacySessionMigration = SessionStateMigrator(name, secure, defaults, keyExists).let {
    it.run()
    it.state
}

private const val SESSION_MIGRATION_MARKER_PREFIX = "__keychain_session_migration"

/**
 * NSUserDefaults-backed key-value storage for non-sensitive settings.
 */
private class UserDefaultsStorage(
    private val name: String,
    private val defaults: NSUserDefaults
) : SecureKeyValueStorage {
    override fun getString(key: String): String? =
        defaults.stringForKey(scopedKey(key))

    override fun putString(key: String, value: String): StorageWriteResult {
        defaults.setObject(value, forKey = scopedKey(key))
        return if (defaults.synchronize()) StorageWriteResult.Persisted else StorageWriteResult.Failed
    }

    override fun remove(key: String): StorageWriteResult {
        defaults.removeObjectForKey(scopedKey(key))
        return if (defaults.synchronize()) StorageWriteResult.Persisted else StorageWriteResult.Failed
    }

    private fun scopedKey(key: String): String = "$name.$key"
}

/**
 * Keychain-backed storage for the session token / secure prefs.
 * Failures are non-fatal: reads return null, writes/removes report their
 * persistence result, and only a non-sensitive status is logged.
 */
@OptIn(ExperimentalForeignApi::class)
private class KeychainStorage(
    service: String
) : SecureKeyValueStorage {

    private val cfService: CFTypeRef? = CFBridgingRetain(service)

    init {
        migrateLegacyAccessibility()
    }

    override fun getString(key: String): String? = cfRetain(key) { cfKey ->
        val cfValue = alloc<CFTypeRefVar>()
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val status = try {
            SecItemCopyMatching(query, cfValue.ptr)
        } finally {
            CFBridgingRelease(query)
        }
        if (status == errSecItemNotFound) return@cfRetain null
        if (status != errSecSuccess) {
            AppLogger.w("Keychain", "get $key failed status=$status")
            return@cfRetain null
        }

        val data = CFBridgingRelease(cfValue.value) as? NSData ?: return@cfRetain null
        // A zero-length NSData may expose a null bytes pointer; "" is a valid
        // stored value (an explicitly cleared API token) and must round-trip.
        if (data.length == 0uL) return@cfRetain ""
        val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return@cfRetain null
        bytes.decodeToString()
    }

    override fun putString(key: String, value: String): StorageWriteResult {
        val encoded = value.encodeToByteArray()
        return encoded.usePinned { pinned ->
            val data = NSData.create(
                bytes = if (encoded.isEmpty()) null else pinned.addressOf(0),
                length = encoded.size.toULong()
            )
            cfRetain(key, data) { cfKey, cfValueData ->
                val query = cfDictionaryOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to cfService,
                    kSecAttrAccount to cfKey
                )
                val attributes = cfDictionaryOf(
                    kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                    kSecValueData to cfValueData
                )
                var status = try {
                    SecItemUpdate(query, attributes)
                } finally {
                    CFBridgingRelease(attributes)
                }
                if (status == errSecItemNotFound) {
                    val addQuery = cfDictionaryOf(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfKey,
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                        kSecValueData to cfValueData
                    )
                    status = try {
                        SecItemAdd(addQuery, null)
                    } finally {
                        CFBridgingRelease(addQuery)
                    }
                }
                CFBridgingRelease(query)
                if (status != errSecSuccess) AppLogger.w("Keychain", "put $key failed status=$status")
                if (status == errSecSuccess) StorageWriteResult.Persisted else StorageWriteResult.Failed
            }
        }
    }

    override fun remove(key: String): StorageWriteResult = cfRetain(key) { cfKey ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey
        )
        val status = try {
            SecItemDelete(query)
        } finally {
            CFBridgingRelease(query)
        }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            AppLogger.w("Keychain", "remove $key failed status=$status")
        }
        if (status == errSecSuccess || status == errSecItemNotFound) {
            StorageWriteResult.Persisted
        } else {
            StorageWriteResult.Failed
        }
    }

    override fun resetStorage(): Result<Unit> = runCatching {
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService
            )
            val status = try {
                SecItemDelete(query)
            } finally {
                CFBridgingRelease(query)
            }
            if (status != errSecSuccess && status != errSecItemNotFound) {
                AppLogger.w("Keychain", "explicit reset failed status=$status")
                error("Keychain reset failed with status $status")
            }
        }
        check(putString(RESET_PROBE_KEY, "ok") == StorageWriteResult.Persisted)
        check(getString(RESET_PROBE_KEY) == "ok")
        check(remove(RESET_PROBE_KEY) == StorageWriteResult.Persisted)
    }

    /** Null means the lookup failed; only a definitive miss allows migration. */
    internal fun containsKey(key: String): Boolean? = cfRetain(key) { cfKey ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val status = try {
            SecItemCopyMatching(query, null)
        } finally {
            CFBridgingRelease(query)
        }
        when (status) {
            errSecSuccess -> true
            errSecItemNotFound -> false
            else -> {
                AppLogger.w("Keychain", "presence check failed status=$status")
                null
            }
        }
    }

    /**
     * Rewrites old items that predate an explicit accessibility setting
     * (NSUserDefaults-era defaults were WhenUnlocked and backup-eligible).
     * The value is carried along with the updated accessibility, so the item
     * is changed in place and never deleted and re-added. Re-running this is
     * harmless: items already on ThisDeviceOnly are skipped.
     */
    private fun migrateLegacyAccessibility() {
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecReturnAttributes to kCFBooleanTrue,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitAll
            )
            val status = try {
                SecItemCopyMatching(query, result.ptr)
            } finally {
                CFBridgingRelease(query)
            }
            if (status == errSecItemNotFound) return@memScoped

            val rawResult = result.value
            try {
                if (status != errSecSuccess || rawResult == null) {
                    AppLogger.w("Keychain", "accessibility migration read failed status=$status")
                    return@memScoped
                }
                val items = rawResult.reinterpret<__CFArray>()
                var upgraded = 0
                var failed = 0
                for (index in 0L until CFArrayGetCount(items)) {
                    val item = CFArrayGetValueAtIndex(items, index)
                        ?.reinterpret<__CFDictionary>() ?: continue
                    val account = CFDictionaryGetValue(item, kSecAttrAccount) ?: continue
                    val valueData = CFDictionaryGetValue(item, kSecValueData) ?: continue
                    val accessibility = CFDictionaryGetValue(item, kSecAttrAccessible)
                        ?.reinterpret<__CFString>()
                    if (accessibility != null &&
                        CFStringCompare(
                            accessibility,
                            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                            0uL
                        ) == 0L
                    ) {
                        continue
                    }
                    val updateQuery = cfDictionaryOf(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to account
                    )
                    val attributes = cfDictionaryOf(
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                        kSecValueData to valueData
                    )
                    val updateStatus = try {
                        SecItemUpdate(updateQuery, attributes)
                    } finally {
                        CFBridgingRelease(updateQuery)
                        CFBridgingRelease(attributes)
                    }
                    when (updateStatus) {
                        errSecSuccess -> upgraded++
                        errSecItemNotFound -> Unit
                        else -> {
                            failed++
                            AppLogger.w(
                                "Keychain",
                                "accessibility migration update failed status=$updateStatus"
                            )
                        }
                    }
                }
                if (failed > 0) {
                    AppLogger.w("Keychain", "accessibility migration incomplete failures=$failed")
                } else if (upgraded > 0) {
                    AppLogger.i("Keychain", "accessibility migration upgraded=$upgraded")
                }
            } finally {
                if (rawResult != null) CFBridgingRelease(rawResult)
            }
        }
    }

    private companion object {
        const val RESET_PROBE_KEY = "__secure_storage_reset_probe"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.cfDictionaryOf(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val size = items.size
    val keys = allocArrayOf(*items.map { it.first }.toTypedArray())
    val values = allocArrayOf(*items.map { it.second }.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        size.convert(),
        null,
        null
    )
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> cfRetain(value: Any?, block: MemScope.(CFTypeRef?) -> T): T = memScoped {
    val cfValue = CFBridgingRetain(value)
    try {
        block(cfValue)
    } finally {
        CFBridgingRelease(cfValue)
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> cfRetain(value1: Any?, value2: Any?, block: MemScope.(CFTypeRef?, CFTypeRef?) -> T): T =
    memScoped {
        val cfValue1 = CFBridgingRetain(value1)
        val cfValue2 = CFBridgingRetain(value2)
        try {
            block(cfValue1, cfValue2)
        } finally {
            CFBridgingRelease(cfValue1)
            CFBridgingRelease(cfValue2)
        }
    }
