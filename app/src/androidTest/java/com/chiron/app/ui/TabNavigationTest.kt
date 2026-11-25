package com.chiron.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.chiron.app.ui.navigation.ChironMainPager
import com.chiron.app.ui.navigation.TabModeContent
import com.chiron.core.ui.components.NavTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Real pager/state boundaries with synthetic content: no user data or ViewModels. */
class TabNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var pager: PagerState
    private lateinit var scope: CoroutineScope

    @Composable
    private fun Host() {
        pager = rememberPagerState { NavTab.entries.size }
        scope = rememberCoroutineScope()
        var statsMode by rememberSaveable { mutableStateOf(true) }
        MaterialTheme {
            Column {
                Row {
                    TextButton(onClick = { pager.requestScrollToPage(0) }) { Text("Volume") }
                    TextButton(onClick = { pager.requestScrollToPage(2) }) { Text("Stats") }
                    TextButton(onClick = { statsMode = !statsMode }) { Text("Toggle Stats") }
                }
                ChironMainPager(
                    pagerState = pager,
                    tabs = NavTab.entries.toTypedArray(),
                    userScrollEnabled = true,
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    val mode = when (tab) {
                        NavTab.HISTORY -> "volume"
                        NavTab.EXERCISES -> "exercises"
                        NavTab.TIMER -> if (statsMode) "stats" else "timer"
                    }
                    TabModeContent(mode) {
                        var draft by rememberSaveable { mutableStateOf("") }
                        Column {
                            Text("Screen $mode")
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                label = { Text("Draft $mode") }
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun repeatedVolumeStatsJumpsRetainBothDrafts() {
        compose.setContent { Host() }
        compose.onNodeWithText("Draft volume").performTextReplacement("Volume position")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Draft stats").performTextReplacement("Unfinished food")
        repeat(12) {
            compose.onNodeWithText("Volume").performClick()
            compose.onNodeWithText("Screen volume").assertIsDisplayed()
            compose.onNodeWithText("Stats").performClick()
            compose.onNodeWithText("Screen stats").assertIsDisplayed()
        }
        compose.onNodeWithText("Draft stats").assertTextEquals("Draft stats", "Unfinished food")
        compose.onNodeWithText("Volume").performClick()
        compose.onNodeWithText("Draft volume").assertTextEquals("Draft volume", "Volume position")
    }

    @Test
    fun modeStateSurvivesFirstToThirdPageDisposal() {
        compose.setContent { Host() }
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Draft stats").performTextReplacement("Keep stats")
        compose.onNodeWithText("Toggle Stats").performClick()
        compose.onNodeWithText("Draft timer").performTextReplacement("Keep timer")
        compose.onNodeWithText("Volume").performClick()
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Toggle Stats").performClick()
        compose.onNodeWithText("Draft stats").assertTextEquals("Draft stats", "Keep stats")
        compose.onNodeWithText("Toggle Stats").performClick()
        compose.onNodeWithText("Draft timer").assertTextEquals("Draft timer", "Keep timer")
    }

    @Test
    fun directNavigationCanInterruptAnInFlightTwoPageAnimation() {
        compose.setContent { Host() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scope.launch { pager.animateScrollToPage(2) } }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            pager.requestScrollToPage(0)
            pager.requestScrollToPage(2)
            pager.requestScrollToPage(0)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Screen volume").assertIsDisplayed()
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Screen stats").assertIsDisplayed()
    }

    @Test
    fun statsDraftAndInactiveVolumeStateSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        compose.onNodeWithText("Draft volume").performTextReplacement("Volume saved")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Draft stats").performTextReplacement("Food saved")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Draft stats").assertTextEquals("Draft stats", "Food saved")
        compose.onNodeWithText("Volume").performClick()
        compose.onNodeWithText("Draft volume").assertTextEquals("Draft volume", "Volume saved")
    }

    @Test
    fun rapidModeABAHasOneLiveDraftAndRestoresBothModes() {
        val restoration = StateRestorationTester(compose)
        var toggleMode: () -> Unit = {}
        var liveOwners = 0
        var peakOwners = 0
        restoration.setContent {
            var modeA by rememberSaveable { mutableStateOf(true) }
            toggleMode = { modeA = !modeA }
            MaterialTheme {
                val mode = if (modeA) "A" else "B"
                TabModeContent(mode) {
                    DisposableEffect(mode) {
                        liveOwners++
                        peakOwners = maxOf(peakOwners, liveOwners)
                        onDispose { liveOwners-- }
                    }
                    var draft by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Draft $mode") }
                    )
                }
            }
        }
        compose.onNodeWithText("Draft A").performTextReplacement("A unfinished")
        compose.runOnIdle { toggleMode() }
        compose.onNodeWithText("Draft B").performTextReplacement("B unfinished")
        compose.runOnIdle { toggleMode() }

        compose.mainClock.autoAdvance = false
        // Rapid A/B/A changes must not require waiting for any animation to finish.
        // The frozen clock deliberately permits coalescing; verify the settled owner
        // and both saved drafts after enabling frame delivery again.
        repeat(6) {
            compose.runOnIdle { toggleMode() }
            compose.runOnIdle { toggleMode() }
        }
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Draft A").assertTextEquals("Draft A", "A unfinished")
        compose.runOnIdle { toggleMode() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Draft B").assertTextEquals("Draft B", "B unfinished")
        compose.runOnIdle {
            assertEquals(1, liveOwners)
            assertEquals(1, peakOwners)
        }
        compose.runOnIdle { toggleMode() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Draft A").assertTextEquals("Draft A", "A unfinished")
        compose.runOnIdle { toggleMode() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Draft B").assertTextEquals("Draft B", "B unfinished")
        compose.runOnIdle {
            assertEquals(1, liveOwners)
            assertEquals(1, peakOwners)
        }
    }
}
