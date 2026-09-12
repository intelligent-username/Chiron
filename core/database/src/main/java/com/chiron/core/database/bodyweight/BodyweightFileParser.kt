package com.chiron.core.database.bodyweight

import com.chiron.core.common.UnitConversion
import java.io.BufferedReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

data class DetectedImportPattern(
    val startRow: Int,
    val stride: Int,
    val unit: WeightImportUnit,
    val stepDays: Int,
    val sampleCount: Int
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

    // Number regex that ignores ordinals (1st, 2nd, 3rd, 4th, 11th) and trailing words
    val WEIGHT_NUMBER_REGEX = Regex(
        """(?<!\w)(\d+(?:[.,]\d+)?)(?!(?:st|nd|rd|th|[a-zA-Z]))""",
        RegexOption.IGNORE_CASE
    )

    private val MONTH_DAY_REGEX = Regex(
        """\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )

    private val DAY_MONTH_REGEX = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\.?\b""",
        RegexOption.IGNORE_CASE
    )

    private val ISO_DATE_REGEX = Regex(
        """\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b"""
    )

    fun splitText(
        rawText: String,
        delimiter: ImportDelimiter = ImportDelimiter.AUTO,
        ignoreBlank: Boolean = true
    ): List<String> {
        if (rawText.isBlank()) return emptyList()

        val sep: String = when (delimiter) {
            ImportDelimiter.PIPES -> "|"
            ImportDelimiter.LINES -> "\n"
            ImportDelimiter.COMMAS -> ","
            ImportDelimiter.TABS -> "\t"
            ImportDelimiter.AUTO -> {
                val pipeCount = rawText.count { it == '|' }
                if (pipeCount >= 2) "|"
                else if (rawText.count { it == '\t' } >= 2) "\t"
                else "\n"
            }
        }

        val tokens = if (sep == "\n") {
            rawText.lineSequence().map { cleanLine(it, false) }.toList()
        } else {
            rawText.split(sep).map { cleanLine(it.trim(), false) }
        }

        return if (ignoreBlank) {
            tokens.filter { it.isNotBlank() }
        } else {
            tokens
        }
    }

    fun parseMonth(name: String): Int? {
        val clean = name.trim().lowercase()
        return when {
            clean.startsWith("jan") -> 1
            clean.startsWith("feb") -> 2
            clean.startsWith("mar") -> 3
            clean.startsWith("apr") -> 4
            clean.startsWith("may") -> 5
            clean.startsWith("jun") -> 6
            clean.startsWith("jul") -> 7
            clean.startsWith("aug") -> 8
            clean.startsWith("sep") -> 9
            clean.startsWith("oct") -> 10
            clean.startsWith("nov") -> 11
            clean.startsWith("dec") -> 12
            else -> null
        }
    }

    fun extractDateFromText(text: String, referenceYear: Int): LocalDate? {
        // 1. ISO format: YYYY-MM-DD
        val isoMatch = ISO_DATE_REGEX.find(text)
        if (isoMatch != null) {
            val y = isoMatch.groupValues[1].toIntOrNull() ?: referenceYear
            val m = isoMatch.groupValues[2].toIntOrNull() ?: 1
            val d = isoMatch.groupValues[3].toIntOrNull() ?: 1
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }

        // 2. Month Day format: January 4th, Jan 4
        val mdMatch = MONTH_DAY_REGEX.find(text)
        if (mdMatch != null) {
            val monthStr = mdMatch.groupValues[1]
            val dayStr = mdMatch.groupValues[2]
            val yearStr = mdMatch.groupValues.getOrNull(3)
            val m = parseMonth(monthStr) ?: 1
            val d = dayStr.toIntOrNull() ?: 1
            val matchedYear = yearStr?.toIntOrNull()
            val colonIdx = text.indexOf(':')
            val yearIdx = if (yearStr != null) text.indexOf(yearStr) else -1
            val colonBeforeYear = colonIdx in 0..yearIdx
            val y = if (matchedYear != null && matchedYear in 2000..2100 && !colonBeforeYear) {
                matchedYear
            } else {
                referenceYear
            }
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }

        // 3. Day Month format: 4th January, 4 Jan
        val dmMatch = DAY_MONTH_REGEX.find(text)
        if (dmMatch != null) {
            val dayStr = dmMatch.groupValues[1]
            val monthStr = dmMatch.groupValues[2]
            val m = parseMonth(monthStr) ?: 1
            val d = dayStr.toIntOrNull() ?: 1
            return runCatching { LocalDate.of(referenceYear, m, d) }.getOrNull()
        }

        return null
    }

    fun detectPattern(items: List<String>): DetectedImportPattern? {
        if (items.size < 4) return null
        val weightCandidates = mutableListOf<Pair<Int, Double>>()
        items.forEachIndexed { idx, item ->
            val trimmed = item.trim()
            val num = trimmed.toDoubleOrNull()
                ?: WEIGHT_NUMBER_REGEX.find(trimmed)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            if (num != null && num in 30.0..300.0) {
                weightCandidates.add(idx to num)
            }
        }
        if (weightCandidates.size < 2) return null

        val firstIdx = weightCandidates.first().first
        for (candStride in 2..30) {
            val matchingCount = weightCandidates.count { (it.first - firstIdx) % candStride == 0 }
            if (matchingCount >= 3 && matchingCount.toDouble() / weightCandidates.size >= 0.5) {
                val avgWeight = weightCandidates.map { it.second }.average()
                val unit = if (avgWeight in 40.0..120.0) WeightImportUnit.KG else WeightImportUnit.LBS
                val stepDays = if (candStride in 7..14) 7 else 1
                return DetectedImportPattern(
                    startRow = firstIdx + 1,
                    stride = candStride,
                    unit = unit,
                    stepDays = stepDays,
                    sampleCount = matchingCount
                )
            }
        }
        return null
    }

    fun parse(lines: Sequence<String>, config: BodyweightImportConfig): BodyweightParseResult {
        throwOnInvalid(config)
        val rawList = lines.toList()
        val needsPipeSplit = (config.delimiter == ImportDelimiter.PIPES || config.delimiter == ImportDelimiter.AUTO) &&
            rawList.any { it.count { c -> c == '|' } >= 2 }
        val all = if (needsPipeSplit) {
            splitText(rawList.joinToString("\n"), config.delimiter)
        } else {
            rawList
        }
        return parseLineList(all, config)
    }

    suspend fun parseStream(reader: BufferedReader, config: BodyweightImportConfig): BodyweightParseResult {
        throwOnInvalid(config)
        val text = reader.readText()
        val items = splitText(text, config.delimiter)
        return parse(items.asSequence(), config)
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
        val refYear = Instant.ofEpochMilli(config.anchorDateUtc).atZone(ZoneOffset.UTC).year
        val headerSkip = config.effectiveHeaderSkipLines

        all.forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            if (lineNo <= headerSkip) return@forEachIndexed
            val dataIndex = lineNo - headerSkip - 1
            if (dataIndex % config.lineStride != 0) return@forEachIndexed

            val cleaned = cleanLine(raw, lineNo == 1)
            var detectedDateMs: Long? = null
            if (config.autoDetectDates) {
                var date = extractDateFromText(cleaned, refYear)
                if (date == null && config.lineStride > 1) {
                    val lookAheadEnd = minOf(all.size - 1, idx + config.lineStride - 1)
                    for (nextIdx in (idx + 1)..lookAheadEnd) {
                        val nextLine = cleanLine(all[nextIdx], false)
                        date = extractDateFromText(nextLine, refYear)
                        if (date != null) break
                    }
                }
                if (date != null) {
                    detectedDateMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
            }

            handleDataLine(cleaned, lineNo, config, kept, errors, detectedDateMs)
        }
        return BodyweightParseResult(stampRows(kept, config), errors, all.size.toLong())
    }

    private fun handleDataLine(
        line: String,
        lineNo: Int,
        config: BodyweightImportConfig,
        kept: MutableList<KeptRow>,
        errors: MutableList<ImportParseError>,
        detectedDateMs: Long? = null
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
            ?: WEIGHT_NUMBER_REGEX.find(token)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        if (rawValue == null) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_NOT_A_NUMBER))
            return
        }
        val lbs = if (config.unit == WeightImportUnit.KG) UnitConversion.kgToLbs(rawValue) else rawValue
        if (lbs < config.minWeightLbs || lbs > config.maxWeightLbs) {
            errors.add(ImportParseError(lineNo, excerpt(line), REASON_IMPLAUSIBLE_VALUE))
            return
        }
        if (config.dateStrategy == ImportDateStrategy.EMBEDDED_DATE_COLUMN) {
            val dayMs = parseEmbeddedDay(line, lineNo, config, errors) ?: return
            kept.add(KeptRow(lineNo, lbs, dayMs))
        } else if (detectedDateMs != null) {
            kept.add(KeptRow(lineNo, lbs, detectedDateMs))
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
            ImportDateStrategy.ONE_PER_DAY_BACKWARDS, ImportDateStrategy.AUTO_DETECT -> stampBackwards(kept, config)
        }
    }

    private fun stampBackwards(kept: List<KeptRow>, config: BodyweightImportConfig): List<ParsedWeightRow> {
        val step = config.stepDays.toLong() * DAY_MS
        return kept.mapIndexed { i, k ->
            val computedTs = if (k.embeddedDateMs != null) {
                k.embeddedDateMs + NOON_MS
            } else {
                val ageIndex = if (config.fileOrder == ImportFileOrder.OLDEST_FIRST) {
                    (kept.size - 1 - i).toLong()
                } else {
                    i.toLong()
                }
                (config.anchorDateUtc - ageIndex * step + NOON_MS).coerceAtLeast(DAY_MS)
            }
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
