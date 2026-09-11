package com.chiron.bodyweight

import com.chiron.core.database.bodyweight.AffectedRange
import com.chiron.core.database.bodyweight.BodyweightResolver
import com.chiron.core.database.bodyweight.affectedRange
import com.chiron.core.model.BodyWeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Edge matrix for day grain LOCF resolution plus affectedRange bounds.
 * Fixed epoch fixtures only, no clocks. MON is Mon 2024-05-06 00:00 UTC.
 */
class BodyweightResolverTest {

    companion object {
        private const val DAY = 86_400_000L
        private const val MON = 1_714_953_600_000L
        private const val TUE = MON + DAY
        private const val WED = MON + 2 * DAY
        private const val THU = MON + 3 * DAY
        private const val FRI = MON + 4 * DAY
        private const val SUN_PRE = MON - DAY
        private const val MON_WT = 176.4
        private const val THU_WT = 180.8
    }

    private fun entry(id: Long, ts: Long, lbs: Double): BodyWeightEntry =
        BodyWeightEntry(id = id, timestampUtc = ts, weightLbs = lbs)

    private fun monThu(): List<BodyWeightEntry> = listOf(
        entry(1, MON + 8 * 3_600_000L, MON_WT),
        entry(2, THU + 8 * 3_600_000L, THU_WT)
    )

    @Test
    fun exactTimestampDay_returnsOwnValue() {
        assertEquals(MON_WT, BodyweightResolver.getWeightForTimestamp(MON, monThu()))
    }

    @Test
    fun midGap_returnsNearestPrior() {
        assertEquals(MON_WT, BodyweightResolver.getWeightForTimestamp(WED, monThu()))
    }

    @Test
    fun nextDayAfterEntry_returnsThatEntry() {
        assertEquals(THU_WT, BodyweightResolver.getWeightForTimestamp(FRI, monThu()))
    }

    @Test
    fun futureDay_returnsLastKnown() {
        assertEquals(THU_WT, BodyweightResolver.getWeightForTimestamp(MON + 60 * DAY, monThu()))
    }

    @Test
    fun preFirst_returnsNull() {
        assertNull(BodyweightResolver.getWeightForTimestamp(SUN_PRE, monThu()))
    }

    @Test
    fun empty_returnsNull() {
        assertNull(BodyweightResolver.getWeightForTimestamp(MON, emptyList()))
    }

    @Test
    fun singleEntry_coversDayAndLater_only() {
        val single = listOf(entry(1, MON + 8 * 3_600_000L, MON_WT))
        assertEquals(MON_WT, BodyweightResolver.getWeightForTimestamp(MON, single))
        assertEquals(MON_WT, BodyweightResolver.getWeightForTimestamp(TUE, single))
        assertNull(BodyweightResolver.getWeightForTimestamp(SUN_PRE, single))
    }

    @Test
    fun sameDayPair_lastWins() {
        val pair = listOf(
            entry(1, MON + 8 * 3_600_000L, MON_WT),
            entry(2, MON + 19 * 3_600_000L, THU_WT)
        )
        assertEquals(THU_WT, BodyweightResolver.getWeightForTimestamp(MON, pair))
    }

    @Test
    fun sameMillisecond_lastInListWins() {
        val pair = listOf(entry(1, MON, MON_WT), entry(2, MON, THU_WT))
        assertEquals(THU_WT, BodyweightResolver.getWeightForTimestamp(MON, pair))
    }

    @Test
    fun deleteRecompute_pureFunctionOfRemainingEntries() {
        assertEquals(MON_WT, BodyweightResolver.getWeightForTimestamp(WED, monThu()))
        val afterDelete = monThu().drop(1)
        assertNull(BodyweightResolver.getWeightForTimestamp(WED, afterDelete))
        assertEquals(THU_WT, BodyweightResolver.getWeightForTimestamp(FRI, afterDelete))
    }

    @Test
    fun affectedRange_boundedByNextLog() {
        assertEquals(
            AffectedRange(MON, THU),
            affectedRange(MON, listOf(MON, THU))
        )
    }

    @Test
    fun affectedRange_openEndedOnLastLog() {
        assertEquals(
            AffectedRange(THU, null),
            affectedRange(THU, listOf(MON, THU))
        )
    }

    @Test
    fun affectedRange_wedEditCoversWedToThuOnly() {
        assertEquals(
            AffectedRange(WED, THU),
            affectedRange(WED, listOf(MON, THU))
        )
    }
}
