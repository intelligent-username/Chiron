package com.chiron.core.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Fixed-length, inclusive day window on a today-anchored paging grid. */
@ConsistentCopyVisibility
data class TimelineWindow private constructor(
    val end: LocalDate,
    val spanDays: Int,
    private val earliest: LocalDate,
    private val today: LocalDate
) {
    val start: LocalDate get() = end.minusDays(spanDays - 1L)
    private val earliestEnd: LocalDate
        get() = earliest.plusDays(spanDays - 1L).coerceAtMost(today)
    val canGoPrevious: Boolean get() = end > earliestEnd
    val canGoNext: Boolean get() = end < today

    fun previous(): TimelineWindow {
        if (!canGoPrevious) return this
        val daysBack = ChronoUnit.DAYS.between(end, today)
        val page = Math.floorDiv(daysBack, spanDays.toLong()) + 1
        return copy(end = today.minusDays(page * spanDays).coerceAtLeast(earliestEnd))
    }

    fun next(): TimelineWindow {
        if (!canGoNext) return this
        val daysBack = ChronoUnit.DAYS.between(end, today)
        val page = Math.floorDiv(daysBack - 1, spanDays.toLong())
        return copy(end = today.minusDays(page * spanDays).coerceAtMost(today))
    }

    companion object {
        fun endingAt(
            end: LocalDate,
            spanDays: Int,
            earliest: LocalDate?,
            today: LocalDate
        ): TimelineWindow {
            require(spanDays > 0)
            val first = (earliest ?: today).coerceAtMost(today)
            val firstEnd = first.plusDays(spanDays - 1L).coerceAtMost(today)
            return TimelineWindow(end.coerceIn(firstEnd, today), spanDays, first, today)
        }
    }
}
