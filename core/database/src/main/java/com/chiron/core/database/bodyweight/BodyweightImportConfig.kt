package com.chiron.core.database.bodyweight

import java.time.LocalDate
import java.time.ZoneOffset

enum class WeightImportUnit { KG, LBS }

enum class ImportFileOrder { OLDEST_FIRST, NEWEST_FIRST }

enum class ImportDateStrategy { ONE_PER_DAY_BACKWARDS, EMBEDDED_DATE_COLUMN, SINGLE_TIMESTAMP }

enum class ImportDuplicateRule { OVERWRITE_SAME_DAY, SKIP_EXISTING }

data class BodyweightImportConfig(
    val unit: WeightImportUnit = WeightImportUnit.LBS,
    val lineStride: Int = 1,
    val startChar: Int = 0,
    val fieldLength: Int? = null,
    val headerSkipLines: Int = 0,
    val decimalSeparator: Char = '.',
    val fileOrder: ImportFileOrder = ImportFileOrder.OLDEST_FIRST,
    val dateStrategy: ImportDateStrategy = ImportDateStrategy.ONE_PER_DAY_BACKWARDS,
    val anchorDateUtc: Long = midnightUtcToday(),
    val stepDays: Int = 1,
    val dateStartChar: Int? = null,
    val dateLength: Int? = null,
    val dateFormat: String? = null,
    val duplicateRule: ImportDuplicateRule = ImportDuplicateRule.OVERWRITE_SAME_DAY,
    val previewLimit: Int = 20
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (lineStride < 1) errors.add("Lines stride must be >= 1")
        if (startChar < 0) errors.add("Start character must be >= 0")
        if (fieldLength != null && fieldLength < 1) errors.add("Field length must be >= 1")
        if (headerSkipLines < 0) errors.add("Header skip must be >= 0")
        if (decimalSeparator != '.' && decimalSeparator != ',') errors.add("Decimal separator must be . or ,")
        if (stepDays < 1) errors.add("Step days must be >= 1")
        if (anchorDateUtc <= 0) errors.add("Anchor date must be set")
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
