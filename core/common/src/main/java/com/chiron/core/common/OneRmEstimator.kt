package com.chiron.core.common

/**
 * 1RM estimation using Epley formula.
 * 1RM = weight * (1 + reps/30). For 1 rep, 1RM = weight.
 */
object OneRmEstimator {

    /**
     * Estimate 1RM from a weight and rep count using Epley formula.
     */
    fun estimate1Rm(weight: Double, reps: Int): Double {
        if (reps <= 1) return weight
        return weight * (1.0 + reps / 30.0)
    }
}
