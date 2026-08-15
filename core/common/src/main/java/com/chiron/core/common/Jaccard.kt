package com.chiron.core.common

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
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        return items
            .filter { 
                val text = textSelector(it).lowercase()
                text.contains(q) 
            }
            .sortedBy { 
                val text = textSelector(it).lowercase()
                when {
                    text == q -> 0
                    text.startsWith(q) -> 1
                    else -> 2
                }
            }
            .take(limit)
    }
}
