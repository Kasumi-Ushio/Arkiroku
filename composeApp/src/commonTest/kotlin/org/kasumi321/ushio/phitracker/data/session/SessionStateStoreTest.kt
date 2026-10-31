package org.kasumi321.ushio.phitracker.data.session

import org.kasumi321.ushio.phitracker.data.platform.FakeSecureKeyValueStorage
import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigration
import org.kasumi321.ushio.phitracker.data.platform.LegacySessionMigrationSource
import org.kasumi321.ushio.phitracker.data.platform.StorageWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStateStoreTest {
    @Test
    fun loginAndLogoutMarkersRoundTrip() {
        val storage = FakeSecureKeyValueStorage()
        val store = SessionStateStore(storage)

        store.beginLogin("owner-a").getOrThrow()
        store.setLogoutPending(true).getOrThrow()
        assertEquals("owner-a", store.pendingLoginOwner)
        assertTrue(store.isLogoutPending)

        store.clearLogin().getOrThrow()
        store.setLogoutPending(false).getOrThrow()
        assertNull(store.pendingLoginOwner)
        assertEquals(false, store.isLogoutPending)
    }

    @Test
    fun ephemeralOrFailedWritesAreNotAcceptedAsDurableIntents() {
        val ephemeral = FakeSecureKeyValueStorage().apply { writeResult = StorageWriteResult.Ephemeral }
        assertTrue(SessionStateStore(ephemeral).setLogoutPending(true).isFailure)

        val failed = FakeSecureKeyValueStorage().apply { writeResult = StorageWriteResult.Failed }
        assertTrue(SessionStateStore(failed).beginLogin("owner-a").isFailure)
    }

    @Test
    fun failedRemovalIsReportedEvenWhenMemoryLooksCleared() {
        val storage = object : FakeSecureKeyValueStorage() {
            override fun remove(key: String): StorageWriteResult {
                map.remove(key)
                return StorageWriteResult.Failed
            }
        }
        storage.map["logout_pending"] = "true"

        val result = SessionStateStore(storage).setLogoutPending(false)

        assertTrue(result.isFailure)
        assertEquals(false, SessionStateStore(storage).isLogoutPending)
    }

    @Test
    fun unresolvedLegacyRecoveryIsReportedAndCanBeDiscarded() {
        val storage = LegacyRecoveryStorage()
        val store = SessionStateStore(storage)

        assertTrue(store.hasUnresolvedLegacyRecovery)

        store.discardUnresolvedLegacyRecovery().getOrThrow()

        assertFalse(store.hasUnresolvedLegacyRecovery)
        assertTrue(storage.discarded)
    }

    @Test
    fun failedLegacyRecoveryDiscardKeepsFailingClosed() {
        val storage = LegacyRecoveryStorage(discardResult = false)
        val store = SessionStateStore(storage)

        assertTrue(store.discardUnresolvedLegacyRecovery().isFailure)
        assertTrue(store.hasUnresolvedLegacyRecovery)
    }

    @Test
    fun completedLegacyMigrationNeverBlocksSessions() {
        val storage = LegacyRecoveryStorage(migration = LegacySessionMigration.Completed)
        val store = SessionStateStore(storage)

        assertFalse(store.hasUnresolvedLegacyRecovery)
        store.discardUnresolvedLegacyRecovery().getOrThrow()
        assertFalse(storage.discarded)
    }
}

private class LegacyRecoveryStorage(
    var migration: LegacySessionMigration = LegacySessionMigration.Unresolved(listOf("logout_pending")),
    private val discardResult: Boolean = true
) : FakeSecureKeyValueStorage(), LegacySessionMigrationSource {
    var discarded = false

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
