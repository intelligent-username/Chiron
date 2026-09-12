package com.chiron.feature.timer

import com.chiron.core.database.bodyweight.BodyweightResolver
import com.chiron.core.model.BodyWeightEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object BodyweightPointCalculator {

    fun computeStats(points: List<BodyweightPoint>): BodyweightStats {
        val weights = points.map { it.weightLbs }.filter { it > 0.0 }
        if (weights.isEmpty()) return BodyweightStats()
        return BodyweightStats(
            current = weights.last(),
            change = if (weights.size >= 2) weights.last() - weights[weights.size - 2] else 0.0,
            average = weights.average(),
            min = weights.minOrNull() ?: 0.0,
            max = weights.maxOrNull() ?: 0.0,
            count = weights.size
        )
    }

    fun buildPoints(
        mode: BodyweightMode,
        weekStart: LocalDate,
        weekCount: Int,
        allEntries: List<BodyWeightEntry>
    ): List<BodyweightPoint> {
        val zone = ZoneId.systemDefault()
        val sortedEntries = allEntries.sortedBy { it.timestampUtc }

        // O(1) date lookup map
        val latestByDate = HashMap<LocalDate, BodyWeightEntry>()
        for (entry in sortedEntries) {
            val d = runCatching { Instant.ofEpochMilli(entry.timestampUtc).atZone(zone).toLocalDate() }.getOrNull()
            if (d != null) {
                latestByDate[d] = entry
            }
        }

        return if (mode == BodyweightMode.BY_DAY && weekCount == 1) {
            buildDayPoints(weekStart, sortedEntries, latestByDate, zone)
        } else {
            buildLongTermPoints(weekStart, weekCount.coerceAtLeast(1), sortedEntries, latestByDate, zone)
        }
    }

    private fun buildDayPoints(
        weekStart: LocalDate,
        sortedEntries: List<BodyWeightEntry>,
        latestByDate: Map<LocalDate, BodyWeightEntry>,
        zone: ZoneId
    ): List<BodyweightPoint> {
        val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val hit = latestByDate[date]
            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val resolvedLbs = hit?.weightLbs ?: BodyweightResolver.getWeightForTimestamp(dayStartMs, sortedEntries)
            BodyweightPoint(
                label = labels[offset],
                weightLbs = resolvedLbs ?: 0.0,
                timestampUtc = hit?.timestampUtc ?: 0L,
                date = date,
                isActualInput = hit != null
            )
        }
    }

    private fun buildLongTermPoints(
        weekStart: LocalDate,
        weekCount: Int,
        sortedEntries: List<BodyWeightEntry>,
        latestByDate: Map<LocalDate, BodyWeightEntry>,
        zone: ZoneId
    ): List<BodyweightPoint> {
        val clusterStart = weekStart.minusWeeks((weekCount - 1).toLong())
        val fmt = DateTimeFormatter.ofPattern("M/d")
        val out = ArrayList<BodyweightPoint>(weekCount * 7)

        for (w in 0 until weekCount) {
            val current = clusterStart.plusWeeks(w.toLong())
            for (d in 0..6) {
                val date = current.plusDays(d.toLong())
                val hit = latestByDate[date]
                val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val resolvedLbs = hit?.weightLbs ?: BodyweightResolver.getWeightForTimestamp(dayStartMs, sortedEntries)
                val label = if (d == 0) current.format(fmt) else ""
                out.add(
                    BodyweightPoint(
                        label = label,
                        weightLbs = resolvedLbs ?: 0.0,
                        timestampUtc = hit?.timestampUtc ?: 0L,
                        date = date,
                        isActualInput = hit != null
                    )
                )
            }
        }
        return out
    }

    fun computeFirstWeekStart(allEntries: List<BodyWeightEntry>): LocalDate {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val minAllowed = today.minusYears(5)
        val validEntries = allEntries.filter { it.timestampUtc > 0L }
        val earliest = validEntries.minOfOrNull {
            runCatching {
                Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate()
            }.getOrDefault(today)
        } ?: today
        val clamped = if (earliest.isBefore(minAllowed)) minAllowed else earliest
        return clamped.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }

    fun todayWeekStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun isCurrentWeek(weekStart: LocalDate): Boolean = weekStart >= todayWeekStart()
}
