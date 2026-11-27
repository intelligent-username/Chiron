package com.chiron.feature.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.components.WeekNavigator
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.feature.timer.BodyweightViewModel
import com.chiron.feature.timer.CalorieScreen
import com.chiron.feature.timer.BodyweightContent
import java.time.format.DateTimeFormatter

@Composable
fun BodyweightStatsScreen(
    viewModel: BodyweightViewModel,
    onImportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.checkDayRollover()
    }
    val state by viewModel.uiState.collectAsState()
    val calories by viewModel.calorieHistory.collectAsState()
    val calorieError by viewModel.calorieError.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val displayedTab = selectedTab.coerceIn(0, 1)
    val subtabStates = rememberSaveableStateHolder()
    val chartMotion = remember { SharedStatsChartMotion() }
    val chartColor by animateColorAsState(
        targetValue = if (displayedTab == 0) ElectricBlue else Color(0xFFFFA726),
        animationSpec = tween(180),
        label = "statsChartColor"
    )
    val focus = LocalFocusManager.current
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = displayedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    listOf("Weight", "Calories").forEachIndexed { index, title ->
                        val tabColor by animateColorAsState(
                            targetValue = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (displayedTab == index) 1f else 0.55f
                            ),
                            animationSpec = tween(180),
                            label = "statsTabColor"
                        )
                        Tab(selected = displayedTab == index, onClick = {
                            if (displayedTab != index) {
                                focus.clearFocus(force = true)
                                selectedTab = index
                            }
                        }, text = { Text(title, color = tabColor) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                  CompositionLocalProvider(
                      LocalStatsChartMotion provides chartMotion,
                      LocalStatsChartMode provides displayedTab,
                      LocalStatsChartColor provides chartColor
                  ) {
                  key(displayedTab) {
                    val tab = displayedTab
                    subtabStates.SaveableStateProvider(if (tab == 1) "calories" else "weight") {
                    if (tab == 1) {
                        CalorieScreen(
                            history = calories,
                            weightState = state,
                            error = calorieError,
                            onSaveEntries = viewModel::saveCalorieEntries,
                            onSaveDay = viewModel::saveCalorieDay,
                            onDelete = viewModel::deleteCalorieEntry,
                            onSaveTarget = viewModel::saveCalorieTarget,
                            graphModifier = Modifier
                        )
                    } else BodyweightContent(
                        state = state,
                        calorieHistory = calories,
                        onToggleUnit = { viewModel.setDisplayInKg(!state.displayInKg) },
                        onWeekCountChange = viewModel::setWeekCount,
                        onPrevWeek = viewModel::goToPreviousWeek,
                        onNextWeek = viewModel::goToNextWeek,
                        onLog = { weight, timestamp -> viewModel.logWeight(weight, timestamp) },
                        onEdit = { id, weight, timestamp -> viewModel.updateEntry(id, weight, timestamp) },
                        onDelete = viewModel::deleteEntry,
                        onDeleteRange = viewModel::deleteEntriesInRange,
                        onImportClick = onImportClick,
                        graphModifier = Modifier
                    )
                    }
                  }
                  }
                }
            }
        }
    }
}

@Composable
fun BodyweightContent(
    state: BodyweightUiState,
    calorieHistory: com.chiron.core.common.CalorieHistory,
    onToggleUnit: () -> Unit,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onLog: (Double, Long) -> Unit,
    onEdit: (Long, Double, Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteRange: (Long, Long) -> Unit,
    onImportClick: () -> Unit = {},
    graphModifier: Modifier = Modifier
) {
    val localInKg = state.displayInKg
    val unit = if (localInKg) "kg" else "lbs"
    var showTransfer by remember { mutableStateOf(false) }

    val weekLabel = remember(state.currentPeriodEnd, state.weekCount) {
        val daysSpan = (state.weekCount * 7 - 1).toLong()
        val start = state.currentPeriodEnd.minusDays(daysSpan)
        "${start.format(MonthDayFormatter)} - ${state.currentPeriodEnd.format(MonthDayFormatter)}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Column(Modifier.heightIn(min = StatsChartGeometry.InputAreaHeight)) {
        // Header controls (import/export hub & persistent unit toggle)
        BodyweightTopBar(
            localInKg = localInKg,
            onToggleUnit = onToggleUnit,
            onTransferClick = { showTransfer = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weight Logger Input
        BodyweightLogInput(
            localInKg = localInKg,
            onLog = onLog,
            error = state.error
        )

        Spacer(modifier = Modifier.height(16.dp))
        }

        // Interactive Line Graph Card (Smooth S-curve, prominent input dots, unbounded scale)
        BodyweightGraphCard(
            state = state,
            localInKg = localInKg,
            unit = unit,
            onWeekCountChange = onWeekCountChange,
            modifier = graphModifier,
            calorieHistory = calorieHistory
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Week/Period Navigation Bar (Strict earliest input date boundary)
        WeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Polished Numerical Statistics Dashboard Section
        BodyweightStatsSection(
            stats = state.stats,
            entries = state.entries,
            localInKg = localInKg,
            unit = unit
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Historical Logs List with 5-50 Row Pagination and Date Range Mass-Delete
        BodyweightHistoryList(
            entries = state.entries,
            localInKg = localInKg,
            onEdit = onEdit,
            onDelete = onDelete,
            onDeleteRange = onDeleteRange
        )

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showTransfer) {
        BodyweightTransferDialog(
            entries = state.entries,
            localInKg = localInKg,
            onDismiss = { showTransfer = false },
            onOpenImport = { showTransfer = false; onImportClick() }
        )
    }
}

private val MonthDayFormatter = DateTimeFormatter.ofPattern("MMM d")
