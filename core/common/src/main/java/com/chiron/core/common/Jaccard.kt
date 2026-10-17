package com.chiron.core.common

/**
 * Exercise name search and autocomplete ranker.
 */
object SearchRanker {

    /**
     * Rank items by similarity to a query.
     * Exact matches rank highest, followed by prefix matches, then substring matches.
     * Operates in a single pass without allocating lowercase strings or wrapper objects.
     */
    fun <T> rankBySimilarity(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        limit: Int = 10
    ): List<T> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val exactMatches = ArrayList<T>()
        val prefixMatches = ArrayList<T>()
        val containsMatches = ArrayList<T>()

        for (item in items) {
            val text = textSelector(item)
            if (text.equals(q, ignoreCase = true)) {
                exactMatches.add(item)
            } else if (text.startsWith(q, ignoreCase = true)) {
                prefixMatches.add(item)
            } else if (text.contains(q, ignoreCase = true)) {
                containsMatches.add(item)
            }
        }

        val result = ArrayList<T>(limit.coerceAtMost(exactMatches.size + prefixMatches.size + containsMatches.size))
        for (item in exactMatches) {
            if (result.size >= limit) return result
            result.add(item)
        }
        for (item in prefixMatches) {
            if (result.size >= limit) return result
            result.add(item)
        }
        for (item in containsMatches) {
            if (result.size >= limit) return result
            result.add(item)
        }
        return result
    }
}
