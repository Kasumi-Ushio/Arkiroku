package org.kasumi321.ushio.phitracker.data.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.CharConversionException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import org.kasumi321.ushio.phitracker.data.logging.AppLogger

private const val LEGACY_MIGRATED_MARKER = "plain_storage_migrated_from_encrypted"
private const val ANDROIDX_MASTER_KEY_ALIAS = "_androidx_security_master_key_"

private fun applicationContext(): Context =
    AndroidPlatformContext.applicationContext
        ?: throw IllegalStateException(
            "AndroidPlatformContext.applicationContext not initialized. " +
                "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
        )

actual fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage =
    RecoverableSecureKeyValueStorage(name, openPrefs = { openEncryptedPrefs(applicationContext(), name) })

internal fun recoverSecureKeyValueStorage(
    name: String,
    openPrefs: () -> SharedPreferences
): SecureKeyValueStorage {
    val context = applicationContext()
    repeat(2) { attempt ->
        try {
            return EncryptedPrefsStorage(logName = name, prefs = openPrefs())
        } catch (e: Exception) {
            AppLogger.w("SecureStorage", e) { "secure storage '$name' unavailable (attempt ${attempt + 1})" }
            if (attempt == 0 && e.isProvenPreferenceCorruption()) {
                val deleted = context.deleteSharedPreferences(name)
                AppLogger.w("SecureStorage") { "discarded provably corrupt preferences '$name': $deleted" }
            }
        }
    }
    AppLogger.w("SecureStorage") { "secure storage '$name' remains unavailable; using ephemeral fallback" }
    return EphemeralKeyValueStorage()
}

private fun Throwable.isProvenPreferenceCorruption(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause is AEADBadTagException ||
            cause is CharConversionException ||
            (cause is GeneralSecurityException && cause.message?.contains("invalid keyset", ignoreCase = true) == true)
    }

private fun openEncryptedPrefs(context: Context, name: String): SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    return EncryptedSharedPreferences.create(
        name,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

actual fun createPlainKeyValueStorage(name: String): SecureKeyValueStorage =
    createPlainKeyValueStorageWithMigration(name, { openEncryptedPrefs(applicationContext(), name) })

actual fun createSessionStateStorage(name: String): SecureKeyValueStorage =
    createPlainKeyValueStorage(name)

actual fun createLegacySettingsStorage(name: String): SecureKeyValueStorage? = try {
    EncryptedPrefsStorage(logName = name, prefs = openEncryptedPrefs(applicationContext(), name))
} catch (e: Exception) {
    AppLogger.w("SecureStorage", e) { "legacy encrypted settings '$name' are unavailable; migration will retry" }
    null
}

internal fun createPlainKeyValueStorageWithMigration(
    name: String,
    openLegacyPrefs: () -> SharedPreferences,
    targetPrefs: SharedPreferences = applicationContext().getSharedPreferences("${name}_plain", Context.MODE_PRIVATE)
): SecureKeyValueStorage {
    val prefs = targetPrefs
    if (!prefs.getBoolean(LEGACY_MIGRATED_MARKER, false)) {
        migrateLegacyEncryptedSettings(legacyName = name, target = prefs, openLegacyPrefs = openLegacyPrefs)
    }
    return PlainPrefsStorage(logName = name, prefs = prefs)
}

private fun migrateLegacyEncryptedSettings(
    legacyName: String,
    target: SharedPreferences,
    openLegacyPrefs: () -> SharedPreferences
): Boolean = runCatching {
    val legacy = openLegacyPrefs()
    val editor = target.edit()
    for ((key, value) in legacy.all) {
        if (key == "api_token" || target.contains(key)) continue
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        }
    }
    editor.putBoolean(LEGACY_MIGRATED_MARKER, true)
    if (!editor.commit()) {
        target.edit().remove(LEGACY_MIGRATED_MARKER).apply()
        error("legacy settings commit failed")
    }
    true
}.getOrElse {
    AppLogger.w("PlainStorage", it) { "legacy settings migration for '$legacyName' failed; will retry" }
    false
}

