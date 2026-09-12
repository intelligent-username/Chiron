package com.chiron.core.database.bodyweight

import java.time.LocalDate
import java.time.ZoneOffset

enum class WeightImportUnit { KG, LBS }

enum class ImportFileOrder { OLDEST_FIRST, NEWEST_FIRST }

enum class ImportDateStrategy { ONE_PER_DAY_BACKWARDS, EMBEDDED_DATE_COLUMN, SINGLE_TIMESTAMP, AUTO_DETECT }

enum class ImportDuplicateRule { OVERWRITE_SAME_DAY, SKIP_EXISTING }

enum class ImportDelimiter(val label: String, val separator: String?) {
    AUTO("Auto", null),
    PIPES("Pipes (|)", "|"),
    LINES("Lines (\\n)", "\n"),
    COMMAS("Commas (,)", ","),
    TABS("Tabs (\\t)", "\t")
}

data class BodyweightImportConfig(
    val unit: WeightImportUnit = WeightImportUnit.LBS,
    val lineStride: Int = 1,
    val startChar: Int = 0,
    val fieldLength: Int? = null,
    val headerSkipLines: Int = 0,
    val startRow: Int = headerSkipLines + 1,
    val delimiter: ImportDelimiter = ImportDelimiter.AUTO,
    val decimalSeparator: Char = '.',
    val fileOrder: ImportFileOrder = ImportFileOrder.OLDEST_FIRST,
    val dateStrategy: ImportDateStrategy = ImportDateStrategy.AUTO_DETECT,
    val autoDetectDates: Boolean = true,
    val anchorDateUtc: Long = midnightUtcToday(),
    val stepDays: Int = 1,
    val minWeightLbs: Double = 50.0,
    val maxWeightLbs: Double = 1500.0,
    val dateStartChar: Int? = null,
    val dateLength: Int? = null,
    val dateFormat: String? = null,
    val duplicateRule: ImportDuplicateRule = ImportDuplicateRule.OVERWRITE_SAME_DAY,
    val previewLimit: Int = 20
) {
    val effectiveStartRow: Int get() = if (startRow >= 1) startRow else (headerSkipLines + 1).coerceAtLeast(1)
    val effectiveHeaderSkipLines: Int get() = (effectiveStartRow - 1).coerceAtLeast(0)

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (lineStride < 1) errors.add("Lines stride must be >= 1")
        if (startChar < 0) errors.add("Start character must be >= 0")
        if (effectiveStartRow < 1) errors.add("Start row must be >= 1")
        if (fieldLength != null && fieldLength < 1) errors.add("Field length must be >= 1")
        if (decimalSeparator != '.' && decimalSeparator != ',') errors.add("Decimal separator must be . or ,")
        if (stepDays < 1) errors.add("Step days must be >= 1")
        if (anchorDateUtc <= 0) errors.add("Anchor date must be set")
        if (minWeightLbs <= 0 || minWeightLbs >= maxWeightLbs) errors.add("Invalid min/max weight bounds")
        if (previewLimit !in 1..100) errors.add("Preview limit must be 1..100")
        if (dateStrategy == ImportDateStrategy.EMBEDDED_DATE_COLUMN) {
            if (dateStartChar == null || dateStartChar < 0) errors.add("Date start char required (>= 0)")
            if (dateLength == null || dateLength < 1) errors.add("Date length required (>= 1)")
            if (dateFormat.isNullOrBlank()) errors.add("Date format required")
        }
        return errors
    }

    companion object {
        fun midnightUtcToday(): Long {
            val today = LocalDate.now(ZoneOffset.UTC)
            return today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
}
