package com.chiron.core.database.pr

import com.chiron.core.model.Exercise

/**
 * The kind of personal-record tracking an exercise supports, derived from its
 * tracking-config flags. This is the single source of truth for "what counts as
 * a PR" across the whole app.
 *
 *  - [WEIGHT_REPS]     classic: per rep-count, the heaviest weight. Gets a 1RM estimate.
 *  - [TIME_WEIGHT]     per weight, the longest duration ("most seconds at this weight").
 *  - [DISTANCE_WEIGHT] per weight, the longest distance ("furthest at this weight").
 *  - [DISTANCE_TIME]   per distance, the shortest duration ("fastest for this distance").
 *  - [NONE]            no meaningful PR model.
 *
 * Only [WEIGHT_REPS] receives a 1RM estimate — there is no defensible estimation
 * formula for the other tracks.
 */
enum class PrCategory {
    WEIGHT_REPS,
    TIME_WEIGHT,
    DISTANCE_WEIGHT,
    DISTANCE_TIME,
    NONE
}

/** Resolve the [PrCategory] for this exercise from its tracking flags. */
fun Exercise.prCategory(): PrCategory {
    val weight = isWeightBased == 1
    val reps = isRepBased == 1
    val time = isTimeBased == 1
    val distance = isDistanceBased == 1

    return when {
        distance && weight -> PrCategory.DISTANCE_WEIGHT
        distance && time -> PrCategory.DISTANCE_TIME
        time && weight -> PrCategory.TIME_WEIGHT
        weight && reps -> PrCategory.WEIGHT_REPS
        else -> PrCategory.NONE
    }
}

/** Whether higher [ExercisePr.record] values are better for this category. */
fun PrCategory.recordIsMaximized(): Boolean = this != PrCategory.DISTANCE_TIME
