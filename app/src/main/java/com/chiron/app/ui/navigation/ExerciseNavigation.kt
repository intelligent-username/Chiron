package com.chiron.app.ui.navigation

import com.chiron.core.model.Exercise

/** Stay within search results when applicable; deep links may fall outside that search. */
internal fun adjacentExerciseIds(
    currentId: Long?,
    exercises: List<Exercise>,
    archivedExercises: List<Exercise>,
    searchResults: List<Exercise>? = null
): Pair<Long?, Long?> {
    if (currentId == null) return null to null
    val list = searchResults?.takeIf { results -> results.any { it.id == currentId } }
        ?: exercises.takeIf { active -> active.any { it.id == currentId } }
        ?: archivedExercises
    val index = list.indexOfFirst { it.id == currentId }
    if (index < 0) return null to null
    return list.getOrNull(index - 1)?.id to list.getOrNull(index + 1)?.id
}
