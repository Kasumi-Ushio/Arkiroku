package org.kasumi321.ushio.phitracker.domain.usecase

/**
 * Shared wildcard-query semantics for search use cases:
 * - `*` matches one or more non-space characters
 * - `?` matches exactly one non-space character
 * - spaces match one or more whitespace characters
 * - falls back to contains match when no wildcards
 *
 * [isFullResultQuery] is the Beta5 guard: queries containing consecutive
 * 2+ '*' (e.g. "**", "***") must return the full result set immediately to
 * avoid pathological regex expansion from \S+\S+ patterns.
 */
internal object WildcardQueryMatcher {

    private val consecutiveAsterisks = Regex("""\*{2,}""")

    fun isFullResultQuery(query: String): Boolean = consecutiveAsterisks.containsMatchIn(query)

    fun hasWildcard(query: String): Boolean = query.contains('*') || query.contains('?')

    /**
     * Convert wildcard query to regex (partial match):
     * - `*` -> `\S+` (one or more non-whitespace chars)
     * - `?` -> `\S` (exactly one non-whitespace char)
     * - space -> `\s+` (one or more whitespace chars)
     * - other chars escaped
     *
     * Returns null if the pattern cannot be compiled.
     */
    fun buildRegex(query: String): Regex? {
        val sb = StringBuilder()
        for (ch in query) {
            when (ch) {
                '*' -> sb.append("\\S+")
                '?' -> sb.append("\\S")
                ' ' -> sb.append("\\s+")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        return try {
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            null
        }
    }
}
