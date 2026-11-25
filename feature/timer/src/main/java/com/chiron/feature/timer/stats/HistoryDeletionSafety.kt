package com.chiron.feature.timer

import com.chiron.core.common.CalorieEntry
import com.chiron.core.model.BodyWeightEntry
import java.time.LocalDate
import java.time.ZoneId

/** A frozen review, including full rows rather than only their count or IDs. */
data class WeightRangeReview(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val zoneId: String,
    val startUtc: Long,
    val endUtc: Long,
    val entries: List<BodyWeightEntry>
)

fun reviewWeightRange(
    startInput: String,
    endInput: String,
    entries: List<BodyWeightEntry>,
    zone: ZoneId
): WeightRangeReview? = runCatching {
    val start = LocalDate.parse(startInput.trim())
    val end = LocalDate.parse(endInput.trim())
    require(!start.isAfter(end))
    val startUtc = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextDayUtc = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val endUtc = Math.subtractExact(nextDayUtc, 1L)
    require(startUtc <= endUtc)
    val matching = entries.filter { it.timestampUtc in startUtc..endUtc }
        .sortedBy { it.id }.map { it.copy() }
    require(matching.isNotEmpty())
    WeightRangeReview(start, end, zone.id, startUtc, endUtc, matching)
}.getOrNull()

fun weightRangeUnchanged(
    review: WeightRangeReview,
    entries: List<BodyWeightEntry>,
    zone: ZoneId
): Boolean {
    if (zone.id != review.zoneId) return false
    val current = reviewWeightRange(
        review.startDate.toString(), review.endDate.toString(), entries, zone
    ) ?: return false
    return current.startUtc == review.startUtc && current.endUtc == review.endUtc &&
        current.entries == review.entries.sortedBy { it.id }
}

fun calorieDayUnchanged(
    date: LocalDate,
    reviewed: List<CalorieEntry>,
    entries: List<CalorieEntry>
): Boolean {
    fun snapshot(source: List<CalorieEntry>) =
        source.filter { it.date == date }.sortedBy { it.id }.map { it.copy() }
    return snapshot(reviewed) == snapshot(entries)
}
