package com.chiron.core.common

/** Monotonic deadlines keep playback overhead from accumulating between beats. */
object MetronomeTiming {
    fun intervalNanos(bpm: Int): Long {
        require(bpm in 20..300)
        return 60_000_000_000L / bpm
    }

    fun nextBeatNanos(previousDeadline: Long, now: Long, bpm: Int): Long {
        val interval = intervalNanos(bpm)
        val next = previousDeadline + interval
        return if (next > now) next else now + interval
    }
}
