package com.chiron.app.ui.history

import com.chiron.app.data.entities.ExerciseEntry

/**
 * Groups a flat list of [ExerciseEntry] records into logical display groups.
 *
 * Each returned sub-list is either:
 *  - A single-element list (standalone exercise), or
 *  - A multi-element list representing a complete superset
 *    (SUPERSET_START → SUPERSET_MIDDLE* → SUPERSET_END).
 *
 * Incomplete supersets (SUPERSET_START with no following members) are emitted
 * as single-element groups so they render as regular exercises.
 */
fun groupExercisesBySuperset(
    entries: List<ExerciseEntry>
): List<List<ExerciseEntry>> {
    val groups = mutableListOf<List<ExerciseEntry>>()
    var currentGroup = mutableListOf<ExerciseEntry>()
    var isBuildingSuperset = false

    for (entry in entries) {
        when (entry.sequenceType) {
            "SUPERSET_START" -> {
                if (currentGroup.isNotEmpty()) {
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                }
                currentGroup.add(entry)
                isBuildingSuperset = true
            }
            "SUPERSET_MIDDLE", "SUPERSET_END" -> {
                currentGroup.add(entry)
                if (entry.sequenceType == "SUPERSET_END") {
                    isBuildingSuperset = false
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                }
            }
            else -> {
                if (currentGroup.isNotEmpty()) {
                    // Single SUPERSET_START with no followers → treat as regular exercise
                    groups.add(currentGroup.toList())
                    currentGroup = mutableListOf()
                    isBuildingSuperset = false
                }
                groups.add(listOf(entry))
            }
        }
    }

    // Flush any trailing incomplete superset group
    if (currentGroup.isNotEmpty()) {
        groups.add(currentGroup.toList())
    }

    return groups
}
