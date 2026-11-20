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

    data class PreparedWeights(
        val latestByDate: Map<LocalDate, BodyWeightEntry>,
        val knownDates: List<LocalDate>
    )

    /** Normalize timestamps and index daily inputs once per repository emission. */
    fun prepare(allEntries: List<BodyWeightEntry>): PreparedWeights {
        val zone = ZoneId.systemDefault()
        val latestByDate = HashMap<LocalDate, BodyWeightEntry>()
        for (entry in allEntries) {
            if (entry.timestampUtc <= 0L || entry.weightLbs <= 0.0) continue
            val millis = if (entry.timestampUtc <= 9_999_999_999L) entry.timestampUtc * 1000L else entry.timestampUtc
            val date = runCatching { Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() }.getOrNull() ?: continue
            val previous = latestByDate[date]
            if (previous == null || millis >= previous.timestampUtc) {
                latestByDate[date] = entry.copy(timestampUtc = millis)
            }
        }
        return PreparedWeights(latestByDate.toMap(), latestByDate.keys.sorted())
    }

    fun computeStats(points: List<BodyweightPoint>, totalCount: Int = points.count { it.isActualInput }): BodyweightStats {
        val actualPoints = points.filter { it.isActualInput && it.weightLbs > 0.0 }
        val allValidPoints = points.filter { it.weightLbs > 0.0 }
        if (allValidPoints.isEmpty()) return BodyweightStats(count = totalCount)

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
            count = totalCount
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
        val today = LocalDate.now()
        val minAllowed = today.minusYears(10)
        val earliest = valid.minOf {
            val millis = if (it.timestampUtc in 1L..9_999_999_999L) it.timestampUtc * 1000L else it.timestampUtc
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        }
        return when {
            earliest.isBefore(minAllowed) -> minAllowed
            earliest.isAfter(today) -> today
            else -> earliest
        }
    }

    /**
     * Total weeks needed to span from earliest input date to today with no artificial cap.
     */
    fun computeMaxWeeks(earliestDate: LocalDate?, today: LocalDate = LocalDate.now()): Int {
        if (earliestDate == null) return 2
        val minDate = today.minusYears(10)
        val clampedEarliest = when {
            earliestDate.isBefore(minDate) -> minDate
            earliestDate.isAfter(today) -> today
            else -> earliestDate
        }
        val days = ChronoUnit.DAYS.between(clampedEarliest, today).toInt() + 1
        return max(2, (days + 6) / 7).coerceAtMost(520)
    }

    /** Build the entire requested span; the navigator handles history boundaries. */
    fun buildPoints(
        periodEnd: LocalDate,
        weekCount: Int,
        prepared: PreparedWeights
    ): List<BodyweightPoint> {
        val zone = ZoneId.systemDefault()
        val latestByDate = prepared.latestByDate
        val knownDates = prepared.knownDates

        val daysRequested = (weekCount.coerceAtLeast(1) * 7).coerceAtMost(3650)
        val actualStart = periodEnd.minusDays((daysRequested - 1).toLong())
        val actualEnd = periodEnd

        if (actualStart > actualEnd || knownDates.isEmpty()) {
            return emptyList()
        }

        val totalDays = (ChronoUnit.DAYS.between(actualStart, actualEnd).toInt() + 1).coerceIn(1, 3650)
        val points = ArrayList<BodyweightPoint>(totalDays)

        for (dayOffset in 0 until totalDays) {
            val date = actualStart.plusDays(dayOffset.toLong())
            val hit = latestByDate[date]

            val weightLbs: Double
            val isActual: Boolean
            val timestampUtc: Long

            if (hit != null) {
                weightLbs = hit.weightLbs
                isActual = true
                val millis = if (hit.timestampUtc in 1L..9_999_999_999L) hit.timestampUtc * 1000L else hit.timestampUtc
                timestampUtc = millis
            } else {
                isActual = false
                timestampUtc = date.atStartOfDay(zone).toInstant().toEpochMilli()

                // Efficient binary search for closest previous and next known entries
                val idx = knownDates.binarySearch(date)
                val ins = if (idx < 0) -idx - 1 else idx
                val prevDate = if (ins > 0) knownDates[ins - 1] else null
                val nextDate = if (ins < knownDates.size) {
                    if (idx >= 0 && ins + 1 < knownDates.size) knownDates[ins + 1]
                    else if (idx < 0) knownDates[ins]
                    else null
                } else null

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
                totalDays <= 7 -> date.format(BodyweightDayFormatter)
                totalDays <= 21 -> if (dayOffset % 3 == 0 || dayOffset == totalDays - 1) date.format(BodyweightDateFormatter) else ""
                totalDays <= 60 -> if (dayOffset % 7 == 0 || dayOffset == totalDays - 1) date.format(BodyweightDateFormatter) else ""
                totalDays <= 180 -> if (date.dayOfMonth == 1 || dayOffset == 0 || dayOffset == totalDays - 1) date.format(BodyweightDateFormatter) else ""
                else -> if (date.dayOfMonth == 1) date.format(BodyweightMonthFormatter) else ""
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

private val BodyweightDayFormatter = DateTimeFormatter.ofPattern("EEE")
private val BodyweightDateFormatter = DateTimeFormatter.ofPattern("M/d")
private val BodyweightMonthFormatter = DateTimeFormatter.ofPattern("MMM")
