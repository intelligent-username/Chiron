package com.chiron.app.util

import kotlin.math.roundToInt

/**
 * Conversion utilities for weight units.
 * DB stores weights in pounds; UI may display kg.
 */
object UnitConversion {
    const val LBS_PER_KG = 2.205

    /**
     * Convert pounds to kilograms.
     */
    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    /**
     * Convert kilograms to pounds.
     */
    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    /**
     * Convert lbs to kg for display.
     */
    fun lbsToDisplayKg(lbs: Double): Double = lbs / LBS_PER_KG

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
        // Round to 2 decimal places
        val formatted = String.format("%.2f", value)
        // Remove trailing zeros and decimal point if needed
        return formatted.trimEnd('0').trimEnd('.')
    }
}
