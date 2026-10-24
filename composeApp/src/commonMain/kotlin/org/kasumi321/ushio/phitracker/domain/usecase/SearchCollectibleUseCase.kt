package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem

/**
 * Wildcard search over collectible items (semantics shared with
 * [SearchSongUseCase] via [WildcardQueryMatcher]). Searchable fields are the
 * localized item name (every part's name — zh_cn field-level fallback is
 * already merged by CollectibleDataProvider), the item key, and the
 * supervisor of any part. Results sort by [CollectibleItem.displayName].
 */
class SearchCollectibleUseCase {

    operator fun invoke(
        query: String,
        items: List<CollectibleItem>
    ): List<CollectibleItem> {
        if (query.isBlank()) return items

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items

        if (WildcardQueryMatcher.isFullResultQuery(trimmed)) {
            return items.sortedBy { it.displayName }
        }

        return if (WildcardQueryMatcher.hasWildcard(trimmed)) {
            val regex = WildcardQueryMatcher.buildRegex(trimmed) ?: return emptyList()
            items.filter { item -> item.matches { field -> regex.containsMatchIn(field) } }
                .sortedBy { it.displayName }
        } else {
            val lowerQuery = trimmed.lowercase()
            items.filter { item -> item.matches { field -> field.lowercase().contains(lowerQuery) } }
                .sortedBy { it.displayName }
        }
    }

    private inline fun CollectibleItem.matches(fieldHit: (String) -> Boolean): Boolean =
        fieldHit(key) || parts.any { fieldHit(it.name) || fieldHit(it.supervisor) }
}
