package org.kasumi321.ushio.phitracker.data.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

class IllustrationUriResolverTest {
    private val perDifficultySong = IllustrationProvider.PER_DIFFICULTY_SONGS.keys.single()

    @Test
    fun perDifficultySongResolvesDifficultyUrlAndCacheSlot() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("$perDifficultySong.0", Difficulty.IN)

        assertEquals(
            "https://example.test/illLow/IN/$perDifficultySong.png",
            resolved
        )
        assertEquals(listOf("$perDifficultySong.0_IN"), cache.thumbnailLookups)
    }

    @Test
    fun perDifficultySongUsesDistinctCacheSlotPerDifficulty() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        resolver.standardUri(perDifficultySong, Difficulty.EZ)
        resolver.standardUri(perDifficultySong, Difficulty.AT)

        assertEquals(
            listOf("$perDifficultySong.0_EZ", "$perDifficultySong.0_AT"),
            cache.standardLookups
        )
    }

    @Test
    fun perDifficultySongWithoutDifficultyUsesHighestVariantCacheSlot() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowUri(perDifficultySong)

        assertEquals("https://example.test/illLow/AT/$perDifficultySong.png", resolved)
        assertEquals(listOf("$perDifficultySong.0_AT"), cache.thumbnailLookups)
    }

    @Test
    fun unknownSongIgnoresDifficultyForUrlAndCacheSlot() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("song.0", Difficulty.HD)

        assertEquals("https://example.test/illLow/song.png", resolved)
        assertEquals(listOf("song.0"), cache.thumbnailLookups)
    }

    @Test
    fun lowLocalUriUsesPerDifficultyCacheSlot() {
        val cache = RecordingArtworkCache(thumbnailUri = "/local/per-difficulty.png")
        val resolver = resolver(cache)

        val resolved = resolver.lowLocalUri(perDifficultySong, Difficulty.IN)

        assertEquals("/local/per-difficulty.png", resolved)
        assertEquals(listOf("${perDifficultySong}.0_IN"), cache.thumbnailLookups)
    }

    @Test
    fun cleanIllustrationIdNormalizesToTheSongDataCacheSlot() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        resolver.lowLocalUri("song")
        resolver.lowLocalUri("song.0")

        assertEquals(listOf("song.0", "song.0"), cache.thumbnailLookups)
    }

    @Test
    fun standardLocalUriUsesCachedStandardOnly() {
        val cache = RecordingArtworkCache(standardUri = "/local/standard.png")

        val resolved = resolver(cache).standardLocalUri("song.0")

        assertEquals("/local/standard.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun standardLocalUriReturnsNullWithoutRemoteFallback() {
        val cache = RecordingArtworkCache()

        val resolved = resolver(cache).standardLocalUri("song.0")

        assertNull(resolved, "local-only lookup must never fall back to the remote URL")
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun bestLocalUriPrefersStandardThenFallsBackToThumbnail() {
        val both = RecordingArtworkCache(
            thumbnailUri = "/local/thumbnail.png",
            standardUri = "/local/standard.png"
        )
        assertEquals("/local/standard.png", resolver(both).bestLocalUri("song.0"))
        assertEquals(listOf("song.0"), both.standardLookups)
        assertEquals(emptyList<String>(), both.thumbnailLookups)

        val thumbnailOnly = RecordingArtworkCache(thumbnailUri = "/local/thumbnail.png")
        assertEquals("/local/thumbnail.png", resolver(thumbnailOnly).bestLocalUri("song.0"))
        assertEquals(listOf("song.0"), thumbnailOnly.standardLookups)
        assertEquals(listOf("song.0"), thumbnailOnly.thumbnailLookups)
    }

    @Test
    fun bestLocalUriReturnsNullWhenNothingIsCached() {
        val cache = RecordingArtworkCache()

        assertNull(resolver(cache).bestLocalUri("song.0"))
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun lowUriUsesLocalThumbnailBeforeRemoteFallback() {
        val cache = RecordingArtworkCache(thumbnailUri = "/local/thumbnail.png")
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("song.0")

        assertEquals("/local/thumbnail.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun lowUriFallsBackToRemoteLowWhenThumbnailMissing() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("song.0")

        assertEquals("https://example.test/illLow/song.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun standardUriUsesLocalStandardBeforeRemoteFallback() {
        val cache = RecordingArtworkCache(standardUri = "/local/standard.png")
        val resolver = resolver(cache)

        val resolved = resolver.standardUri("song.0")

        assertEquals("/local/standard.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun standardUriFallsBackToRemoteStandardWhenArtworkMissing() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.standardUri("song.0")

        assertEquals("https://example.test/ill/song.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun lowLocalUriReturnsLocalThumbnailWhenCached() {
        val cache = RecordingArtworkCache(thumbnailUri = "/local/thumbnail.png")
        val resolver = resolver(cache)

        val resolved = resolver.lowLocalUri("song.0")

        assertEquals("/local/thumbnail.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun lowLocalUriReturnsNullWithoutRemoteFallbackWhenThumbnailMissing() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowLocalUri("song.0")

        assertNull(resolved, "local-only lookup must never fall back to the remote URL")
        assertEquals(0, cache.downloadCalls)
    }

    private fun resolver(cache: StandardArtworkCache): IllustrationUriResolver {
        val provider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        return IllustrationUriResolver(cache, provider)
    }

    private class RecordingArtworkCache(
        private val thumbnailUri: String? = null,
        private val standardUri: String? = null
    ) : StandardArtworkCache {
        var downloadCalls = 0
            private set
        val thumbnailLookups = mutableListOf<String>()
        val standardLookups = mutableListOf<String>()

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            downloadCalls += 1
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? {
            thumbnailLookups += songId
            return thumbnailUri
        }

        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloadCalls += 1
            return url
        }

        override fun getStandardIfPresent(songId: String): String? {
            standardLookups += songId
            return standardUri
        }

        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }
}
