package com.chiron.feature.history

internal fun Double.formatVolume(inKg: Boolean): String {
    val v = if (inKg) this * 0.453592 else this
    return when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        v >= 1_000     -> "%.1fk".format(v / 1_000)
        else           -> "%.0f".format(v)
    }
}
