package com.chiron.app.util

import kotlin.math.roundToInt

/**
 * Conversion utilities for weight units.
 * DB stores weights in pounds; UI may display kg.
 */
object UnitConversion {
    const val LBS_PER_KG = 2.2046226218

    /**
     * Convert pounds to kilograms.
     */
    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    /**
     * Convert kilograms to pounds.
     */
    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    /**
     * Round kg to nearest 0.5 kg for display.
     */
    fun roundKgToHalf(kg: Double): Double = (kg * 2).roundToInt() / 2.0

    /**
     * Convert lbs to kg and round for display.
     */
    fun lbsToDisplayKg(lbs: Double): Double = roundKgToHalf(lbsToKg(lbs))

    /**
     * Format weight for display based on unit preference.
     */
    fun formatWeight(weightLbs: Double?, displayInKg: Boolean): String {
        if (weightLbs == null) return "—"
        return if (displayInKg) {
            val kg = lbsToDisplayKg(weightLbs)
            "${formatNumber(kg)} kg"
        } else {
            "${formatNumber(weightLbs)} lbs"
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
