package org.kasumi321.ushio.phitracker.ui.collectible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.collectible.CollectibleDataProvider
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.currentLanguageTag
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectibleChapter
import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem
import org.kasumi321.ushio.phitracker.domain.model.CollectibleLanguage
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SearchCollectibleUseCase

data class CollectiblesUiState(
    val searchQuery: String = "",
    val allItems: List<CollectibleItem> = emptyList(),
    val filteredItems: List<CollectibleItem> = emptyList(),
    /** True until the first data load settles; keeps a restored drill-down open. */
    val isLoading: Boolean = true,
    /** Display-order chapters; selection sets hold folderKeys (zh_cn names). */
    val chapters: List<CollectibleChapter> = emptyList(),
    val selectedChapters: Set<String> = emptySet(),
    val selectedCategories: Set<CollectibleCategory> = emptySet(),
    val showFilterSheet: Boolean = false
)

/**
 * True once the search box holds text or any filter is active: the sub-tab
 * then shows the flat results list instead of the chapter poster grid, so
 * the header's collapse-on-scroll logic needs this same predicate (see
 * MainScreen) to pick the scroll state that is actually driving the list.
 */
internal fun CollectiblesUiState.isBrowsingActive(): Boolean =
    searchQuery.isNotBlank() || selectedChapters.isNotEmpty() || selectedCategories.isNotEmpty()

/**
 * State holder for the collectibles sub-tab of the Index page. Deliberately
 * separate from the home screen's state holder (per the
 * viewmodel-no-peer-viewmodel boundary); mirrors its search/filter patterns
 * for the collectible index instead. Updates are not owned here: both
 * datasets update through the combined song-data update flow, and this
 * screen reloads by observing CollectibleDataProvider's cache
 * invalidations.
 */
class CollectiblesViewModel(
    private val collectibleDataProvider: CollectibleDataProvider,
    private val searchCollectibleUseCase: SearchCollectibleUseCase,
    private val settingsRepository: SettingsRepository,
    private val illustrationUriResolver: IllustrationUriResolver
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(CollectiblesUiState())
    val uiState: StateFlow<CollectiblesUiState> = mutableUiState.asStateFlow()

    // Resolved once per ViewModel instance: the app recreates activities on an
    // in-app language change, so a cached value is refreshed with the new
    // locale (same assumption as TipsProvider's read-on-every-call guard).
    private var appLanguageCached: String = ""

    init {
        viewModelScope.launch {
            appLanguageCached = settingsRepository.appLanguage.first()
            loadCollectibles()
        }
        // A combined song+collection update finishes by invalidating the
        // provider's cache; that emission is the reload signal.
        viewModelScope.launch {
            collectibleDataProvider.cacheInvalidations.collect {
                loadCollectibles()
            }
        }
    }

    fun search(query: String) {
        mutableUiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleChapter(folderKey: String) {
        mutableUiState.update { state ->
            val newChapters = state.selectedChapters.toMutableSet().apply {
                if (contains(folderKey)) remove(folderKey) else add(folderKey)
            }
            state.copy(selectedChapters = newChapters)
        }
        applyFilters()
    }

    fun toggleCategory(category: CollectibleCategory) {
        mutableUiState.update { state ->
            val newCategories = state.selectedCategories.toMutableSet().apply {
                if (contains(category)) remove(category) else add(category)
            }
            state.copy(selectedCategories = newCategories)
        }
        applyFilters()
    }

    fun toggleFilterSheet(show: Boolean) {
        mutableUiState.update { it.copy(showFilterSheet = show) }
    }

    fun resetFilters() {
        mutableUiState.update { it.copy(selectedChapters = emptySet(), selectedCategories = emptySet()) }
        applyFilters()
    }

    /**
     * Chapter cover thumbnail for [coverSongId], read from the local preload
     * cache only. A missing copy stays blank instead of triggering an
     * on-demand download, matching the no-network policy for index artwork
     * (declined preloads therefore behave exactly the same).
     */
    fun getCoverUrl(coverSongId: String): String? =
        illustrationUriResolver.lowLocalUri(coverSongId)

    /**
     * Poster art for the chapter grid and chapter header: prefers the locally
     * cached HD slot and falls back to the preloaded thumbnail. Never returns
     * a remote URL, so an uncached chapter renders the placeholder colour.
     */
    fun getPosterCoverUrl(coverSongId: String): String? =
        illustrationUriResolver.bestLocalUri(coverSongId)

    private fun loadCollectibles() {
        try {
            val language = CollectibleLanguage.fromAppLanguage(appLanguageCached, ::currentLanguageTag)
            val chapters = collectibleDataProvider.getChapters(language)
            val items = collectibleDataProvider.getItems(language)
            mutableUiState.update {
                it.copy(isLoading = false, chapters = chapters, allItems = items)
            }
            applyFilters()
            AppLogger.event("data", "collectible_data_loaded", mapOf("count" to items.size.toString()))
        } catch (e: Exception) {
            mutableUiState.update { it.copy(isLoading = false) }
            AppLogger.event(
                "data",
                "collectible_data_load_failed",
                mapOf("error" to (e.message ?: "unknown")),
                throwable = e
            )
        }
    }

    private fun applyFilters() {
        val state = mutableUiState.value
        val searchResults = if (state.searchQuery.isNotBlank()) {
            searchCollectibleUseCase(state.searchQuery, state.allItems)
        } else {
            state.allItems
        }
        val chapters = state.selectedChapters
        val categories = state.selectedCategories
        val filtered = searchResults.filter { item ->
            val matchesChapter = chapters.isEmpty() || item.chapterFolderKey in chapters
            val matchesCategory = categories.isEmpty() ||
                item.parts.any { it.category in categories }
            matchesChapter && matchesCategory
        }
        mutableUiState.update { it.copy(filteredItems = filtered) }
    }
}
