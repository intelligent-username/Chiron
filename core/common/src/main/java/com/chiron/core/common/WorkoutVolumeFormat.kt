package com.chiron.core.common

import kotlin.math.roundToLong

fun formatWorkoutVolume(volumeLbs: Double, displayInKg: Boolean): String {
    val volume = (if (displayInKg) UnitConversion.lbsToKg(volumeLbs) else volumeLbs)
        .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    if (volume < 100.0) return volume.roundToLong().toString()

    val hundreds = (volume / 100.0).roundToLong()
    val thousands = hundreds / 10
    val decimal = hundreds % 10
    return if (decimal == 0L) "${thousands}k" else "$thousands.${decimal}k"
}
