package com.chiron.common

import com.chiron.core.common.UnitConversion
import com.chiron.core.common.formatWorkoutVolume
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVolumeFormatTest {
    @Test
    fun smallVolumesRoundToWholeNumbers() {
        assertEquals("0", formatWorkoutVolume(0.0, false))
        assertEquals("49", formatWorkoutVolume(49.4, false))
        assertEquals("50", formatWorkoutVolume(49.5, false))
        assertEquals("100", formatWorkoutVolume(99.9, false))
    }

    @Test
    fun hundredsUseTenthsOfK() {
        assertEquals("0.1k", formatWorkoutVolume(100.0, false))
        assertEquals("0.1k", formatWorkoutVolume(149.9, false))
        assertEquals("0.2k", formatWorkoutVolume(150.0, false))
        assertEquals("0.5k", formatWorkoutVolume(500.0, false))
        assertEquals("1k", formatWorkoutVolume(950.0, false))
        assertEquals("11.5k", formatWorkoutVolume(11_460.0, false))
        assertEquals("12k", formatWorkoutVolume(11_950.0, false))
    }

    @Test
    fun convertsUnitsBeforeApplyingThresholdAndRounding() {
        assertEquals("50", formatWorkoutVolume(UnitConversion.kgToLbs(50.4), true))
        assertEquals("0.1k", formatWorkoutVolume(UnitConversion.kgToLbs(100.0), true))
        assertEquals("0.5k", formatWorkoutVolume(UnitConversion.kgToLbs(500.0), true))
        assertEquals("11.5k", formatWorkoutVolume(UnitConversion.kgToLbs(11_460.0), true))
        assertEquals("1k", formatWorkoutVolume(2_205.0, true))
        assertEquals("2.2k", formatWorkoutVolume(2_205.0, false))
    }

    @Test
    fun formattingDoesNotExposeInvalidNumbers() {
        assertEquals("0", formatWorkoutVolume(Double.NaN, false))
        assertEquals("0", formatWorkoutVolume(Double.POSITIVE_INFINITY, true))
        assertEquals("0", formatWorkoutVolume(-10.0, false))
    }
}
