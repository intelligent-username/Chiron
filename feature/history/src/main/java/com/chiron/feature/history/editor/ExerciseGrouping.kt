package com.chiron.feature.history

import com.chiron.core.model.ExerciseEntry

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
    val linkedGroups = entries.filter { it.groupId != null }.groupBy { it.groupId }
    val emittedIds = mutableSetOf<Long>()
    val orderedEntries = buildList {
        entries.forEach { entry ->
            if (entry.id in emittedIds) return@forEach
            val linked = linkedGroups[entry.groupId]
            if (linked != null && linked.any { it.sequenceType == "SUPERSET_START" }) {
                val sorted = linked.sortedWith(
                    compareBy<ExerciseEntry> { it.sequenceType != "SUPERSET_START" }
                        .thenBy { it.slotIndex }
                )
                addAll(sorted)
                emittedIds.addAll(sorted.map { it.id })
            } else {
                add(entry)
                emittedIds.add(entry.id)
            }
        }
    }
    val groups = mutableListOf<List<ExerciseEntry>>()
    var currentGroup = mutableListOf<ExerciseEntry>()
    var isBuildingSuperset = false

    for (entry in orderedEntries) {
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
