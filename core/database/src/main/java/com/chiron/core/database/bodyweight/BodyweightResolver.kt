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
        if (entries.isEmpty()) return null
        val target = dayStartUtc + DAY_MS - 1
        if (entries.first().timestampUtc > target) return null
        if (entries.last().timestampUtc <= target) return entries.last().weightLbs

        var low = 0
        var high = entries.size - 1
        var candidate = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].timestampUtc <= target) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (candidate != -1) entries[candidate].weightLbs else null
    }
}
