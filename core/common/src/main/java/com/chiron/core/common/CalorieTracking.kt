package com.chiron.core.common

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class CalorieEntry(
    val id: String,
    val date: LocalDate,
    val name: String,
    val caloriesPerServing: Double,
    val proteinPerServing: Double,
    val servings: Double
) {
    init {
        require(id.isNotBlank())
        require(caloriesPerServing.isFinite() && caloriesPerServing >= 0)
        require(proteinPerServing.isFinite() && proteinPerServing >= 0)
        require(servings.isFinite() && servings > 0)
        require((caloriesPerServing * servings).isFinite() && (proteinPerServing * servings).isFinite())
    }
    val calories: Double get() = caloriesPerServing * servings
    val protein: Double get() = proteinPerServing * servings
}

data class CalorieTarget(val date: LocalDate, val calories: Double?, val protein: Double?) {
    init {
        require(calories == null || calories.isFinite() && calories > 0)
        require(protein == null || protein.isFinite() && protein > 0)
    }
}

data class CalorieHistory(
    val entries: List<CalorieEntry> = emptyList(),
    val targets: List<CalorieTarget> = emptyList()
) {
    fun targetFor(date: LocalDate): CalorieTarget? = targets.filter { it.date <= date }.maxByOrNull { it.date }

    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("entries", JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().put("id", entry.id).put("date", entry.date.toString())
                    .put("name", entry.name).put("calories", entry.caloriesPerServing)
                    .put("protein", entry.proteinPerServing).put("servings", entry.servings))
            }
        })
        put("targets", JSONArray().apply {
            targets.forEach { target ->
                put(JSONObject().put("date", target.date.toString())
                    .put("calories", target.calories ?: JSONObject.NULL)
                    .put("protein", target.protein ?: JSONObject.NULL))
            }
        })
    }.toString()

    companion object {
        fun parse(text: String): CalorieHistory {
            val json = JSONObject(text)
            require(json.getInt("version") == 1) { "Unsupported calorie history version" }
            val entries = json.getJSONArray("entries")
            val targets = json.getJSONArray("targets")
            val result = CalorieHistory(
                entries = (0 until entries.length()).map {
                    val row = entries.getJSONObject(it)
                    CalorieEntry(row.getString("id"), LocalDate.parse(row.getString("date")),
                        row.getString("name"), row.getDouble("calories"), row.getDouble("protein"),
                        row.getDouble("servings"))
                },
                targets = (0 until targets.length()).map {
                    val row = targets.getJSONObject(it)
                    CalorieTarget(LocalDate.parse(row.getString("date")),
                        if (row.isNull("calories")) null else row.getDouble("calories"),
                        if (row.isNull("protein")) null else row.getDouble("protein"))
                }
            )
            require(result.entries.map { it.id }.distinct().size == result.entries.size) { "Duplicate calorie entry IDs" }
            require(result.targets.map { it.date }.distinct().size == result.targets.size) { "Duplicate target dates" }
            return result
        }
    }
}