internal class EncryptedPrefsStorage(
    private val logName: String,
    private val prefs: SharedPreferences
) : SecureKeyValueStorage {
    override fun getString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        AppLogger.w("SecureStorage", e) { "read failed for storage='$logName' key=$key" }
        null
    }

    override fun putString(key: String, value: String): StorageWriteResult {
        val committed = try {
            prefs.edit().putString(key, value).commit()
        } catch (e: Exception) {
            AppLogger.w("SecureStorage", e) { "write failed for storage='$logName' key=$key" }
            false
        }
        if (!committed) AppLogger.w("SecureStorage") { "write not committed for storage='$logName' key=$key" }
        return if (committed) StorageWriteResult.Persisted else StorageWriteResult.Failed
    }

    override fun remove(key: String): StorageWriteResult {
        val committed = try {
            prefs.edit().remove(key).commit()
        } catch (e: Exception) {
            AppLogger.w("SecureStorage", e) { "remove failed for storage='$logName' key=$key" }
            false
        }
        if (!committed) AppLogger.w("SecureStorage") { "remove not committed for storage='$logName' key=$key" }
        return if (committed) StorageWriteResult.Persisted else StorageWriteResult.Failed
    }
}

internal class PlainPrefsStorage(
    private val logName: String,
    private val prefs: SharedPreferences
) : SecureKeyValueStorage {
    override fun getString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        AppLogger.w("PlainStorage", e) { "read failed for storage='$logName' key=$key" }
        null
    }

    override fun putString(key: String, value: String): StorageWriteResult = try {
        if (prefs.edit().putString(key, value).commit()) {
            StorageWriteResult.Persisted
        } else {
            StorageWriteResult.Failed
        }
    } catch (e: Exception) {
        AppLogger.w("PlainStorage", e) { "write failed for storage='$logName' key=$key" }
        StorageWriteResult.Failed
    }

    override fun remove(key: String): StorageWriteResult = try {
        if (prefs.edit().remove(key).commit()) {
            StorageWriteResult.Persisted
        } else {
            StorageWriteResult.Failed
        }
    } catch (e: Exception) {
        AppLogger.w("PlainStorage", e) { "remove failed for storage='$logName' key=$key" }
        StorageWriteResult.Failed
    }
}

internal class RecoverableSecureKeyValueStorage(
    private val name: String,
    private val openPrefs: () -> SharedPreferences,
    private val resetMasterKey: () -> Unit = ::resetAndroidMasterKey
) : SecureKeyValueStorage {
    private var delegate: SecureKeyValueStorage = recoverSecureKeyValueStorage(name, openPrefs)

    override fun getString(key: String): String? = delegate.getString(key)
    override fun putString(key: String, value: String): StorageWriteResult = delegate.putString(key, value)
    override fun remove(key: String): StorageWriteResult = delegate.remove(key)

    @Synchronized
    override fun resetStorage(): Result<Unit> = runCatching {
        resetMasterKey()
        if (!applicationContext().deleteSharedPreferences(name)) {
            AppLogger.w("SecureStorage") { "explicit reset could not discard preferences '$name'; probing replacement" }
        }
        delegate = recoverSecureKeyValueStorage(name, openPrefs)
        check(delegate.putString(RESET_PROBE_KEY, "ok") == StorageWriteResult.Persisted)
        check(delegate.getString(RESET_PROBE_KEY) == "ok")
        check(delegate.remove(RESET_PROBE_KEY) == StorageWriteResult.Persisted)
    }

    private companion object {
        const val RESET_PROBE_KEY = "__secure_storage_reset_probe"
    }
}

private fun resetAndroidMasterKey() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    keyStore.deleteEntry(ANDROIDX_MASTER_KEY_ALIAS)
}

private class EphemeralKeyValueStorage : SecureKeyValueStorage {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String): StorageWriteResult {
        map[key] = value
        return StorageWriteResult.Ephemeral
    }
    override fun remove(key: String): StorageWriteResult {
        map.remove(key)
        return StorageWriteResult.Ephemeral
    }
}
