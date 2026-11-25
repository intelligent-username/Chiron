package com.chiron.calories

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import com.chiron.feature.timer.BodyweightUiState
import com.chiron.feature.timer.CalorieScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Synthetic UI only: never launch MainActivity or obtain a ViewModel/repository.
 * These tests are compiled centrally; device execution is a separate, explicit check.
 */
class CalorieSavedStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val additions = mutableListOf<List<CalorieEntry>>()
    private val replacements = mutableListOf<Pair<List<CalorieEntry>, Set<String>>>()
    private val targets = mutableListOf<CalorieTarget>()
    private val deletes = mutableListOf<String>()

    @Composable
    private fun Screen(history: CalorieHistory = CalorieHistory()) {
        MaterialTheme {
            CalorieScreen(
                history = history,
                weightState = BodyweightUiState(isLoading = false),
                error = null,
                onSaveEntries = { additions.add(it) },
                onSaveDay = { entries, ids -> replacements.add(entries to ids) },
                onDelete = { deletes.add(it) },
                onSaveTarget = { targets.add(it) }
            )
        }
    }

    private fun field(label: String, index: Int = 0): SemanticsNodeInteraction =
        compose.onAllNodes(hasSetTextAction() and hasText(label))[index]

    private fun enter(label: String, value: String, index: Int = 0) {
        field(label, index).performScrollTo().performTextReplacement(value)
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun assertNoWrites() {
        compose.runOnIdle {
            assertTrue(additions.isEmpty())
            assertTrue(replacements.isEmpty())
            assertTrue(targets.isEmpty())
            assertTrue(deletes.isEmpty())
        }
    }

    @Test
    fun addRowsPreserveRawInputOrderAndSelectedDateAcrossRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen() }
        compose.onNodeWithContentDescription("Previous day").performClick()
        val selectedDate = LocalDate.now().minusDays(1)
        click("Log food")
        enter("Name (optional)", "First draft")
        enter("Calories per serving", "1..")
        enter("Protein per serving (g)", "")
        enter("Servings", ".")
        click("+ Add row")
        enter("Name (optional)", "Discarded middle", 1)
        click("+ Add row")
        enter("Name (optional)", "Last draft", 2)
        enter("Calories per serving", "250", 2)
        enter("Protein per serving (g)", "12.5", 2)
        enter("Servings", "2", 2)
        compose.onAllNodesWithText("Remove row")[1].performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onAllNodes(hasSetTextAction() and hasText("Name (optional)")).assertCountEquals(2)
        field("Name (optional)").assert(hasText("First draft"))
        field("Name (optional)", 1).assert(hasText("Last draft"))
        field("Calories per serving").assert(hasText("1.."))
        field("Servings").assert(hasText("."))
        field("Calories per serving", 1).assert(hasText("250"))
        field("Protein per serving (g)", 1).assert(hasText("12.5"))
        field("Servings", 1).assert(hasText("2"))
        compose.onNodeWithText("Log food · $selectedDate").assertExists()
        assertNoWrites()
        // Invalid input must remain editable, and must never be submitted by restoration.
        compose.onNodeWithText("Save").performClick()
        assertNoWrites()
        enter("Calories per serving", "100")
        enter("Protein per serving (g)", "3")
        enter("Servings", "1.5")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(1, additions.size)
            val saved = additions.single()
            assertEquals(listOf("First draft", "Last draft"), saved.map { it.name })
            assertTrue(saved.all { it.date == selectedDate })
            assertEquals(2, saved.map { it.id }.toSet().size)
            assertEquals(150.0, saved[0].calories, 0.0)
            assertEquals(500.0, saved[1].calories, 0.0)
            assertTrue(replacements.isEmpty())
        }
    }

    @Test
    fun editedDayRetainsRemovedOriginalIdAndAddedRowAcrossRestoration() {
        val date = LocalDate.now().minusDays(2)
        val originals = listOf(
            CalorieEntry("original-a", date, "Alpha", 100.0, 4.0, 1.0),
            CalorieEntry("original-b", date, "Beta", 200.0, 8.0, 2.0)
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen(CalorieHistory(entries = originals)) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Edit"))
        click("Edit")
        enter("Calories per serving", "210.5", 1)
        compose.onAllNodesWithText("Remove row")[0].performScrollTo().performClick()
        click("+ Add row")
        enter("Name (optional)", "New food", 1)
        enter("Calories per serving", "50", 1)
        enter("Protein per serving (g)", "2", 1)
        enter("Servings", "3", 1)

        restoration.emulateSavedInstanceStateRestore()

        field("Name (optional)").assert(hasText("Beta"))
        field("Calories per serving").assert(hasText("210.5"))
        field("Name (optional)", 1).assert(hasText("New food"))
        compose.onNodeWithText("Log food · $date").assertExists()
        assertNoWrites()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertTrue(additions.isEmpty())
            assertEquals(1, replacements.size)
            val (saved, originalIds) = replacements.single()
            assertEquals(setOf("original-a", "original-b"), originalIds)
            assertEquals(listOf("Beta", "New food"), saved.map { it.name })
            assertEquals("original-b", saved.first().id)
            assertTrue(saved.last().id !in originalIds)
            assertTrue(saved.all { it.date == date })
            assertEquals(421.0, saved.first().calories, 0.0)
            assertEquals(150.0, saved.last().calories, 0.0)
            assertTrue(deletes.isEmpty())
        }
    }

    @Test
    fun cancelRestoresClosedEditorAndNextOpeningIsFresh() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen() }
        click("Log food")
        enter("Name (optional)", "Cancelled draft")
        click("+ Add row")
        compose.onNodeWithText("Cancel").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Food 1").assertDoesNotExist()
        assertNoWrites()
        click("Log food")
        compose.onAllNodes(hasSetTextAction() and hasText("Name (optional)")).assertCountEquals(1)
        field("Name (optional)").assert(hasText(""))
        compose.onNodeWithText("Cancelled draft").assertDoesNotExist()
        enter("Calories per serving", "5")
        enter("Protein per serving (g)", "0")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(1, additions.size)
            assertEquals("", additions.single().single().name)
            assertEquals(1.0, additions.single().single().servings, 0.0)
            assertTrue(replacements.isEmpty())
        }
    }

    @Test
    fun saveableHostDisposalAndReentryRetainUnfinishedFoodRows() {
        val visible = mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val holder = rememberSaveableStateHolder()
            if (visible.value) {
                holder.SaveableStateProvider("calories") { Screen() }
            }
        }
        click("Log food")
        enter("Name (optional)", "Retained while away")
        enter("Calories per serving", "-")
        click("+ Add row")
        enter("Name (optional)", "Second retained row", 1)
        // Host disposal models navigating to another branch without accessing internal helpers.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { visible.value = true }

        compose.onAllNodes(hasSetTextAction() and hasText("Name (optional)")).assertCountEquals(2)
        field("Name (optional)").assert(hasText("Retained while away"))
        field("Calories per serving").assert(hasText("-"))
        field("Name (optional)", 1).assert(hasText("Second retained row"))
        assertNoWrites()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Food 1").assertDoesNotExist()
        assertNoWrites()
    }

    @Test
    fun targetRawDraftAndEffectiveDateSurviveRestorationAndIncomingHistory() {
        val initialDate = LocalDate.now().plusDays(3)
        val history = mutableStateOf(
            CalorieHistory(targets = listOf(CalorieTarget(initialDate, 2000.0, 100.0)))
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen(history.value) }
        compose.onNodeWithContentDescription("Calorie settings").performClick()
        compose.onNodeWithText("Daily targets").performClick()
        enter("Calories", "2..")
        enter("Protein (g)", "")
        compose.runOnIdle {
            history.value = CalorieHistory(
                targets = listOf(CalorieTarget(initialDate.plusDays(5), 2500.0, 120.0))
            )
        }

        restoration.emulateSavedInstanceStateRestore()

        field("Calories").assert(hasText("2.."))
        field("Protein (g)").assert(hasText(""))
        assertNoWrites()
        enter("Calories", "2100")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(listOf(CalorieTarget(initialDate, 2100.0, null)), targets)
            assertTrue(additions.isEmpty())
            assertTrue(replacements.isEmpty())
        }
    }
}
