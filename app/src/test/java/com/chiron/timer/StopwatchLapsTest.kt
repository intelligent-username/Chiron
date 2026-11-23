package com.chiron.timer

import com.chiron.feature.timer.TimerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopwatchLapsTest {
    @Test
    fun emptyLapsHaveNoAverageOrCopyText() {
        assertEquals(emptyList<Long>(), TimerViewModel.completedLapDurations(emptyList()))
        assertNull(TimerViewModel.averageLapDuration(emptyList()))
        assertEquals("", TimerViewModel.copyLapText(emptyList()))
    }

    @Test
    fun singleLapUsesItsCumulativeTimeAsDuration() {
        val laps = listOf(12_345L)
        assertEquals(listOf(12_345L), TimerViewModel.completedLapDurations(laps))
        assertEquals(12_345L, TimerViewModel.averageLapDuration(laps))
        assertEquals("00:12.34", TimerViewModel.formatStopwatch(TimerViewModel.averageLapDuration(laps)!!))
        assertEquals("Lap 1: 00:12.34 (+00:12.34)", TimerViewModel.copyLapText(laps))
    }

    @Test
    fun unevenLapsAverageCompletedDurationsAndCopyNewestFirst() {
        val laps = listOf(1_010L, 3_040L, 6_055L)
        assertEquals(listOf(1_010L, 2_030L, 3_015L), TimerViewModel.completedLapDurations(laps))
        assertEquals(2_018L, TimerViewModel.averageLapDuration(laps))
        assertEquals("00:02.01", TimerViewModel.formatStopwatch(TimerViewModel.averageLapDuration(laps)!!))
        assertEquals(
            "Lap 3: 00:06.05 (+00:03.01)\n" +
                "Lap 2: 00:03.04 (+00:02.03)\n" +
                "Lap 1: 00:01.01 (+00:01.01)",
            TimerViewModel.copyLapText(laps)
        )
    }
}
