package com.chiron.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chiron.core.ui.components.NavTab
import org.junit.Rule
import org.junit.Test

/** Isolated startup layout tests: no activity, repositories or Spotify connection. */
class StartupLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Startup(persistedName: String) {
        var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
        val pager = rememberPagerState(
            initialPage = NavTab.entries.firstOrNull {
                it.name.equals(selectedName, ignoreCase = true)
            }?.ordinal ?: 0
        ) { NavTab.entries.size }
        var ready by remember { mutableStateOf(selectedName != null) }
        LaunchedEffect(Unit) {
            if (!ready) {
                selectedName = restoreStartupTab(pager, persistedName)
                ready = true
            }
        }
        MaterialTheme {
            if (ready) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
                    Text("Page ${NavTab.entries[page].name}")
                }
            } else {
                StartupLoadingFallback()
            }
        }
    }

    @Test
    fun timerInitializationFinishesBeforePagerHasEverBeenComposed() {
        compose.setContent { Startup("timer") }
        compose.onNodeWithText("Page TIMER").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
    }

    @Test
    fun invalidSavedNameFallsBackWithoutWaitingForLayout() {
        compose.setContent { Startup("unknown") }
        compose.onNodeWithText("Page HISTORY").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
    }

    @Test
    fun restoredSelectionWinsOverStalePreference() {
        var persistedName = "timer"
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Startup(persistedName) }
        compose.onNodeWithText("Page TIMER").assertIsDisplayed()
        compose.runOnIdle { persistedName = "history" }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Page TIMER").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
    }

    @Test
    fun splashUsesOnlyBrandAndHonestLoadingLabel() {
        compose.setContent { MaterialTheme { ChironSplashScreen() } }
        compose.onNodeWithText("Chiron").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.onNodeWithText("Your training, your records").assertDoesNotExist()
        compose.onNodeWithText("Ready").assertDoesNotExist()
    }

    @Test
    fun brandedFallbackDoesNotFlashDuringNormalStartup() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { StartupLoadingFallback() } }
        compose.onNodeWithText("Chiron").assertDoesNotExist()
        compose.onNodeWithText("Loading…").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Chiron").assertIsDisplayed()
        compose.onNodeWithText("Loading…").assertIsDisplayed()
    }
}
