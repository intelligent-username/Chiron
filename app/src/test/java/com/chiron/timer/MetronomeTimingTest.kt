package com.chiron.timer

import com.chiron.core.common.MetronomeTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeTimingTest {
    @Test
    fun intervalsRetainSubMillisecondPrecision() {
        assertEquals(1_000_000_000L, MetronomeTiming.intervalNanos(60))
        assertEquals(487_804_878L, MetronomeTiming.intervalNanos(123))
        assertEquals(200_000_000L, MetronomeTiming.intervalNanos(300))
    }

    @Test
    fun smallPlaybackDelaysDoNotShiftTheBeatGrid() {
        assertEquals(
            2_000_000_000L,
            MetronomeTiming.nextBeatNanos(1_000_000_000L, 1_008_000_000L, 60)
        )
    }

    @Test
    fun schedulerStallsNeverCauseCatchUpBursts() {
        assertEquals(
            4_500_000_000L,
            MetronomeTiming.nextBeatNanos(1_000_000_000L, 3_500_000_000L, 60)
        )
    }

    @Test
    fun tempoChangesApplyToTheNextInterval() {
        assertEquals(
            1_500_000_000L,
            MetronomeTiming.nextBeatNanos(1_000_000_000L, 1_001_000_000L, 120)
        )
    }
}
