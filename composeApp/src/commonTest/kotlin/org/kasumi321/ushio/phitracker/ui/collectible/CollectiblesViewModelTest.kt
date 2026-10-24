package org.kasumi321.ushio.phitracker.ui.collectible

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.domain.usecase.SearchCollectibleUseCase
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import org.kasumi321.ushio.phitracker.ui.settings.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectiblesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun coversResolveFromLocalCacheOnlyUsingSongDataSlots() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setAppLanguage("en")
        val cache = RecordingCoverCache(
            thumbnails = mapOf("Dlyrotz.Likey.0" to "/local/thumb.png"),
            standards = mapOf("Dlyrotz.Likey.0" to "/local/standard.png")
        )
        val viewModel = createViewModel(settings, cache)
        advanceUntilIdle()

        assertEquals("/local/thumb.png", viewModel.getCoverUrl("Dlyrotz.Likey"))
        assertEquals("/local/standard.png", viewModel.getPosterCoverUrl("Dlyrotz.Likey"))
        assertTrue(cache.thumbnailLookups.all { it.endsWith(".0") }, "clean cover ids must map to song-data slots")
        assertTrue(cache.standardLookups.all { it.endsWith(".0") })
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun posterFallsBackToThumbnailAndUncachedCoversStayBlank() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setAppLanguage("en")
        val cache = RecordingCoverCache(
            thumbnails = mapOf("LeaveAllBehind.rider.0" to "/local/thumb.png")
        )
        val viewModel = createViewModel(settings, cache)
        advanceUntilIdle()

        assertEquals("/local/thumb.png", viewModel.getCoverUrl("LeaveAllBehind.rider"))
        assertEquals("/local/thumb.png", viewModel.getPosterCoverUrl("LeaveAllBehind.rider"))
        assertNull(viewModel.getCoverUrl("Unknown.Song"))
        assertNull(viewModel.getPosterCoverUrl("Unknown.Song"))
        assertEquals(0, cache.downloadCalls)
    }

    private fun createViewModel(
        settings: FakeSettingsRepository,
        cache: StandardArtworkCache
    ): CollectiblesViewModel = viewModelLifecycle.track(
        CollectiblesViewModel(
            collectibleDataProvider = CollectibleDataProvider(CoverAssets),
            searchCollectibleUseCase = SearchCollectibleUseCase(),
            settingsRepository = settings,
            illustrationUriResolver = IllustrationUriResolver(cache, IllustrationProvider())
        )
    )

    private class RecordingCoverCache(
        private val thumbnails: Map<String, String> = emptyMap(),
        private val standards: Map<String, String> = emptyMap()
    ) : StandardArtworkCache {
        var downloadCalls = 0
            private set
        val thumbnailLookups = mutableListOf<String>()
        val standardLookups = mutableListOf<String>()

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            downloadCalls++
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? {
            thumbnailLookups += songId
            return thumbnails[songId]
        }

        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloadCalls++
            return url
        }

        override fun getStandardIfPresent(songId: String): String? {
            standardLookups += songId
            return standards[songId]
        }

        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private object CoverAssets : TextAssetReader {
        private const val HEADER =
            "folder,folder_sub,cover,key,sub_index,name,date,supervisor,category,content,properties"

        override fun readText(name: String): String = when (name) {
            "collection_zh_cn.csv", "collection_en.csv" ->
                HEADER + "\n冰封世界,第一章,cover.jpg,item1,1,Item,2026/01/01,Sup,main,Content,"
            "collection_version.txt" -> "1.0"
            else -> error("Unexpected asset: $name")
        }
    }
}
