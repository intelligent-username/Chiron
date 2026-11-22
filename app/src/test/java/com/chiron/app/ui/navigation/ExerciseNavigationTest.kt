package com.chiron.app.ui.navigation

import com.chiron.core.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNavigationTest {
    private fun exercises(vararg ids: Long) = ids.map { Exercise(id = it, name = "Exercise $it") }

    @Test
    fun followsListOrderRatherThanDatabaseIds() {
        assertEquals(
            30L to 10L,
            adjacentExerciseIds(20L, exercises(30L, 20L, 10L), emptyList())
        )
    }

    @Test
    fun firstAndLastDoNotWrap() {
        val list = exercises(1L, 2L, 3L)
        assertEquals(null to 2L, adjacentExerciseIds(1L, list, emptyList()))
        assertEquals(2L to null, adjacentExerciseIds(3L, list, emptyList()))
    }

    @Test
    fun respectsSearchOrderAndBoundaries() {
        val all = exercises(1L, 2L, 3L, 4L)
        val results = exercises(4L, 2L)
        assertEquals(null to 2L, adjacentExerciseIds(4L, all, emptyList(), results))
        assertEquals(4L to null, adjacentExerciseIds(2L, all, emptyList(), results))
    }

    @Test
    fun unrelatedSearchDoesNotDisableDeepLinkNavigation() {
        assertEquals(
            1L to 3L,
            adjacentExerciseIds(2L, exercises(1L, 2L, 3L), emptyList(), exercises(4L))
        )
    }

    @Test
    fun archivedExercisesStayInTheirOwnList() {
        assertEquals(
            7L to 9L,
            adjacentExerciseIds(8L, exercises(1L, 2L), exercises(7L, 8L, 9L))
        )
    }

    @Test
    fun missingEmptyOrSingleExerciseCannotNavigate() {
        assertEquals(null to null, adjacentExerciseIds(null, exercises(1L), emptyList()))
        assertEquals(null to null, adjacentExerciseIds(99L, exercises(1L), exercises(2L)))
        assertEquals(null to null, adjacentExerciseIds(1L, emptyList(), emptyList()))
        assertEquals(null to null, adjacentExerciseIds(1L, exercises(1L), emptyList()))
    }
}
