package org.kasumi321.ushio.phitracker.data.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.repository.SettingsRepositoryImpl
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SecureKeyValueStorageAndroidTest {
    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        AndroidPlatformContext.initialize(ApplicationProvider.getApplicationContext())
        context = AndroidPlatformContext.applicationContext!!
    }

    @Test
    fun preferenceCommitResultsAreNotReportedAsPersistentWhenTheyFail() {
        val delegate = context.getSharedPreferences("commit-failure-${System.nanoTime()}", Context.MODE_PRIVATE)
        val storage = EncryptedPrefsStorage("test", CommitFailingPreferences(delegate))

        assertEquals(StorageWriteResult.Failed, storage.putString("key", "value"))
        assertEquals(StorageWriteResult.Failed, storage.remove("key"))
    }

    @Test
    fun secureStorageRetriesWithoutDeletingPreferencesAfterTransientFailure() {
        val name = "secure-recovery-${System.nanoTime()}"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("key", "old").commit()
        var attempts = 0
        val storage = recoverSecureKeyValueStorage(name) {
            attempts++
            if (attempts == 1) error("injected transient provider failure")
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        assertEquals(2, attempts)
        assertEquals("old", context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("key", null))
        assertEquals(StorageWriteResult.Persisted, storage.putString("key", "new"))
        assertEquals("new", storage.getString("key"))
    }

    @Test
    fun persistentConstructionFailureIsExplicitlyEphemeralAndPreservesPreferences() {
        val name = "secure-failure-${System.nanoTime()}"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("key", "old").commit()
        val storage = recoverSecureKeyValueStorage(name) {
            error("keystore unavailable")
        }

        assertEquals(StorageWriteResult.Ephemeral, storage.putString("key", "value"))
        assertEquals("old", context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("key", null))
    }

    @Test
    fun provenAuthenticationCorruptionIsDiscardedBeforeRetry() {
        val name = "secure-corrupt-${System.nanoTime()}"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("key", "unreadable").commit()
        var attempts = 0
        val storage = recoverSecureKeyValueStorage(name) {
            attempts++
            if (attempts == 1) {
                throw java.security.GeneralSecurityException(
                    "decryption failed",
                    javax.crypto.AEADBadTagException("tag mismatch")
                )
            }
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        assertEquals(2, attempts)
        assertNull(storage.getString("key"))
        assertEquals(StorageWriteResult.Persisted, storage.putString("key", "new"))
    }

    @Test
    fun explicitSecureStorageResetRecoversPersistentWrites() {
        val name = "secure-reset-${System.nanoTime()}"
        var available = false
        val storage = RecoverableSecureKeyValueStorage(
            name = name,
            openPrefs = {
                if (!available) error("permanent keystore failure")
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
            },
            resetMasterKey = {}
        )
        assertEquals(StorageWriteResult.Ephemeral, storage.putString("key", "value"))

        available = true
        storage.resetStorage().getOrThrow()

        assertEquals(StorageWriteResult.Persisted, storage.putString("key", "new"))
        assertEquals("new", storage.getString("key"))
    }

    @Test
    fun plainMigrationSkipsApiTokenAndMarksCompletion() {
        val name = "legacy-settings-${System.nanoTime()}"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        legacy.edit().putString("api_token", "secret").putString("theme_mode", "2").commit()

        val plain = createPlainKeyValueStorageWithMigration(name, { legacy })

        assertEquals("2", plain.getString("theme_mode"))
        assertNull(plain.getString("api_token"))
        val marker = context.getSharedPreferences("${name}_plain", Context.MODE_PRIVATE)
        assertEquals(true, marker.getBoolean("plain_storage_migrated_from_encrypted", false))
    }

    @Test
    fun failedPlainMigrationDoesNotWriteMarkerAndRetriesNextCreation() {
        val name = "legacy-retry-${System.nanoTime()}"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        legacy.edit().putString("theme_mode", "3").commit()
        var attempts = 0
        val openLegacy: () -> SharedPreferences = {
            attempts++
            if (attempts == 1) error("transient read failure") else legacy
        }

        createPlainKeyValueStorageWithMigration(name, openLegacy)
        val marker = context.getSharedPreferences("${name}_plain", Context.MODE_PRIVATE)
        assertEquals(false, marker.getBoolean("plain_storage_migrated_from_encrypted", false))

        val migrated = createPlainKeyValueStorageWithMigration(name, openLegacy)
        assertEquals("3", migrated.getString("theme_mode"))
        assertEquals(true, marker.getBoolean("plain_storage_migrated_from_encrypted", false))
    }

    @Test
    fun retryAfterUserEditKeepsTheNewerTargetValue() {
        val name = "legacy-user-edit-${System.nanoTime()}"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        legacy.edit().putString("theme_mode", "3").commit()
        var attempts = 0
        val openLegacy: () -> SharedPreferences = {
            attempts++
            if (attempts == 1) error("transient read failure") else legacy
        }

        val first = createPlainKeyValueStorageWithMigration(name, openLegacy)
        first.putString("theme_mode", "1")
        val migrated = createPlainKeyValueStorageWithMigration(name, openLegacy)

        assertEquals("1", migrated.getString("theme_mode"))
    }

    @Test
    fun markerCommitFailureNeverOverwritesNewerTargetValues() {
        val name = "legacy-marker-failure-${System.nanoTime()}"
        val delegate = context.getSharedPreferences("${name}_plain", Context.MODE_PRIVATE)
        val target = CommitFailingPreferences(delegate)
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        legacy.edit().putString("theme_mode", "3").commit()

        createPlainKeyValueStorageWithMigration(name, { legacy }, target)
        delegate.edit().putString("theme_mode", "1").commit()
        val migrated = createPlainKeyValueStorageWithMigration(name, { legacy }, target)

        assertEquals("1", migrated.getString("theme_mode"))
        assertEquals(false, delegate.getBoolean("plain_storage_migrated_from_encrypted", false))
    }

    @Test
    fun productionStorageCompositionMigratesApiTokenWithoutPlaintext() = runTest {
        val name = "release-structure-${System.nanoTime()}"
        val legacyPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val securePrefs = context.getSharedPreferences("secure-$name", Context.MODE_PRIVATE)
        val legacySecure = EncryptedPrefsStorage(name, legacyPrefs)
        assertEquals(StorageWriteResult.Persisted, legacySecure.putString("api_token", "legacy-token"))
        assertEquals(StorageWriteResult.Persisted, legacySecure.putString("theme_mode", "2"))
        val secure = EncryptedPrefsStorage("secure-$name", securePrefs)
        val plain = createPlainKeyValueStorageWithMigration(name, { legacyPrefs })

        val repository = SettingsRepositoryImpl(
            storage = plain,
            preloadStorage = FakeSecureKeyValueStorage(),
            secureStorage = secure,
            legacySecureStorage = legacySecure
        )

        assertEquals("2", plain.getString("theme_mode"))
        assertEquals("legacy-token", secure.getString("api_token"))
        assertNull(legacySecure.getString("api_token"))
        assertNull(plain.getString("api_token"))
        assertEquals("legacy-token", repository.apiToken.first())
    }

    private class CommitFailingPreferences(
        private val delegate: SharedPreferences
    ) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }

                override fun remove(key: String?): SharedPreferences.Editor {
                    editor.remove(key)
                    return this
                }

                override fun commit(): Boolean = false
            }
        }
    }
}
