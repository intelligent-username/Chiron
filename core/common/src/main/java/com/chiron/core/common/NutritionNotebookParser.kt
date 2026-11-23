package com.chiron.core.common

import java.time.LocalDate
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

data class NotebookFood(val line: Int, val date: LocalDate, val calories: Double, val protein: Double)
data class NotebookWeight(val line: Int, val date: LocalDate, val value: Double)
data class NutritionNotebook(
    val foods: List<NotebookFood>,
    val weights: List<NotebookWeight>,
    val errors: List<String>,
    val ignoredLines: Int
)

/** Reads dated nutrition rows without confusing weekly averages or weigh-ins with food. */
object NutritionNotebookParser {
    private val headerYear = Regex("""\b((?:19|20)\d{2})\s+Counter\b""", RegexOption.IGNORE_CASE)
    private val namedDate = Regex("""^[A-Za-z]+\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:\s*,?\s*\d{4})?$""", RegexOption.IGNORE_CASE)
    private val isoDate = Regex("""^\d{4}-\d{1,2}-\d{1,2}$""")
    private val ordinal = Regex("""(?<=\d)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)
    private val formats = listOf("MMMM d uuuu", "MMM d uuuu", "uuuu-M-d").map {
        java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it)
            .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }

    fun lines(text: String): List<String> = text.removePrefix("\uFEFF")
        .split('|', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }

    fun referenceYear(text: String, fallback: Int): Int =
        headerYear.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: fallback

    fun parse(text: String, fallbackYear: Int = LocalDate.now().year): NutritionNotebook {
        val items = lines(text)
        val year = referenceYear(text, fallbackYear)
        val foods = mutableListOf<NotebookFood>()
        val errors = mutableListOf<String>()
        items.forEachIndexed { index, line ->
            val colon = line.indexOf(':')
            if (colon < 0) return@forEachIndexed
            val dateText = line.substring(0, colon).trim()
            if (!namedDate.matches(dateText) && !isoDate.matches(dateText)) return@forEachIndexed
            val normalized = ordinal.replace(dateText, "").replace(",", "").replace(".", "")
                .replace(Regex("""\s+"""), " ")
            val dated = if (!isoDate.matches(dateText) && !Regex("""\d{4}$""").containsMatchIn(normalized)) "$normalized $year" else normalized
            val date = formats.firstNotNullOfOrNull { format ->
                runCatching { LocalDate.parse(dated, format) }.getOrNull()
            }
            val values = line.substring(colon + 1).split(',').map { it.trim().toDoubleOrNull() }
            if (date == null || values.size != 2 || values.any { it == null || !it.isFinite() || it < 0 }) {
                errors += "Item ${index + 1}: use a valid date followed by calories, protein"
            } else {
                foods += NotebookFood(index + 1, date, values[0]!!, values[1]!!)
            }
        }
        val weights = items.mapIndexedNotNull { index, line ->
            val value = line.toDoubleOrNull() ?: return@mapIndexedNotNull null
            val precededBySeparator = items.getOrNull(index - 1)?.let { previous ->
                previous.length >= 3 && previous.all { it in "—–-_" }
            } == true
            if (!precededBySeparator || !value.isFinite() || value !in 30.0..300.0) return@mapIndexedNotNull null
            val nextFood = foods.firstOrNull { it.line > index + 1 } ?: return@mapIndexedNotNull null
            // Match the established importer: the weigh-in heads the following dated week.
            if (nextFood.line != index + 2) return@mapIndexedNotNull null
            NotebookWeight(index + 1, nextFood.date, value)
        }
        return NutritionNotebook(foods, weights, errors, (items.size - foods.size - weights.size - errors.size).coerceAtLeast(0))
    }

    fun calorieEntry(food: NotebookFood): CalorieEntry = CalorieEntry(
        id = UUID.nameUUIDFromBytes("chiron:nutrition-notebook:${food.date}".toByteArray(Charsets.UTF_8)).toString(),
        date = food.date,
        name = "Daily total",
        caloriesPerServing = food.calories,
        proteinPerServing = food.protein,
        servings = 1.0
    )
}

data class CalorieTextPreview(val entries: List<CalorieEntry>, val errors: List<String>, val ignoredLines: Int)

object CalorieTextImporter {
    fun parse(text: String, referenceYear: Int = LocalDate.now().year): CalorieTextPreview {
        val input = text.trim().removePrefix("\uFEFF").trim()
        require(input.isNotEmpty()) { "Paste a nutrition log or calorie export first" }
        if (input.startsWith("{")) {
            return CalorieTextPreview(CalorieHistory.parse(input).entries, emptyList(), 0)
        }
        val notebook = NutritionNotebookParser.parse(input, referenceYear)
        require(notebook.foods.isNotEmpty() || notebook.errors.isNotEmpty()) {
            "No dated calorie rows found. Example: January 1st: 1340, 53"
        }
        val duplicateDates = notebook.foods.groupBy { it.date }.filterValues { it.size > 1 }.keys
        val errors = notebook.errors + duplicateDates.map { "Multiple daily totals for $it; keep one row for that date" }
        return CalorieTextPreview(notebook.foods.map(NutritionNotebookParser::calorieEntry), errors,
            notebook.ignoredLines + notebook.weights.size)
    }
}
