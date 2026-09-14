package com.chiron.core.database.workout

import com.chiron.core.database.bodyweight.BodyweightResolver
import com.chiron.core.database.dao.BodyWeightDao
import com.chiron.core.database.dao.BodyweightSetRow
import com.chiron.core.database.dao.DailyVolume
import com.chiron.core.database.dao.VolumeAnalyticsDao
import com.chiron.core.model.BodyWeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Repository dedicated strictly to aggregating and computing volume metrics across workouts,
 * including base weighted volume and dynamic bodyweight calculations.
 */
class VolumeAnalyticsRepository(
    private val volumeAnalyticsDao: VolumeAnalyticsDao,
    private val bodyWeightDao: BodyWeightDao? = null
) {

    /**
     * Total volume grouped by workout day, including dynamic bodyweight share.
     */
    fun getVolumeSummaryByDayFlow(exerciseId: Long? = null): Flow<List<DailyVolume>> {
        val bwDao = bodyWeightDao ?: return baseVolumeFlow(exerciseId)
        val weightsFlow = bwDao.getAllFlow()
        return if (exerciseId != null) {
            combine(
                weightsFlow,
                volumeAnalyticsDao.getVolumeSummaryByDayForExerciseFlow(exerciseId),
                volumeAnalyticsDao.getBodyweightSetRowsForExerciseFlow(exerciseId)
            ) { weights, base, rows -> mergeVolumes(base, rows, weights) }
                .distinctUntilChanged()
        } else {
            combine(
                weightsFlow,
                volumeAnalyticsDao.getVolumeSummaryByDayFlow(),
                volumeAnalyticsDao.getBodyweightSetRowsFlow()
            ) { weights, base, rows -> mergeVolumes(base, rows, weights) }
                .distinctUntilChanged()
        }
    }

    private fun baseVolumeFlow(exerciseId: Long?): Flow<List<DailyVolume>> =
        if (exerciseId != null) volumeAnalyticsDao.getVolumeSummaryByDayForExerciseFlow(exerciseId)
        else volumeAnalyticsDao.getVolumeSummaryByDayFlow()

    private fun mergeVolumes(
        base: List<DailyVolume>,
        rows: List<BodyweightSetRow>,
        weights: List<BodyWeightEntry>
    ): List<DailyVolume> {
        if (rows.isEmpty()) return base
        val validWeights = weights.filter { it.timestampUtc > 0L }.sortedBy { it.timestampUtc }
        val validRows = rows.filter { it.dateUtc > 0L }
        val cache = validRows.map { it.dateUtc }.toSet()
        .associateWith { day -> BodyweightResolver.getWeightForTimestamp(day, validWeights) }
        val extra = validRows.groupBy { it.dateUtc }
            .mapValues { (_, dayRows) -> dayRows.sumOf { effectiveVolume(it, cache[it.dateUtc]) } }
        val totals = base.filter { it.dateUtc > 0L }.associate { it.dateUtc to it.volumeLbs }.toMutableMap()
        for ((day, volume) in extra) totals[day] = (totals[day] ?: 0.0) + volume
        return totals.entries.sortedBy { it.key }
            .map { DailyVolume(it.key, it.value) }
    }

    private fun effectiveVolume(row: BodyweightSetRow, bwLbs: Double?): Double {
        val repEq = repEquivalent(row) ?: return 0.0
        if (bwLbs == null || bwLbs <= 0.0) return (row.addedWeightLbs ?: return 0.0) * repEq
        val pct = if (row.percentBodyweight <= 0) 100.0 else row.percentBodyweight
        return (bwLbs * pct / 100.0 + (row.addedWeightLbs ?: 0.0)) * repEq
    }

    private fun repEquivalent(row: BodyweightSetRow): Double? {
        val reps = row.reps
        val duration = row.durationSeconds
        val distance = row.distanceMeters
        return when {
            distance != null && reps != null -> reps * (distance * 2.0)
            distance != null -> distance / 5.0
            duration != null -> duration / 3.0
            reps != null -> reps.toDouble()
            else -> null
        }
    }
}
