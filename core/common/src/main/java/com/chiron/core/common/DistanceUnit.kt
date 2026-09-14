package com.chiron.core.common

/** Canonical unit for distance display. DB always stores meters. */
enum class DistanceUnit(val key: String, val displayLabel: String) {
    METERS("meters", "Meters"),
    FEET("feet", "Feet");

    companion object {
        fun fromString(value: String?): DistanceUnit =
            values().firstOrNull { it.key == value } ?: METERS
    }
}
