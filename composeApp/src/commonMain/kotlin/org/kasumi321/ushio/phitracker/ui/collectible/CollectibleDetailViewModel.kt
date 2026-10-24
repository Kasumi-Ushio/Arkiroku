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
import org.kasumi321.ushio.phitracker.domain.model.CollectibleChapter
import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem
import org.kasumi321.ushio.phitracker.domain.model.CollectibleLanguage
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository

data class CollectibleDetailUiState(
    val isLoading: Boolean = true,
    val item: CollectibleItem? = null,
    val chapter: CollectibleChapter? = null,
    /** Local HD-or-thumbnail cover URL for [CollectibleChapter.coverSongId]; null when uncached. */
    val coverUrl: String? = null
)

/**
 * Loads one collectible item (all of its parts) plus its chapter for the
 * cover song id. Kept separate from the list screen's view model per the
 * viewmodel-no-peer-viewmodel boundary; the list and detail screens share
 * data only through CollectibleDataProvider.
 */
class CollectibleDetailViewModel(
    private val folderKey: String,
    private val key: String,
    private val collectibleDataProvider: CollectibleDataProvider,
    private val illustrationUriResolver: IllustrationUriResolver,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(CollectibleDetailUiState())
    val uiState: StateFlow<CollectibleDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val appLanguage = settingsRepository.appLanguage.first()
                val language = CollectibleLanguage.fromAppLanguage(appLanguage, ::currentLanguageTag)
                val item = collectibleDataProvider.getItem(folderKey, key, language)
                val chapter = collectibleDataProvider.getChapters(language)
                    .firstOrNull { it.folderKey == folderKey }
                val coverUrl = chapter?.coverSongId?.let { illustrationUriResolver.bestLocalUri(it) }
                mutableUiState.update {
                    it.copy(isLoading = false, item = item, chapter = chapter, coverUrl = coverUrl)
                }
            } catch (e: Exception) {
                AppLogger.event(
                    "data",
                    "collectible_detail_load_failed",
                    mapOf("folder" to folderKey, "key" to key, "error" to (e.message ?: "unknown")),
                    throwable = e
                )
                mutableUiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
