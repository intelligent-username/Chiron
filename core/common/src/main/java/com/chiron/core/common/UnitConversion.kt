package com.chiron.core.common

import com.chiron.core.model.Exercise
import com.chiron.core.model.SetEntry
import com.chiron.core.common.DistanceUnit
import kotlin.math.roundToInt

/**
 * Conversion utilities for weight, distance, and time units.
 * DB stores weights in pounds, distances in meters, durations in seconds.
 * UI converts based on user prefs.
 */
object UnitConversion {
    const val LBS_PER_KG = 2.205

    // ── Weight ────────────────────────────────────────────────────────────────

    /** Convert pounds to kilograms. */
    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    /** Convert kilograms to pounds. */
    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    /** Convert lbs to kg for display. */
    fun lbsToDisplayKg(lbs: Double): Double = lbs / LBS_PER_KG

    /** Format weight for display based on unit preference. */
    fun formatWeight(weightLbs: Double?, displayInKg: Boolean): String {
        if (weightLbs == null) return "—"
        return if (displayInKg) {
            val kg = lbsToDisplayKg(weightLbs)
            "${formatNumber(kg)} kg"
        } else {
            "${formatNumber(weightLbs)} lbs"
        }
    }

    // ── Distance ──────────────────────────────────────────────────────────────

    private const val METERS_PER_FOOT = 0.3048

    /** Convert meters to feet. */
    fun metersToFeet(meters: Double): Double = meters / METERS_PER_FOOT

    /** Convert feet to meters. */
    fun feetToMeters(feet: Double): Double = feet * METERS_PER_FOOT

    /**
     * Format a distance stored in meters for display based on the user's preferred unit.
     */
    fun formatDistance(distanceMeters: Double?, unit: DistanceUnit): String {
        if (distanceMeters == null) return "—"
        return when (unit) {
            DistanceUnit.METERS -> "${formatNumber(distanceMeters)} m"
            DistanceUnit.FEET -> "${formatNumber(metersToFeet(distanceMeters))} ft"
        }
    }

    // ── Time ──────────────────────────────────────────────────────────────────

    /**
     * Format a duration in seconds as m:ss (or h:mm:ss for durations >= 1 hour).
     */
    fun formatDuration(seconds: Int?): String {
        if (seconds == null) return "—"
        val totalSec = seconds.coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            "$hours:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        } else {
            "$mins:${secs.toString().padStart(2, '0')}"
        }
    }

    // ── Combined set display ──────────────────────────────────────────────────

    /**
     * Build a display string for a set pill given the exercise config and user prefs.
     *
     * Examples:
     *  - weight+reps:     "80 kg × 5"
     *  - weight+time:     "80 lbs × 1:30"
     *  - distance+reps:   "500 m × 8"
     *  - time only:       "2:00"
     *  - distance only:   "1.5 km"
     *  - placeholder:     "—"
     */
    fun formatSet(
        set: SetEntry,
        exercise: Exercise,
        displayInKg: Boolean,
        distanceUnit: DistanceUnit
    ): String {
        val hasWeight   = exercise.isWeightBased == 1
        val hasReps     = exercise.isRepBased == 1
        val hasTime     = exercise.isTimeBased == 1
        val hasDist     = exercise.isDistanceBased == 1

        // Determine individual parts
        val weightLbs = set.weightLbs
        val weightPart = if (hasWeight && weightLbs != null) {
            if (displayInKg) "${formatNumber(lbsToDisplayKg(weightLbs))}kg"
            else "${formatNumber(weightLbs)}lbs"
        } else null

        val reps = set.reps
        val durationSeconds = set.durationSeconds
        val distanceMeters = set.distanceMeters
        val repOrTimePart = when {
            hasReps && reps != null -> "$reps"
            hasTime && durationSeconds != null -> formatDuration(durationSeconds)
            else -> null
        }

        val distPart = if (hasDist && distanceMeters != null) {
            formatDistance(distanceMeters, distanceUnit)
        } else null

        // Combine parts
        val parts = listOfNotNull(
            weightPart,
            repOrTimePart?.let { r -> if (weightPart != null) "× $r" else r },
            distPart?.let { d -> if (weightPart != null || repOrTimePart != null) "· $d" else d }
        )

        return if (parts.isEmpty()) "—" else parts.joinToString(" ")
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    fun formatNumber(value: Double): String {
        val formatted = String.format("%.2f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
