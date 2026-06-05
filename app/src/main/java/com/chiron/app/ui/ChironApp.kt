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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.chiron.app.viewmodel.TimerTab
import com.chiron.app.viewmodel.TimerViewModel
import com.chiron.app.ui.volume.VolumeScreen
import com.chiron.app.viewmodel.VolumeViewModel
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
    var prTargetExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var prOpenedFromHistory by rememberSaveable { mutableStateOf(false) }
    var exerciseDetailOpenedFromHistory by rememberSaveable { mutableStateOf(false) }
    var exercisesSearchHasText by remember { mutableStateOf(false) }
    var isVolumeMode by rememberSaveable { mutableStateOf(false) }

    val exercisesState by exercisesViewModel.uiState.collectAsState()
    val historyState by historyViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()
    val volumeViewModel: VolumeViewModel = viewModel(factory = ServiceLocator.volumeViewModelFactory)

    val tabs = NavTab.values()
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()

    val spotifyEnabled by ServiceLocator.userSettingsRepository.spotifyEnabledFlow
        .collectAsState(initial = false)
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(spotifyEnabled) {
        if (!spotifyEnabled) SpotifyManager.disconnect()
    }

    LaunchedEffect(selectedTab) {
        val page = selectedTab.ordinal
        if (pagerState.currentPage != page) {
            pagerState.animateScrollToPage(page)
        }
    }

    // Keep selectedTab in sync with the pager, but only once the pager has settled.
    // Using currentPage here causes a feedback loop when animating from page 0 -> 2:
    // currentPage briefly becomes 1 (Exercises), overwriting selectedTab mid-animation.
    LaunchedEffect(pagerState.settledPage) {
        val settledTab = tabs[pagerState.settledPage]
        if (selectedTab != settledTab) {
            selectedTab = settledTab
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            isVolumeMode -> {
                isVolumeMode = false
                volumeViewModel.setExerciseFilter(null)
            }
            isPrScreenOpen -> {
                isPrScreenOpen = false
                prTargetExerciseId = null
                if (prOpenedFromHistory) {
                    selectedTab = NavTab.HISTORY
                    prOpenedFromHistory = false
                }
            }
            isExerciseDetailOpen -> {
                isExerciseDetailOpen = false
                activeExerciseId = null
                if (exerciseDetailOpenedFromHistory) {
                    selectedTab = NavTab.HISTORY
                    exerciseDetailOpenedFromHistory = false
                }
            }
            isSettingsOpen -> isSettingsOpen = false
            selectedTab == NavTab.HISTORY && historyState.isEditorOpen -> historyViewModel.closeEditor()
            selectedTab == NavTab.EXERCISES && exercisesSearchHasText -> { /* handled by child */ }
            pagerState.currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(0) }
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
                    title = {
                        if (selectedTab == NavTab.HISTORY) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = isVolumeMode,
                                label = "volume_toggle"
                            ) { mode ->
                                Text(
                                    text = if (mode) "Volume" else "History",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) { 
                                        isVolumeMode = !isVolumeMode 
                                        if (isVolumeMode) {
                                            volumeViewModel.setExerciseFilter(null)
                                        }
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = when (selectedTab) { NavTab.EXERCISES -> "Exercises"; NavTab.TIMER -> "Timer"; else -> "" },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        when (selectedTab) {
                            NavTab.EXERCISES -> {
                                IconButton(onClick = { exercisesViewModel.toggleShowArchived() }) {
                                    Icon(Icons.Default.Archive, contentDescription = if (exercisesState.showArchived) "Show active" else "Show archived", tint = if (exercisesState.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    prTargetExerciseId = null
                                    prOpenedFromHistory = false
                                    isPrScreenOpen = true
                                }) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = "Personal Records", tint = PrGold)
                                }
                            }
                            NavTab.TIMER -> IconButton(onClick = { isPresetsOpen = true }) { Icon(Icons.Default.Tune, contentDescription = "Presets") }
                            else -> {
                                if (selectedTab == NavTab.HISTORY && isVolumeMode) {
                                    IconButton(onClick = { volumeViewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                                }
                                IconButton(onClick = { isSettingsOpen = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    if (spotifyEnabled) MiniPlayerBar()
                    BottomNavBar(
                        selectedTab = selectedTab,
                        isVolumeMode = isVolumeMode,
                        onTabSelected = { tab ->
                            if (tab == selectedTab) {
                                // User clicked the currently active tab: pop to root
                                if (tab == NavTab.HISTORY) {
                                    historyViewModel.closeEditor()
                                } else if (tab == NavTab.EXERCISES) {
                                    isExerciseDetailOpen = false
                                    isPrScreenOpen = false
                                    prTargetExerciseId = null
                                }
                            } else {
                                // User switched tabs: close any overlays so the new tab is unobscured
                                isPrScreenOpen = false
                                prTargetExerciseId = null
                                prOpenedFromHistory = false
                                isExerciseDetailOpen = false
                                activeExerciseId = null
                                exerciseDetailOpenedFromHistory = false
                                selectedTab = tab
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !historyState.isEditorOpen,
                    beyondViewportPageCount = tabs.size,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (tabs[page]) {
                        NavTab.HISTORY -> {
                            if (isVolumeMode) {
                                VolumeScreen(
                                    viewModel = volumeViewModel,
                                    displayInKg = historyState.displayInKg,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                HistoryScreen(
                                    viewModel = historyViewModel,
                                    onOpenWorkout = {},
                                    onOpenPrForExercise = { exerciseId ->
                                        prTargetExerciseId = exerciseId
                                        prOpenedFromHistory = true
                                        selectedTab = NavTab.EXERCISES
                                        isPrScreenOpen = true
                                    },
                                    onOpenExerciseDetail = { exerciseId ->
                                        selectedTab = NavTab.EXERCISES
                                        activeExerciseId = exerciseId
                                        isExerciseDetailOpen = true
                                        exerciseDetailOpenedFromHistory = true
                                    }
                                )
                            }
                        }
                        NavTab.EXERCISES -> Box(modifier = Modifier.fillMaxSize()) {
                            ExercisesScreen(
                                viewModel = exercisesViewModel,
                                onOpenDetail = { exId ->
                                    activeExerciseId = exId
                                    isExerciseDetailOpen = true
                                    exerciseDetailOpenedFromHistory = false
                                },
                                onSearchQueryChange = { exercisesSearchHasText = it }
                            )
                            if (isExerciseDetailOpen) {
                                val exercise = exercisesState.exercises.find { it.id == activeExerciseId }
                                    ?: exercisesState.archivedExercises.find { it.id == activeExerciseId }
                                ExerciseDetailScreen(
                                    exercise = exercise,
                                    volumeViewModel = volumeViewModel,
                                    displayInKg = historyState.displayInKg,
                                    onSave = { exercisesViewModel.updateExerciseSuspend(it) },
                                    onDelete = { exercisesViewModel.archiveExercise(it) },
                                    onUnarchive = { exercisesViewModel.unarchiveExercise(it) },
                                    onDeletePermanently = { exercisesViewModel.deleteExercisePermanently(it) },
                                    onOpenPrForExercise = { exerciseId ->
                                        prTargetExerciseId = exerciseId
                                        prOpenedFromHistory = false
                                        isPrScreenOpen = true
                                    },
                                    onClose = {
                                        isExerciseDetailOpen = false
                                        activeExerciseId = null
                                        volumeViewModel.setExerciseFilter(null)
                                        if (exerciseDetailOpenedFromHistory) {
                                            selectedTab = NavTab.HISTORY
                                            exerciseDetailOpenedFromHistory = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        NavTab.TIMER -> TimerScreenHost(viewModel = timerViewModel)
                    }
                }

                if (isPrScreenOpen) {
                    PrScreen(
                        viewModel = exercisesViewModel,
                        displayInKg = historyState.displayInKg,
                        distanceUnit = historyState.distanceUnit,
                        initialExerciseId = prTargetExerciseId,
                        onClose = {
                            isPrScreenOpen = false
                            prTargetExerciseId = null
                            if (prOpenedFromHistory) {
                                selectedTab = NavTab.HISTORY
                                prOpenedFromHistory = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (isPresetsOpen) {
            PresetsSheet(
                presets = timerState.presets,
                currentDuration = timerState.countdownSeconds,
                onSelectPreset = {
                    timerViewModel.selectTab(TimerTab.TIMER)
                    timerViewModel.setCountdownPreset(it)
                    timerViewModel.startCountdown()
                    isPresetsOpen = false
                },
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
