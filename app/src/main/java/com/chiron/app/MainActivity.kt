package com.chiron.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiron.app.di.ServiceLocator
import com.chiron.app.ui.components.BottomNavBar
import com.chiron.app.ui.components.NavTab
import com.chiron.app.ui.exercises.ExerciseDetailScreen
import com.chiron.app.ui.exercises.ExercisesScreen
import com.chiron.app.ui.exercises.PrScreen
import com.chiron.app.ui.history.HistoryScreen
import com.chiron.app.ui.settings.SettingsScreen
import com.chiron.app.ui.theme.ChironTheme
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.timer.AddPresetDialog
import com.chiron.app.ui.timer.PresetsSheet
import com.chiron.app.ui.timer.TimerScreenHost
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.TimerViewModel
import kotlinx.coroutines.launch

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
                var isPresetsOpen by rememberSaveable { mutableStateOf(false) }
                var showAddPresetDialog by rememberSaveable { mutableStateOf(false) }
                var isPrScreenOpen by rememberSaveable { mutableStateOf(false) }

                val historyViewModel: HistoryViewModel = viewModel(factory = ServiceLocator.historyViewModelFactory)
                val exercisesViewModel: ExercisesViewModel = viewModel(factory = ServiceLocator.exercisesViewModelFactory)
                val timerViewModel: TimerViewModel = viewModel(factory = ServiceLocator.timerViewModelFactory)

                val exercisesState by exercisesViewModel.uiState.collectAsState()
                val historyState by historyViewModel.uiState.collectAsState()

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
                        isPrScreenOpen -> {
                            isPrScreenOpen = false
                        }
                        isExerciseDetailOpen -> {
                            isExerciseDetailOpen = false
                            activeExerciseId = null
                        }
                        isSettingsOpen -> {
                            isSettingsOpen = false
                        }
                        historyState.isEditorOpen -> {
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
                                    when (selectedTab) {
                                        NavTab.EXERCISES -> {
                                            // Archive toggle
                                            androidx.compose.material3.IconButton(onClick = { exercisesViewModel.toggleShowArchived() }) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Default.Archive,
                                                    contentDescription = if (exercisesState.showArchived) "Show active exercises" else "Show archived exercises",
                                                    tint = if (exercisesState.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            // PR trophy button
                                            androidx.compose.material3.IconButton(onClick = { isPrScreenOpen = true }) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Default.EmojiEvents,
                                                    contentDescription = "Personal Records",
                                                    tint = PrGold
                                                )
                                            }
                                        }
                                        NavTab.TIMER -> {
                                            androidx.compose.material3.IconButton(onClick = { isPresetsOpen = true }) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "Presets"
                                                )
                                            }
                                        }
                                        else -> {
                                            androidx.compose.material3.IconButton(onClick = { isSettingsOpen = true }) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Settings"
                                                )
                                            }
                                        }
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
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !historyState.isEditorOpen
                            ) { page ->
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
                                            if (isPrScreenOpen) {
                                                PrScreen(
                                                    viewModel = exercisesViewModel,
                                                    displayInKg = historyState.displayInKg,
                                                    onClose = { isPrScreenOpen = false },
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


                        }
                    }

                    if (isPresetsOpen) {
                        val timerState by timerViewModel.uiState.collectAsState()
                        PresetsSheet(
                            presets = timerState.presets,
                            currentDuration = timerState.countdownSeconds,
                            onSelectPreset = { seconds ->
                                timerViewModel.setCountdownPreset(seconds)
                                timerViewModel.startCountdown()
                                isPresetsOpen = false
                            },
                            onAddPreset = { showAddPresetDialog = true },
                            onDeletePreset = { preset ->
                                scope.launch {
                                    timerViewModel.deletePreset(preset)
                                }
                            },
                            onEditPreset = { preset ->
                                scope.launch {
                                    timerViewModel.deletePreset(preset)
                                }
                            },
                            onDismiss = { isPresetsOpen = false }
                        )
                    }
                }

                if (showAddPresetDialog) {
                    AddPresetDialog(
                        onDismiss = { showAddPresetDialog = false },
                        onSave = { label, durationSeconds ->
                            scope.launch {
                                timerViewModel.addPreset(label, durationSeconds)
                            }
                            showAddPresetDialog = false
                        }
                    )
                }
            }
        }
    }
}
