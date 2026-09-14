package com.chiron.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiron.app.di.ServiceLocator
import com.chiron.app.ui.dialogs.ChironDialogHost
import com.chiron.app.ui.navigation.BottomBarContainer
import com.chiron.app.ui.navigation.ChironTabPager
import com.chiron.app.ui.settings.SettingsScreen
import com.chiron.app.ui.topbar.ChironTopBar
import com.chiron.core.spotify.SpotifyManager
import com.chiron.core.ui.components.NavTab
import com.chiron.feature.exercises.ExercisesViewModel
import com.chiron.feature.goals.GoalsViewModel
import com.chiron.feature.history.HistoryViewModel
import com.chiron.feature.history.VolumeViewModel
import com.chiron.feature.timer.BodyweightViewModel
import com.chiron.feature.timer.TimerTab
import com.chiron.feature.timer.TimerViewModel
import kotlinx.coroutines.launch

@Composable
fun ChironApp(
    historyViewModel: HistoryViewModel,
    exercisesViewModel: ExercisesViewModel,
    timerViewModel: TimerViewModel,
    onFinish: () -> Unit
) {
    var activeExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isExerciseDetailOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isPresetsOpen by rememberSaveable { mutableStateOf(false) }
    var showAddPresetDialog by rememberSaveable { mutableStateOf(false) }
    var isPrScreenOpen by rememberSaveable { mutableStateOf(false) }
    var prTargetExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var prOpenedFromHistory by rememberSaveable { mutableStateOf(false) }
    var prReturnExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exerciseDetailOpenedFromHistory by rememberSaveable { mutableStateOf(false) }
    var exercisesSearchHasText by remember { mutableStateOf(false) }
    var isVolumeMode by rememberSaveable { mutableStateOf(false) }
    var isGoalsMode by rememberSaveable { mutableStateOf(false) }
    var isBodyweightMode by rememberSaveable { mutableStateOf(false) }
    var showBodyweightImportDialog by rememberSaveable { mutableStateOf(false) }

    val exercisesState by exercisesViewModel.uiState.collectAsState()
    val historyState by historyViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()
    val volumeViewModel: VolumeViewModel = viewModel(factory = ServiceLocator.volumeViewModelFactory)
    val goalsViewModel: GoalsViewModel = viewModel(factory = ServiceLocator.goalsViewModelFactory)
    val bodyweightViewModel: BodyweightViewModel = viewModel(factory = ServiceLocator.bodyweightViewModelFactory)

    val tabs = NavTab.entries.toTypedArray()
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage]

    val userSettingsRepository = remember { ServiceLocator.userSettingsRepository }
    val savedTab by userSettingsRepository.currentTabFlow.collectAsState(initial = "history")

    LaunchedEffect(savedTab) {
        val targetPage = tabs.indexOfFirst { it.name.lowercase() == savedTab }.coerceAtLeast(0)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val tabName = tabs[pagerState.currentPage].name.lowercase()
        userSettingsRepository.setCurrentTab(tabName)
    }

    val spotifyEnabled by ServiceLocator.userSettingsRepository.spotifyEnabledFlow
        .collectAsState(initial = false)

    LaunchedEffect(spotifyEnabled) {
        if (!spotifyEnabled) SpotifyManager.disconnect()
    }

    BackHandler(enabled = true) {
        when {
            isBodyweightMode -> isBodyweightMode = false
            isGoalsMode -> {
                isGoalsMode = false
                goalsViewModel.closeDetail()
            }
            isVolumeMode -> {
                isVolumeMode = false
                volumeViewModel.setExerciseFilter(null)
            }
            isPrScreenOpen -> {
                isPrScreenOpen = false
                prTargetExerciseId = null
                if (prOpenedFromHistory) {
                    scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                    prOpenedFromHistory = false
                }
            }
            isExerciseDetailOpen -> {
                isExerciseDetailOpen = false
                activeExerciseId = null
                if (exerciseDetailOpenedFromHistory) {
                    scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                    exerciseDetailOpenedFromHistory = false
                }
            }
            isSettingsOpen -> isSettingsOpen = false
            selectedTab == NavTab.HISTORY && historyState.isEditorOpen -> {
                val returnExerciseId = prReturnExerciseId
                historyViewModel.closeEditor()
                if (returnExerciseId != null) {
                    prReturnExerciseId = null
                    prTargetExerciseId = returnExerciseId
                    prOpenedFromHistory = false
                    isPrScreenOpen = true
                }
            }
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
                ChironTopBar(
                    selectedTab = selectedTab,
                    isVolumeMode = isVolumeMode,
                    onToggleVolumeMode = {
                        isVolumeMode = !isVolumeMode
                        if (isVolumeMode) {
                            volumeViewModel.setExerciseFilter(null)
                        }
                    },
                    isGoalsMode = isGoalsMode,
                    onToggleGoalsMode = { isGoalsMode = !isGoalsMode },
                    isBodyweightMode = isBodyweightMode,
                    onToggleBodyweightMode = { isBodyweightMode = !isBodyweightMode },
                    showArchivedExercises = exercisesState.showArchived,
                    onToggleShowArchived = { exercisesViewModel.toggleShowArchived() },
                    onOpenPrScreen = {
                        prTargetExerciseId = null
                        prOpenedFromHistory = false
                        prReturnExerciseId = null
                        isPrScreenOpen = true
                    },
                    onRefreshBodyweight = { bodyweightViewModel.refresh() },
                    onOpenPresets = { isPresetsOpen = true },
                    onRefreshVolume = { volumeViewModel.refresh() },
                    onOpenSettings = { isSettingsOpen = true }
                )
            },
            bottomBar = {
                BottomBarContainer(
                    selectedTab = selectedTab,
                    selectedTabFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    isVolumeMode = isVolumeMode,
                    isGoalsMode = isGoalsMode,
                    isBodyweightMode = isBodyweightMode,
                    spotifyEnabled = spotifyEnabled,
                    onTabSelected = { tab ->
                        if (tab == selectedTab) {
                            when (tab) {
                                NavTab.HISTORY -> historyViewModel.closeEditor()
                                NavTab.EXERCISES -> {
                                    isExerciseDetailOpen = false
                                    isPrScreenOpen = false
                                    prTargetExerciseId = null
                                }
                                NavTab.TIMER -> isBodyweightMode = !isBodyweightMode
                            }
                        } else {
                            if (selectedTab == NavTab.EXERCISES) {
                                isGoalsMode = false
                                goalsViewModel.closeDetail()
                            }
                            isPrScreenOpen = false
                            prTargetExerciseId = null
                            prOpenedFromHistory = false
                            isExerciseDetailOpen = false
                            activeExerciseId = null
                            exerciseDetailOpenedFromHistory = false
                            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                ChironTabPager(
                    pagerState = pagerState,
                    tabs = tabs,
                    isEditorOpen = historyState.isEditorOpen,
                    isVolumeMode = isVolumeMode,
                    volumeViewModel = volumeViewModel,
                    displayInKg = historyState.displayInKg,
                    historyViewModel = historyViewModel,
                    onOpenVolumePoint = { date ->
                        historyViewModel.openLastWorkoutOnDate(date) {
                            isVolumeMode = false
                        }
                    },
                    onOpenPrFromHistory = { exerciseId ->
                        prTargetExerciseId = exerciseId
                        prOpenedFromHistory = true
                        prReturnExerciseId = null
                        scope.launch { pagerState.scrollToPage(NavTab.EXERCISES.ordinal) }
                        isPrScreenOpen = true
                    },
                    onOpenExerciseDetailFromHistory = { exerciseId ->
                        scope.launch { pagerState.scrollToPage(NavTab.EXERCISES.ordinal) }
                        activeExerciseId = exerciseId
                        isExerciseDetailOpen = true
                        exerciseDetailOpenedFromHistory = true
                    },
                    onOpenSetInWorkout = { setId ->
                        historyViewModel.openWorkoutFromPr(setId)
                    },
                    isGoalsMode = isGoalsMode,
                    goalsViewModel = goalsViewModel,
                    exercisesViewModel = exercisesViewModel,
                    onOpenExerciseDetail = { exId ->
                        activeExerciseId = exId
                        isExerciseDetailOpen = true
                        exerciseDetailOpenedFromHistory = false
                    },
                    onExercisesSearchQueryChange = { exercisesSearchHasText = it },
                    isExerciseDetailOpen = isExerciseDetailOpen,
                    activeExerciseId = activeExerciseId,
                    exercises = exercisesState.exercises,
                    archivedExercises = exercisesState.archivedExercises,
                    onExerciseSave = { exercisesViewModel.updateExerciseSuspend(it) },
                    onExerciseDelete = { exercisesViewModel.archiveExercise(it) },
                    onExerciseUnarchive = { exercisesViewModel.unarchiveExercise(it) },
                    onExerciseDeletePermanently = { exercisesViewModel.deleteExercisePermanently(it) },
                    onExerciseOpenPr = { exerciseId ->
                        prTargetExerciseId = exerciseId
                        prOpenedFromHistory = false
                        prReturnExerciseId = null
                        isPrScreenOpen = true
                    },
                    onExerciseOpenWorkoutFromDate = { exerciseId, date ->
                        isExerciseDetailOpen = false
                        activeExerciseId = null
                        volumeViewModel.setExerciseFilter(null)
                        isVolumeMode = false
                        scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                        historyViewModel.openWorkoutFromDate(exerciseId, date)
                    },
                    onExerciseDetailClose = {
                        isExerciseDetailOpen = false
                        activeExerciseId = null
                        volumeViewModel.setExerciseFilter(null)
                        if (exerciseDetailOpenedFromHistory) {
                            scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                            exerciseDetailOpenedFromHistory = false
                        }
                    },
                    isBodyweightMode = isBodyweightMode,
                    bodyweightViewModel = bodyweightViewModel,
                    onOpenBodyweightImport = { showBodyweightImportDialog = true },
                    timerViewModel = timerViewModel
                )

                ChironDialogHost(
                    isPrScreenOpen = isPrScreenOpen,
                    prTargetExerciseId = prTargetExerciseId,
                    exercisesViewModel = exercisesViewModel,
                    displayInKg = historyState.displayInKg,
                    distanceUnit = historyState.distanceUnit,
                    onClosePrScreen = {
                        isPrScreenOpen = false
                        prTargetExerciseId = null
                        if (prOpenedFromHistory) {
                            scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                            prOpenedFromHistory = false
                        }
                    },
                    onOpenWorkoutFromPr = { exerciseId, setId ->
                        prReturnExerciseId = exerciseId
                        isPrScreenOpen = false
                        prTargetExerciseId = null
                        prOpenedFromHistory = false
                        scope.launch { pagerState.scrollToPage(NavTab.HISTORY.ordinal) }
                        historyViewModel.openWorkoutFromPr(setId)
                    },
                    isPresetsOpen = isPresetsOpen,
                    presets = timerState.presets,
                    countdownSeconds = timerState.countdownSeconds,
                    onSelectPreset = {
                        timerViewModel.selectTab(TimerTab.TIMER)
                        timerViewModel.setCountdownPreset(it)
                        timerViewModel.startCountdown()
                        isPresetsOpen = false
                    },
                    onOpenAddPresetDialog = { showAddPresetDialog = true },
                    onDeletePreset = { scope.launch { timerViewModel.deletePreset(it) } },
                    onEditPreset = { scope.launch { timerViewModel.deletePreset(it) } },
                    onDismissPresets = { isPresetsOpen = false },
                    showAddPresetDialog = showAddPresetDialog,
                    onDismissAddPreset = { showAddPresetDialog = false },
                    onSaveAddPreset = { label, secs ->
                        scope.launch { timerViewModel.addPreset(label, secs) }
                        showAddPresetDialog = false
                    },
                    showBodyweightImportDialog = showBodyweightImportDialog,
                    onDismissBodyweightImport = { showBodyweightImportDialog = false },
                    onConfirmBodyweightImport = { lines, config ->
                        bodyweightViewModel.importWeightsFromLines(lines, config) { _ -> }
                        showBodyweightImportDialog = false
                    }
                )
            }
        }
    }
}
