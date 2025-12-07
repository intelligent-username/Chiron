package com.chiron.app.util

import kotlin.math.roundToInt

/**
 * 1RM estimation and rep suggestion using Epley formula.
 * 1RM = weight * (1 + reps/30)
 */
object OneRmEstimator {

    /**
     * Estimate 1RM from a weight and rep count using Epley formula.
     */
    fun estimate1Rm(weight: Double, reps: Int): Double {
        if (reps <= 0) return weight
        return weight * (1 + reps / 30.0)
    }

    /**
     * Estimate reps from a target weight and known 1RM (inverted Epley).
     * Clamped to 1..30.
     */
    fun estimateReps(weight: Double, oneRm: Double): Int {
        if (weight <= 0 || oneRm <= 0) return 1
        if (weight >= oneRm) return 1

        val reps = ((oneRm / weight) - 1) * 30
        return reps.roundToInt().coerceIn(1, 30)
    }

    /**
     * Suggest reps for a given weight based on historical max 1RM.
     */
    fun suggestReps(weight: Double, historicalSets: List<Pair<Double, Int>>): Int? {
        if (historicalSets.isEmpty() || weight <= 0) return null

        val max1Rm = historicalSets
            .filter { it.first > 0 && it.second > 0 }
            .maxOfOrNull { estimate1Rm(it.first, it.second) }
            ?: return null

        return estimateReps(weight, max1Rm)
    }

    /**
     * Suggest weight for a given rep count based on historical max 1RM.
     */
    fun suggestWeight(reps: Int, historicalSets: List<Pair<Double, Int>>): Double? {
        if (historicalSets.isEmpty() || reps <= 0) return null

        val max1Rm = historicalSets
            .filter { it.first > 0 && it.second > 0 }
            .maxOfOrNull { estimate1Rm(it.first, it.second) }
            ?: return null

        // weight = 1RM / (1 + reps/30)
        return max1Rm / (1 + reps / 30.0)
    }
}
