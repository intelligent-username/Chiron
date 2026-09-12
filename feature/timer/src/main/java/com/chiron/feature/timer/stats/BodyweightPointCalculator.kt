package com.chiron.feature.timer

import com.chiron.core.model.BodyWeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.max

object BodyweightPointCalculator {

    fun computeStats(points: List<BodyweightPoint>): BodyweightStats {
        val actualPoints = points.filter { it.isActualInput && it.weightLbs > 0.0 }
        val allValidPoints = points.filter { it.weightLbs > 0.0 }
        if (allValidPoints.isEmpty()) return BodyweightStats()

        val current = actualPoints.lastOrNull()?.weightLbs ?: allValidPoints.last().weightLbs
        val change = if (actualPoints.size >= 2) {
            actualPoints.last().weightLbs - actualPoints.first().weightLbs
        } else if (allValidPoints.size >= 2) {
            allValidPoints.last().weightLbs - allValidPoints.first().weightLbs
        } else {
            0.0
        }

        val statsSource = if (actualPoints.isNotEmpty()) actualPoints else allValidPoints
        val weights = statsSource.map { it.weightLbs }

        return BodyweightStats(
            current = current,
            change = change,
            average = weights.average(),
            min = weights.minOrNull() ?: 0.0,
            max = weights.maxOrNull() ?: 0.0,
            count = actualPoints.size
        )
    }

    /**
     * Compute the earliest date of all logged inputs.
     * Strictly the earliest actual input date.
     */
    fun computeEarliestInputDate(
        allEntries: List<BodyWeightEntry>,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val valid = allEntries.filter { it.timestampUtc > 0L && it.weightLbs > 0.0 }
        if (valid.isEmpty()) return null
        return valid.minOf {
            Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate()
        }
    }

    /**
     * Total weeks needed to span from earliest input date to today with no artificial cap.
     */
    fun computeMaxWeeks(earliestDate: LocalDate?, today: LocalDate = LocalDate.now()): Int {
        if (earliestDate == null) return 2
        val days = ChronoUnit.DAYS.between(earliestDate, today).toInt() + 1
        return max(2, (days + 6) / 7)
    }

    /**
     * Build points between actualStart and actualEnd.
     * actualStart is strictly bounded by [earliestDate].
     */
    fun buildPoints(
        periodEnd: LocalDate,
        weekCount: Int,
        earliestDate: LocalDate?,
        allEntries: List<BodyWeightEntry>
    ): List<BodyweightPoint> {
        val zone = ZoneId.systemDefault()
        val sortedEntries = allEntries.filter { it.timestampUtc > 0L && it.weightLbs > 0.0 }
            .sortedBy { it.timestampUtc }

        // Find latest entry for each distinct calendar date
        val latestByDate = HashMap<LocalDate, BodyWeightEntry>()
        for (entry in sortedEntries) {
            val d = runCatching { Instant.ofEpochMilli(entry.timestampUtc).atZone(zone).toLocalDate() }.getOrNull()
            if (d != null) {
                latestByDate[d] = entry
            }
        }
        val knownDates = latestByDate.keys.sorted()

        // Determine date span
        val daysRequested = (weekCount.coerceAtLeast(1) * 7)
        val tentativeStart = periodEnd.minusDays((daysRequested - 1).toLong())
        val actualStart = if (earliestDate != null && tentativeStart < earliestDate) earliestDate else tentativeStart
        val actualEnd = periodEnd

        if (actualStart > actualEnd || knownDates.isEmpty()) {
            return emptyList()
        }

        val totalDays = ChronoUnit.DAYS.between(actualStart, actualEnd).toInt() + 1
        val points = ArrayList<BodyweightPoint>(totalDays)

        val dayFmt = DateTimeFormatter.ofPattern("EEE")
        val dateFmt = DateTimeFormatter.ofPattern("M/d")
        val monthFmt = DateTimeFormatter.ofPattern("MMM")

        for (dayOffset in 0 until totalDays) {
            val date = actualStart.plusDays(dayOffset.toLong())
            val hit = latestByDate[date]

            val weightLbs: Double
            val isActual: Boolean
            val timestampUtc: Long

            if (hit != null) {
                weightLbs = hit.weightLbs
                isActual = true
                timestampUtc = hit.timestampUtc
            } else {
                isActual = false
                timestampUtc = date.atStartOfDay(zone).toInstant().toEpochMilli()

                // Find closest previous and next known entries
                val prevDate = knownDates.lastOrNull { it < date }
                val nextDate = knownDates.firstOrNull { it > date }

                weightLbs = when {
                    prevDate != null && nextDate != null -> {
                        val wPrev = latestByDate[prevDate]!!.weightLbs
                        val wNext = latestByDate[nextDate]!!.weightLbs
                        val spanDays = ChronoUnit.DAYS.between(prevDate, nextDate).toDouble()
                        val diffDays = ChronoUnit.DAYS.between(prevDate, date).toDouble()
                        val t = (diffDays / spanDays).coerceIn(0.0, 1.0)
                        // Smooth S-curve (cosine interpolation) for continuous biological weight transition
                        val s = (1.0 - cos(t * Math.PI)) / 2.0
                        wPrev + (wNext - wPrev) * s
                    }
                    prevDate != null -> {
                        // After last input: carry forward
                        latestByDate[prevDate]!!.weightLbs
                    }
                    nextDate != null -> {
                        latestByDate[nextDate]!!.weightLbs
                    }
                    else -> 0.0
                }
            }

            // X-axis label determination based on span
            val label = when {
                totalDays <= 7 -> date.format(dayFmt)
                totalDays <= 21 -> if (dayOffset % 3 == 0 || dayOffset == totalDays - 1) date.format(dateFmt) else ""
                totalDays <= 60 -> if (dayOffset % 7 == 0 || dayOffset == totalDays - 1) date.format(dateFmt) else ""
                totalDays <= 180 -> if (date.dayOfMonth == 1 || dayOffset == 0 || dayOffset == totalDays - 1) date.format(dateFmt) else ""
                else -> if (date.dayOfMonth == 1) date.format(monthFmt) else ""
            }

            points.add(
                BodyweightPoint(
                    label = label,
                    weightLbs = weightLbs,
                    timestampUtc = timestampUtc,
                    date = date,
                    isActualInput = isActual
                )
            )
        }

        return points
    }
}
