package com.chiron.core.database.bodyweight

import com.chiron.core.common.UnitConversion
import java.io.BufferedReader
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ParsedWeightRow(
    val weightLbs: Double,
    val timestampUtc: Long,
    val sourceLineNumber: Int
)

data class ImportParseError(
    val lineNumber: Int,
    val excerpt: String,
    val reason: String
)

data class BodyweightParseResult(
    val rows: List<ParsedWeightRow>,
    val errors: List<ImportParseError>,
    val totalLinesSeen: Long
)

object BodyweightFileParser {
    const val REASON_LINE_TOO_SHORT = "LINE_TOO_SHORT"
    const val REASON_NOT_A_NUMBER = "NOT_A_NUMBER"
    const val REASON_IMPLAUSIBLE_VALUE = "IMPLAUSIBLE_VALUE"
    const val REASON_BAD_DATE = "BAD_DATE"
    const val REASON_EMPTY_TOKEN = "EMPTY_TOKEN"

    const val DAY_MS = 86_400_000L
    const val NOON_MS = 43_200_000L
    const val MAX_PLAUSIBLE_LBS = 1500.0

    fun parse(lines: Sequence<String>, config: BodyweightImportConfig): BodyweightParseResult {
        throwOnInvalid(config)
        val all = lines.toList()
        return parseLineList(all, config)
    }

    suspend fun parseStream(reader: BufferedReader, config: BodyweightImportConfig): BodyweightParseResult {
        throwOnInvalid(config)
        val kept = mutableListOf<KeptRow>()
        val errors = mutableListOf<ImportParseError>()
        var lineNo = 0
        var seen = 0L
        var isFirstDataLine = true
        var line = reader.readLine()
        while (line != null) {
            lineNo++
            seen++
            if (lineNo % 1000 == 0) currentCoroutineContext().ensureActive()
            val cleaned = cleanLine(line, lineNo == 1)
            if (lineNo <= config.headerSkipLines) {
                isFirstDataLine = false
                line = reader.readLine()
                continue
            }
            val dataIndex = lineNo - config.headerSkipLines - 1
            if (dataIndex % config.lineStride != 0) {
                line = reader.readLine()
                continue
            }
            handleDataLine(cleaned, lineNo, config, kept, errors)
            isFirstDataLine = false
            line = reader.readLine()
        }
        val rows = stampRows(kept, config)
        return BodyweightParseResult(rows, errors, seen)
    }

    fun safeSlice(line: String, startChar: Int, fieldLength: Int?): String? {
        if (startChar < 0 || startChar >= line.length) return null
        if (fieldLength == null) return line.substring(startChar)
        return line.substring(startChar, minOf(startChar + fieldLength, line.length))
    }

    private data class KeptRow(val lineNumber: Int, val weightLbs: Double, val embeddedDateMs: Long? = null)

    private fun parseLineList(all: List<String>, config: BodyweightImportConfig): BodyweightParseResult {
        val kept = mutableListOf<KeptRow>()
        val errors = mutableListOf<ImportParseError>()
        all.forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            if (lineNo <= config.headerSkipLines) return@forEachIndexed
            val dataIndex = lineNo - config.headerSkipLines - 1
            if (dataIndex % config.lineStride != 0) return@forEachIndexed
            handleDataLine(cleanLine(raw, lineNo == 1), lineNo, config, kept, errors)
        }
        return BodyweightParseResult(stampRows(kept, config), errors, all.size.toLong())
    }

    private fun handleDataLine(
        line: String,
        lineNo: Int,
        config: BodyweightImportConfig,
        kept: MutableList<KeptRow>,
        errors: MutableList<ImportParseError>
    ) {
        val slice = safeSlice(line, config.startChar, config.fieldLength)
        if (slice == null) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_LINE_TOO_SHORT))
            return
        }
        val token = slice.trim().replace(config.decimalSeparator.toString(), ".")
        if (token.isEmpty()) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_EMPTY_TOKEN))
            return
        }
        val rawValue = token.toDoubleOrNull()
            ?: Regex("""\d+(?:[.,]\d+)?""").find(token)?.value?.replace(',', '.')?.toDoubleOrNull()
        if (rawValue == null) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_NOT_A_NUMBER))
            return
        }
        val lbs = if (config.unit == WeightImportUnit.KG) UnitConversion.kgToLbs(rawValue) else rawValue
        if (lbs <= 0 || lbs > MAX_PLAUSIBLE_LBS) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_IMPLAUSIBLE_VALUE))
            return
        }
        if (config.dateStrategy == ImportDateStrategy.EMBEDDED_DATE_COLUMN) {
            val dayMs = parseEmbeddedDay(line, lineNo, config, errors) ?: return
            kept.add(KeptRow(lineNo, lbs, dayMs))
        } else {
            kept.add(KeptRow(lineNo, lbs))
        }
    }

    private fun parseEmbeddedDay(
        line: String,
        lineNo: Int,
        config: BodyweightImportConfig,
        errors: MutableList<ImportParseError>
    ): Long? {
        val ds = config.dateStartChar
        val dl = config.dateLength
        val df = config.dateFormat
        if (ds == null || dl == null || df.isNullOrBlank()) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_BAD_DATE))
            return null
        }
        val slice = safeSlice(line, ds, dl)?.trim()
        if (slice.isNullOrEmpty()) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_BAD_DATE))
            return null
        }
        return runCatching {
            val date = LocalDate.parse(slice, DateTimeFormatter.ofPattern(df))
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_BAD_DATE))
            null
        }
    }

    private fun stampRows(kept: List<KeptRow>, config: BodyweightImportConfig): List<ParsedWeightRow> {
        if (kept.isEmpty()) return emptyList()
        return when (config.dateStrategy) {
            ImportDateStrategy.EMBEDDED_DATE_COLUMN -> kept.map {
                ParsedWeightRow(it.weightLbs, (it.embeddedDateMs ?: 0L) + NOON_MS, it.lineNumber)
            }
            ImportDateStrategy.SINGLE_TIMESTAMP -> {
                val now = System.currentTimeMillis()
                kept.mapIndexed { i, k -> ParsedWeightRow(k.weightLbs, now + i, k.lineNumber) }
            }
            ImportDateStrategy.ONE_PER_DAY_BACKWARDS -> stampBackwards(kept, config)
        }
    }

    private fun stampBackwards(kept: List<KeptRow>, config: BodyweightImportConfig): List<ParsedWeightRow> {
        val step = config.stepDays.toLong() * DAY_MS
        return kept.mapIndexed { i, k ->
            val ageIndex = if (config.fileOrder == ImportFileOrder.OLDEST_FIRST) {
                (kept.size - 1 - i).toLong()
            } else {
                i.toLong()
            }
            val computedTs = (config.anchorDateUtc - ageIndex * step + NOON_MS).coerceAtLeast(DAY_MS)
            ParsedWeightRow(k.weightLbs, computedTs, k.lineNumber)
        }
    }

    private fun cleanLine(raw: String, isFirst: Boolean): String {
        var line = raw.trimEnd('\r')
        if (isFirst && line.startsWith("\uFEFF")) line = line.removePrefix("\uFEFF")
        return line
    }

    private fun excerpt(line: String): String {
        val flat = line.replace("\r", "").replace("\n", "")
        return if (flat.length <= 80) flat else flat.take(80)
    }

    private fun throwOnInvalid(config: BodyweightImportConfig) {
        val errs = config.validate()
        if (errs.isNotEmpty()) throw IllegalArgumentException(errs.joinToString("; "))
    }
}
