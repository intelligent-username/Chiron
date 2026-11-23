package com.chiron.calories

import com.chiron.core.common.CalorieEntry
import com.chiron.core.database.bodyweight.BodyweightFileParser
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.bodyweight.ImportDateStrategy
import com.chiron.core.database.bodyweight.ImportDelimiter
import com.chiron.core.database.bodyweight.WeightImportUnit
import com.chiron.core.model.BodyWeightEntry
import com.chiron.feature.timer.CalorieGraphCalculator
import com.chiron.feature.timer.weightTextExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalorieGraphTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 3, 1)
    private val end = LocalDate.of(2026, 2, 28)

    private fun calorie(day: LocalDate, kcal: Double, protein: Double = 20.0, id: String = day.toString()) =
        CalorieEntry(id, day, "", kcal, protein, 1.0)

    private fun weight(day: LocalDate, lbs: Double) = BodyWeightEntry(
        id = day.toEpochDay(),
        weightLbs = lbs,
        timestampUtc = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 43_200_000L
    )

    @Test
    fun graphZeroFillsQuietDaysAndKeepsEveryDayInTheWindow() {
        val points = CalorieGraphCalculator.build(
            listOf(calorie(end, 2000.0, 150.0), calorie(end.minusDays(2), 1800.0, 90.0)),
            emptyList(), end, 1, 0
        )
        assertEquals(7, points.size)
        assertEquals(LocalDate.of(2026, 2, 22), points.first().date)
        assertEquals(0.0, points[3].calories, 0.0)
        assertEquals(1800.0, points[4].calories, 0.0)
        assertEquals(2000.0, points[6].calories, 0.0)
        assertEquals(90.0, points[4].protein, 0.0)
    }

    @Test
    fun positiveLagReadsLaterWeightsAndNegativeLagReadsEarlierWeights() {
        val weights = listOf(weight(end.minusDays(1), 180.0), weight(end.plusDays(1), 178.0))
        val lagged = CalorieGraphCalculator.build(emptyList(), weights, end, 1, 1)
        assertEquals(end.plusDays(1), lagged.last().weightDate)
        assertEquals(178.0, lagged.last().weightLbs!!, 0.0)
        val laggedBack = CalorieGraphCalculator.build(emptyList(), weights, end, 1, -1)
        assertEquals(end.minusDays(1), laggedBack.last().weightDate)
        assertEquals(180.0, laggedBack.last().weightLbs!!, 0.0)
    }

    @Test
    fun windowHonoursEarliestEntryAndToday() {
        val window = CalorieGraphCalculator.window(end, 2, listOf(calorie(end.minusDays(30), 1900.0)), today)
        assertEquals(end, window.end)
        assertTrue(window.start <= end.minusDays(13))
    }

    @Test
    fun textExportRoundTripsThroughTheWeightImporter() {
        val entries = listOf(
            weight(LocalDate.of(2026, 1, 10), 180.2),
            weight(LocalDate.of(2026, 1, 17), 179.6)
        )
        val exported = weightTextExport(entries, zone)
        assertEquals(
            listOf("180.2 lbs · 2026-01-10", "179.6 lbs · 2026-01-17"),
            exported.lines()
        )
        val config = BodyweightImportConfig(
            unit = WeightImportUnit.LBS,
            lineStride = 1,
            startChar = 0,
            fieldLength = null,
            startRow = 1,
            headerSkipLines = 0,
            delimiter = ImportDelimiter.AUTO,
            dateStrategy = ImportDateStrategy.AUTO_DETECT,
            autoDetectDates = true,
            anchorDateUtc = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        val parsed = BodyweightFileParser.parse(exported.lineSequence(), config)
        assertEquals(2, parsed.rows.size)
        assertEquals(listOf(180.2, 179.6), parsed.rows.map { it.weightLbs })
        val dates = parsed.rows.map {
            Instant.ofEpochMilli(it.timestampUtc).atZone(ZoneOffset.UTC).toLocalDate()
        }
        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17)), dates)
    }

    @Test(expected = IllegalArgumentException::class)
    fun graphRejectsOutOfRangeLag() {
        CalorieGraphCalculator.build(emptyList(), emptyList(), end, 1, 4)
    }
}
