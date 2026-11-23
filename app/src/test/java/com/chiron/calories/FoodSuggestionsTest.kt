package com.chiron.calories

import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.foodSuggestions
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSuggestionsTest {
    private fun entry(id: String, name: String, date: String = "2025-01-01",
                      calories: Double = 100.0, protein: Double = 5.0) =
        CalorieEntry(id, LocalDate.parse(date), name, calories, protein, 1.0)

    @Test
    fun matchesTrimmedNamesAndQueriesCaseInsensitively() {
        val history = listOf(entry("1", "  Green APPLE  "), entry("2", "Banana"), entry("3", "  "))
        assertEquals(listOf("Green APPLE"), foodSuggestions(history, "  apple ").map { it.name })
        assertTrue(foodSuggestions(history, "   ").isEmpty())
        assertTrue(foodSuggestions(history, "missing").isEmpty())
    }

    @Test
    fun deduplicatesByNameUsingLatestDateThenGreatestId() {
        val history = listOf(
            entry("z", " OATS ", "2025-01-01", 1.0, 2.0),
            entry("a", "oats", "2025-01-02", 3.0, 4.0),
            entry("b", " Oats ", "2025-01-02", 5.0, 6.0)
        )
        val suggestion = foodSuggestions(history, "oat").single()
        assertEquals("b", suggestion.id)
        assertEquals("Oats", suggestion.name)
        assertEquals(5.0, suggestion.caloriesPerServing, 0.0)
        assertEquals(6.0, suggestion.proteinPerServing, 0.0)
        assertEquals(listOf(suggestion), foodSuggestions(history.reversed(), "OAT"))
    }

    @Test
    fun sortsByNormalizedNameAndDoesNotModifyHistory() {
        val history = mutableListOf(entry("1", " Zebra "), entry("2", "apple"), entry("3", "Apricot"))
        val original = history.toList()
        assertEquals(listOf("apple", "Apricot", "Zebra"),
            foodSuggestions(history, "a").map { it.name })
        assertEquals(original, history)
    }

    @Test
    fun normalizationDoesNotDependOnDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(1, foodSuggestions(listOf(entry("1", "MILK")), "milk").size)
        } finally {
            Locale.setDefault(original)
        }
    }
}
