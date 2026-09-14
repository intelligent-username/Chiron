package com.chiron.core.common

/**
 * Exercise name search and autocomplete ranker.
 */
object SearchRanker {

    /**
     * Rank items by similarity to a query.
     * Exact matches rank highest, followed by prefix matches, then substring matches.
     */
    fun <T> rankBySimilarity(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        limit: Int = 10
    ): List<T> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        class ScoredItem(val item: T, val score: Int)

        val matches = ArrayList<ScoredItem>(items.size.coerceAtMost(limit * 2))
        for (item in items) {
            val text = textSelector(item).lowercase()
            val score = when {
                text == q -> 0
                text.startsWith(q) -> 1
                text.contains(q) -> 2
                else -> -1
            }
            if (score != -1) {
                matches.add(ScoredItem(item, score))
            }
        }

        matches.sortBy { it.score }
        val resultSize = matches.size.coerceAtMost(limit)
        val result = ArrayList<T>(resultSize)
        for (i in 0 until resultSize) {
            result.add(matches[i].item)
        }
        return result
    }
}

/**
 * Legacy alias for [SearchRanker].
 */
object Jaccard {
    fun <T> rankBySimilarity(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        limit: Int = 10
    ): List<T> = SearchRanker.rankBySimilarity(query, items, textSelector, limit)
}
