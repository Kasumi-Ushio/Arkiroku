package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.SongInfo

/**
 * Supports wildcard search (see [WildcardQueryMatcher] for the semantics):
 * - `*` matches one or more non-space characters
 * - `?` matches exactly one non-space character
 * - spaces match one or more whitespace characters
 * - falls back to contains match when no wildcards
 */
class SearchSongUseCase {

    operator fun invoke(
        query: String,
        allSongs: Map<String, SongInfo>
    ): List<SongInfo> {
        if (query.isBlank()) return allSongs.values.toList()

        val trimmed = query.trim()

        // Beta5 guard: queries containing consecutive 2+ '*' (e.g. "**", "***")
        // return all songs immediately to avoid pathological regex expansion
        // from \S+\S+ patterns.
        if (WildcardQueryMatcher.isFullResultQuery(trimmed)) {
            return allSongs.values.sortedBy { it.name }
        }

        return if (WildcardQueryMatcher.hasWildcard(trimmed)) {
            val regex = WildcardQueryMatcher.buildRegex(trimmed) ?: return emptyList()
            allSongs.values.filter { song ->
                regex.containsMatchIn(song.name) ||
                regex.containsMatchIn(song.id) ||
                regex.containsMatchIn(song.composer) ||
                song.nicknames.any { regex.containsMatchIn(it) }
            }.sortedBy { it.name }
        } else {
            val lowerQuery = trimmed.lowercase()
            allSongs.values.filter { song ->
                song.name.lowercase().contains(lowerQuery) ||
                song.id.lowercase().contains(lowerQuery) ||
                song.composer.lowercase().contains(lowerQuery) ||
                song.nicknames.any { it.lowercase().contains(lowerQuery) }
            }.sortedBy { it.name }
        }
    }
}
