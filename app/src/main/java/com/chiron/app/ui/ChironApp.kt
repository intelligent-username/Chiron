package com.chiron.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.chiron.app.di.ServiceLocator
import com.chiron.app.ui.components.BottomNavBar
import com.chiron.app.spotify.MiniPlayerBar
import com.chiron.app.spotify.SpotifyManager
import com.chiron.app.ui.components.NavTab
import com.chiron.app.ui.exercises.ExerciseDetailScreen
import com.chiron.app.ui.exercises.ExercisesScreen
import com.chiron.app.ui.exercises.PrScreen
import com.chiron.app.ui.history.HistoryScreen
import com.chiron.app.ui.settings.SettingsScreen
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.timer.AddPresetDialog
import com.chiron.app.ui.timer.PresetsSheet
import com.chiron.app.ui.timer.TimerScreenHost
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.TimerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChironApp(
    historyViewModel: HistoryViewModel,
    exercisesViewModel: ExercisesViewModel,
    timerViewModel: TimerViewModel,
    onFinish: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.HISTORY) }
    var activeExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isExerciseDetailOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isPresetsOpen by rememberSaveable { mutableStateOf(false) }
    var showAddPresetDialog by rememberSaveable { mutableStateOf(false) }
    var isPrScreenOpen by rememberSaveable { mutableStateOf(false) }
    var exercisesSearchHasText by remember { mutableStateOf(false) }

    val exercisesState by exercisesViewModel.uiState.collectAsState()
    val historyState by historyViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()

    val tabs = NavTab.values()
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()

    val spotifyEnabled by ServiceLocator.userSettingsRepository.spotifyEnabledFlow
        .collectAsState(initial = false)
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(spotifyEnabled) {
        if (!spotifyEnabled) SpotifyManager.disconnect()
    }

    LaunchedEffect(selectedTab) { pagerState.animateScrollToPage(selectedTab.ordinal) }
    LaunchedEffect(pagerState.currentPage) { selectedTab = tabs[pagerState.currentPage] }

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            isPrScreenOpen -> isPrScreenOpen = false
            isExerciseDetailOpen -> { isExerciseDetailOpen = false; activeExerciseId = null }
            isSettingsOpen -> isSettingsOpen = false
            historyState.isEditorOpen -> historyViewModel.closeEditor()
            selectedTab == NavTab.EXERCISES && exercisesSearchHasText -> { /* handled by child */ }
            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            else -> onFinish()
        }
    }

    val isAppLoading = exercisesState.isLoading
    AnimatedVisibility(visible = isAppLoading, enter = fadeIn(tween(300)), exit = fadeOut(tween(600))) {
        ChironSplashScreen(isLoading = isAppLoading, modifier = Modifier.fillMaxSize())
    }

    if (!isAppLoading && isSettingsOpen) {
        SettingsScreen(
            repository = historyViewModel.getSettingsRepository(),
            onExportData = { ServiceLocator.repository.exportDataSnapshot() },
            onImportData = { uri -> ServiceLocator.repository.importDataFromFile(uri) },
            onBack = { isSettingsOpen = false }
        )
    } else if (!isAppLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = when (selectedTab) { NavTab.HISTORY -> "History"; NavTab.EXERCISES -> "Exercises"; NavTab.TIMER -> "Timer" }) },
                    actions = {
                        when (selectedTab) {
                            NavTab.EXERCISES -> {
                                IconButton(onClick = { exercisesViewModel.toggleShowArchived() }) {
                                    Icon(Icons.Default.Archive, contentDescription = if (exercisesState.showArchived) "Show active" else "Show archived", tint = if (exercisesState.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { isPrScreenOpen = true }) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = "Personal Records", tint = PrGold)
                                }
                            }
                            NavTab.TIMER -> IconButton(onClick = { isPresetsOpen = true }) { Icon(Icons.Default.Tune, contentDescription = "Presets") }
                            else -> IconButton(onClick = { isSettingsOpen = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    if (spotifyEnabled) MiniPlayerBar()
                    BottomNavBar(selectedTab = selectedTab, onTabSelected = { tab -> selectedTab = tab; isExerciseDetailOpen = false })
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                HorizontalPager(state = pagerState, userScrollEnabled = !historyState.isEditorOpen) { page ->
                    when (tabs[page]) {
                        NavTab.HISTORY -> HistoryScreen(viewModel = historyViewModel, onOpenWorkout = {})
                        NavTab.EXERCISES -> Box(modifier = Modifier.fillMaxSize()) {
                            ExercisesScreen(viewModel = exercisesViewModel, onOpenDetail = { exId -> activeExerciseId = exId; isExerciseDetailOpen = true }, onSearchQueryChange = { exercisesSearchHasText = it })
                            if (isExerciseDetailOpen) {
                                ExerciseDetailScreen(
                                    exercise = exercisesState.exercises.find { it.id == activeExerciseId },
                                    onSave = { exercisesViewModel.updateExercise(it) },
                                    onDelete = { exercisesViewModel.archiveExercise(it) },
                                    onClose = { isExerciseDetailOpen = false; activeExerciseId = null },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (isPrScreenOpen) {
                                PrScreen(viewModel = exercisesViewModel, displayInKg = historyState.displayInKg, onClose = { isPrScreenOpen = false }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        NavTab.TIMER -> TimerScreenHost(viewModel = timerViewModel)
                    }
                }
            }
        }

        if (isPresetsOpen) {
            PresetsSheet(
                presets = timerState.presets,
                currentDuration = timerState.countdownSeconds,
                onSelectPreset = { timerViewModel.setCountdownPreset(it); timerViewModel.startCountdown(); isPresetsOpen = false },
                onAddPreset = { showAddPresetDialog = true },
                onDeletePreset = { scope.launch { timerViewModel.deletePreset(it) } },
                onEditPreset = { scope.launch { timerViewModel.deletePreset(it) } },
                onDismiss = { isPresetsOpen = false }
            )
        }
    }

    if (showAddPresetDialog) {
        AddPresetDialog(
            onDismiss = { showAddPresetDialog = false },
            onSave = { label, secs -> scope.launch { timerViewModel.addPreset(label, secs) }; showAddPresetDialog = false }
        )
    }
}
