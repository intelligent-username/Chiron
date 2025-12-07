package com.chiron.app.util

/**
 * Jaccard similarity for exercise name autocomplete.
 * Tokenizes strings and computes set-based similarity.
 */
object Jaccard {

    /**
     * Tokenize a string into lowercase words.
     */
    fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Compute Jaccard similarity between two token sets.
     * Returns value in [0, 1].
     */
    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val intersection = a.intersect(b).size
        val union = a.union(b).size

        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Compute Jaccard similarity between two strings.
     */
    fun similarity(a: String, b: String): Double {
        return similarity(tokenize(a), tokenize(b))
    }

    /**
     * Rank items by Jaccard similarity to a query.
     * Returns top N items with similarity > 0.
     */
    fun <T> rankBySimilarity(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        limit: Int = 10
    ): List<T> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        return items
            .map { item -> item to similarity(queryTokens, tokenize(textSelector(item))) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
