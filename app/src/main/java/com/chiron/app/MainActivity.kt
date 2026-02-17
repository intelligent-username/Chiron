package com.chiron.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.chiron.app.ui.settings.SettingsScreen

import androidx.activity.enableEdgeToEdge

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ChironTheme {
                var selectedTab by rememberSaveable { mutableStateOf(NavTab.HISTORY) }
                var activeExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
                var isExerciseDetailOpen by rememberSaveable { mutableStateOf(false) }
                var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

                val historyViewModel: HistoryViewModel = viewModel(factory = ServiceLocator.historyViewModelFactory)
                val exercisesViewModel: ExercisesViewModel = viewModel(factory = ServiceLocator.exercisesViewModelFactory)
                val timerViewModel: TimerViewModel = viewModel()

                val exercisesState by exercisesViewModel.uiState.collectAsState()

                val tabs = NavTab.values()
                val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
                val scope = rememberCoroutineScope()

                // Keep pager and bottom nav in sync
                LaunchedEffect(selectedTab) {
                    pagerState.animateScrollToPage(selectedTab.ordinal)
                }
                LaunchedEffect(pagerState.currentPage) {
                    selectedTab = tabs[pagerState.currentPage]
                }

                androidx.activity.compose.BackHandler(enabled = true) {
                    when {
                        isExerciseDetailOpen -> {
                            isExerciseDetailOpen = false
                            activeExerciseId = null
                        }
                        isSettingsOpen -> {
                            isSettingsOpen = false
                        }
                        historyViewModel.uiState.value.isEditorOpen -> {
                            historyViewModel.closeEditor()
                        }
                        pagerState.currentPage > 0 -> {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                        else -> {
                            finish()
                        }
                    }
                }

                if (isSettingsOpen) {
                    SettingsScreen(
                        repository = historyViewModel.getSettingsRepository(),
                        onBack = { isSettingsOpen = false }
                    )
                } else {
                    androidx.compose.material3.Scaffold(
                        topBar = {
                            androidx.compose.material3.TopAppBar(
                                title = { 
                                    androidx.compose.material3.Text(
                                        text = when(selectedTab) {
                                            NavTab.HISTORY -> "History"
                                            NavTab.EXERCISES -> "Exercises"
                                            NavTab.TIMER -> "Timer"
                                        }
                                    ) 
                                },
                                actions = {
                                    androidx.compose.material3.IconButton(onClick = { isSettingsOpen = true }) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings"
                                        )
                                    }
                                }
                            )
                        },
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
                            HorizontalPager(state = pagerState) { page ->
                                when (tabs[page]) {
                                    NavTab.HISTORY -> {
                                        HistoryScreen(
                                            viewModel = historyViewModel,
                                            onOpenWorkout = { } // Not needed anymore, handled internally
                                        )
                                    }
                                    NavTab.EXERCISES -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
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
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    NavTab.TIMER -> {
                                        TimerScreenHost(viewModel = timerViewModel)
                                    }
                                }
                            }



                            // Settings Icon removed from here
                        }
                    }
                }
            }
        }
    }
}
