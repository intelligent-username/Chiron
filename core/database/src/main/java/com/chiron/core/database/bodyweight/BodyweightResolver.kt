package com.chiron.core.database.bodyweight

import com.chiron.core.model.BodyWeightEntry

/**
 * Day grain LOCF resolution over weigh-ins. Lbs in, lbs out.
 *
 * Rules: last entry dated on or before the queried day wins (same day
 * included, latest timestamp wins). Gaps resolve to the nearest prior entry.
 * Today and future resolve to the last known entry. Before the first entry
 * or with no entries the result is null, never 0.
 *
 * The caller sorts [entries] ascending by timestampUtc. Same millisecond
 * duplicates resolve to the last element in list order.
 */
object BodyweightResolver {

    private const val DAY_MS = 86_400_000L

    fun getWeightForTimestamp(dayStartUtc: Long, entries: List<BodyWeightEntry>): Double? {
        val endOfDay = dayStartUtc + DAY_MS
        var result: Double? = null
        for (entry in entries) {
            if (entry.timestampUtc < endOfDay) result = entry.weightLbs
        }
        return result
    }
}

/**
 * Days a weight change may move: [fromDayUtc, untilDayUtc).
 * Null [untilDayUtc] means open ended (no later log bounds the window).
 *
 * Example: logs on Mon and Thu. A Wed correction affects [Wed, Thu) only,
 * so Mon and Thu volumes stay identical. Deleting Thu extends Wed open ended.
 */
data class AffectedRange(val fromDayUtc: Long, val untilDayUtc: Long?)

/**
 * Build the affected range for a change dated [changedDayUtc].
 * [sortedDayStarts] holds UTC midnights of known log days, ascending.
 * The bound is the first log day after the change, or null if none.
 */
fun affectedRange(changedDayUtc: Long, sortedDayStarts: List<Long>): AffectedRange {
    val next = sortedDayStarts.firstOrNull { it > changedDayUtc }
    return AffectedRange(fromDayUtc = changedDayUtc, untilDayUtc = next)
}
