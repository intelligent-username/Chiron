package com.chiron.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiron.app.di.ServiceLocator
import com.chiron.app.ui.components.BottomNavBar
import com.chiron.app.ui.components.NavTab
import com.chiron.app.ui.exercises.ExerciseDetailScreen
import com.chiron.app.ui.exercises.ExercisesScreen
import com.chiron.app.ui.history.HistoryScreen
import com.chiron.app.ui.theme.ChironTheme
import com.chiron.app.ui.timer.TimerScreenHost
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.TimerViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPagerApi::class)
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChironTheme {
                var selectedTab by rememberSaveable { mutableStateOf(NavTab.HISTORY) }
                var activeExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
                var isExerciseDetailOpen by rememberSaveable { mutableStateOf(false) }

                val historyViewModel: HistoryViewModel = viewModel(factory = ServiceLocator.historyViewModelFactory)
                val exercisesViewModel: ExercisesViewModel = viewModel(factory = ServiceLocator.exercisesViewModelFactory)
                val timerViewModel: TimerViewModel = viewModel()

                val exercisesState by exercisesViewModel.uiState.collectAsState()

                val tabs = NavTab.values()
                val pagerState = rememberPagerState(initialPage = selectedTab.ordinal)

                // Keep pager and bottom nav in sync
                LaunchedEffect(selectedTab) {
                    pagerState.animateScrollToPage(selectedTab.ordinal)
                }
                LaunchedEffect(pagerState.currentPage) {
                    selectedTab = tabs[pagerState.currentPage]
                }

                androidx.compose.material3.Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                isExerciseDetailOpen = false
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        HorizontalPager(state = pagerState, count = tabs.size) { page ->
                            when (tabs[page]) {
                                NavTab.HISTORY -> {
                                    HistoryScreen(
                                        viewModel = historyViewModel,
                                        onOpenWorkout = { } // Not needed anymore, handled internally
                                    )
                                }
                                NavTab.EXERCISES -> {
                                    ExercisesScreen(
                                        viewModel = exercisesViewModel,
                                        onOpenDetail = { exId ->
                                            activeExerciseId = exId
                                            isExerciseDetailOpen = true
                                        }
                                    )
                                    if (isExerciseDetailOpen) {
                                        val exercise = exercisesState.exercises.find { it.id == activeExerciseId }
                                        ExerciseDetailScreen(
                                            exercise = exercise,
                                            onSave = { updated -> exercisesViewModel.updateExercise(updated) },
                                            onDelete = { exerciseId -> 
                                                exercisesViewModel.archiveExercise(exerciseId)
                                            },
                                            onClose = {
                                                isExerciseDetailOpen = false
                                                activeExerciseId = null
                                            }
                                        )
                                    }
                                }
                                NavTab.TIMER -> {
                                    TimerScreenHost(viewModel = timerViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
