package com.chiron.calories

import com.chiron.core.common.CalorieTextImporter
import com.chiron.core.common.NutritionNotebookParser
import com.chiron.core.common.UnitConversion
import com.chiron.core.database.bodyweight.BodyweightFileParser
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.bodyweight.ImportDateStrategy
import com.chiron.core.database.bodyweight.ImportDelimiter
import com.chiron.core.database.bodyweight.ImportFileOrder
import com.chiron.core.database.bodyweight.WeightImportUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NutritionImportTest {
    private val example = checkNotNull(javaClass.getResource("/nutrition-counter-2026.txt")).readText()
    private val anchor = LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun suppliedNotebookImportsExactly59CalorieDays() {
        val result = CalorieTextImporter.parse(example, 2025)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(59, result.entries.size)
        assertEquals(LocalDate.of(2026, 1, 1), result.entries.first().date)
        assertEquals(LocalDate.of(2026, 2, 28), result.entries.last().date)
        assertEquals(1340.0, result.entries.first().calories, 0.0)
        assertEquals(53.0, result.entries.first().protein, 0.0)
        assertEquals(113.5, result.entries.single { it.date == LocalDate.of(2026, 1, 10) }.protein, 0.0)
        assertEquals(426.0, result.entries.single { it.date == LocalDate.of(2026, 1, 16) }.calories, 0.0)
        assertTrue(result.entries.all { it.servings == 1.0 })
        assertTrue(result.ignoredLines > 0)
    }

    @Test
    fun repeatNotebookImportsUseStableDailyIdsEvenWhenTotalsChange() {
        val first = CalorieTextImporter.parse("2026 Counter\nJanuary 1st: 1340, 53")
        val edited = CalorieTextImporter.parse("| 2026 Counter | January 1st: 1500, 60")
        assertEquals(first.entries.single().id, edited.entries.single().id)
        assertEquals(1500.0, edited.entries.single().calories, 0.0)
    }

    @Test
    fun invalidDatedRowsAreReportedRatherThanSilentlySkipped() {
        val result = CalorieTextImporter.parse("2026 Counter\nFebruary 30th: 1234, 50\nJanuary 1st: -2, 5\nJanuary 2nd: 100, NaN")
        assertEquals(3, result.errors.size)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun duplicateDailyTotalsRequireCorrectionBeforeImport() {
        val result = CalorieTextImporter.parse("2026 Counter\nJanuary 1st: 100, 20\nJanuary 1st: 200, 40")
        assertTrue(result.errors.any { it.contains("Multiple daily totals") })
    }

    @Test
    fun supportsIsoDatesAndZeroNutritionValues() {
        val result = CalorieTextImporter.parse("2026-01-01: 0, 0\n2026-01-02: 1234.5, 78.25")
        assertTrue(result.errors.isEmpty())
        assertEquals(0.0, result.entries.first().calories, 0.0)
        assertEquals(78.25, result.entries.last().protein, 0.0)
    }

    @Test
    fun headerYearOverridesFallbackButExplicitRowYearIsPreserved() {
        val result = CalorieTextImporter.parse("2025 Counter\nJanuary 1st: 100, 10\nJanuary 2nd, 2026: 200, 20", 2024)
        assertEquals(listOf(2025, 2026), result.entries.map { it.date.year })
    }

    @Test
    fun notebookContainsEightWeeklyWeighInsNotAveragesOrDailyFoods() {
        val result = NutritionNotebookParser.parse(example)
        assertEquals(listOf(70.3, 69.95, 69.5, 69.5, 69.35, 68.1, 66.9, 66.7), result.weights.map { it.value })
        assertEquals(LocalDate.of(2026, 1, 4), result.weights.first().date)
        assertEquals(LocalDate.of(2026, 2, 22), result.weights.last().date)
    }

    @Test
    fun automaticWeightPatternUsesTenItemsNotEverySecondItem() {
        val items = BodyweightFileParser.splitText(example)
        val detected = checkNotNull(BodyweightFileParser.detectPattern(items))
        assertEquals(10, detected.stride)
        assertEquals(7, detected.stepDays)
        assertEquals(WeightImportUnit.KG, detected.unit)
        assertEquals(8, detected.sampleCount)
        val config = BodyweightImportConfig(
            unit = detected.unit, startRow = detected.startRow, lineStride = detected.stride,
            stepDays = detected.stepDays, anchorDateUtc = anchor
        )
        val rows = BodyweightFileParser.parse(items.asSequence(), config)
        assertTrue(rows.errors.toString(), rows.errors.isEmpty())
        assertEquals(8, rows.rows.size)
        assertEquals(70.3, UnitConversion.lbsToKg(rows.rows.first().weightLbs), 0.000001)
        assertEquals(LocalDate.of(2026, 1, 4), Instant.ofEpochMilli(rows.rows.first().timestampUtc).atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 22), Instant.ofEpochMilli(rows.rows.last().timestampUtc).atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(rows, BodyweightFileParser.parse(example.lineSequence(), config))
    }

    @Test
    fun manualStartRowStrideColumnAndFieldLengthStillWork() {
        val config = BodyweightImportConfig(
            unit = WeightImportUnit.KG, startRow = 2, lineStride = 2, startChar = 2, fieldLength = 5,
            delimiter = ImportDelimiter.LINES, dateStrategy = ImportDateStrategy.ONE_PER_DAY_BACKWARDS,
            autoDetectDates = false, anchorDateUtc = anchor, stepDays = 7
        )
        val result = BodyweightFileParser.parse(sequenceOf("heading", "xx70.30junk", "skip", "xx69.95junk"), config)
        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(2, 4), result.rows.map { it.sourceLineNumber })
        assertEquals(70.3, UnitConversion.lbsToKg(result.rows.first().weightLbs), 0.000001)
        assertEquals(LocalDate.of(2026, 1, 25), Instant.ofEpochMilli(result.rows.first().timestampUtc).atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 1), Instant.ofEpochMilli(result.rows.last().timestampUtc).atZone(ZoneOffset.UTC).toLocalDate())
    }

    @Test
    fun manualNewestFirstAndPoundsStillWork() {
        val config = BodyweightImportConfig(
            unit = WeightImportUnit.LBS, fileOrder = ImportFileOrder.NEWEST_FIRST,
            dateStrategy = ImportDateStrategy.ONE_PER_DAY_BACKWARDS, autoDetectDates = false, anchorDateUtc = anchor
        )
        val result = BodyweightFileParser.parse(sequenceOf("150", "149"), config)
        assertEquals(150.0, result.rows.first().weightLbs, 0.0)
        assertTrue(result.rows.first().timestampUtc > result.rows.last().timestampUtc)
    }

    @Test
    fun commasAndTabsAndDecimalCommaKeepExistingBehavior() {
        val config = BodyweightImportConfig(anchorDateUtc = anchor)
        for (delimiter in listOf(ImportDelimiter.COMMAS, ImportDelimiter.TABS)) {
            val items = BodyweightFileParser.splitText("150${delimiter.separator}151", delimiter)
            assertEquals(2, BodyweightFileParser.parse(items.asSequence(), config.copy(delimiter = delimiter)).rows.size)
        }
        val decimal = BodyweightFileParser.parse(sequenceOf("70,3"), config.copy(
            unit = WeightImportUnit.KG, delimiter = ImportDelimiter.LINES, decimalSeparator = ','
        ))
        assertEquals(70.3, UnitConversion.lbsToKg(decimal.rows.single().weightLbs), 0.000001)
    }

    @Test
    fun nonFiniteWeightIsNeverAccepted() {
        val result = BodyweightFileParser.parse(sequenceOf("NaN"), BodyweightImportConfig(anchorDateUtc = anchor))
        assertTrue(result.rows.isEmpty())
        assertFalse(result.errors.isEmpty())
    }
}
