package com.chiron.feature.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.components.WeekNavigator
import java.time.format.DateTimeFormatter

@Composable
fun BodyweightStatsScreen(
    viewModel: BodyweightViewModel,
    displayInKg: Boolean,
    onImportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            BodyweightContent(
                state = state,
                displayInKg = displayInKg,
                onWeekCountChange = viewModel::setWeekCount,
                onPrevWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onLog = viewModel::logWeight,
                onEdit = viewModel::updateEntry,
                onDelete = viewModel::deleteEntry,
                onImportClick = onImportClick
            )
        }
    }
}

@Composable
fun BodyweightContent(
    state: BodyweightUiState,
    displayInKg: Boolean,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onLog: (Double) -> Unit,
    onEdit: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
    onImportClick: () -> Unit = {}
) {
    // Direct kg/lbs toggle in sub-tab (overrides default setting locally)
    var localInKg by rememberSaveable { mutableStateOf(displayInKg) }
    val unit = if (localInKg) "kg" else "lbs"

    val weekLabel = remember(state.currentWeekStart, state.weekCount) {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        if (state.weekCount <= 1) {
            "${state.currentWeekStart.format(fmt)} - ${state.currentWeekStart.plusDays(6).format(fmt)}"
        } else {
            val start = state.currentWeekStart.minusWeeks((state.weekCount - 1).toLong())
            "${start.format(fmt)} - ${state.currentWeekStart.plusDays(6).format(fmt)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header controls (Import & Unit toggle)
        BodyweightTopBar(
            localInKg = localInKg,
            onToggleUnit = { localInKg = !localInKg },
            onImportClick = onImportClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weight Logger Input
        BodyweightLogInput(
            localInKg = localInKg,
            onLog = onLog,
            error = state.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Line Graph Card
        BodyweightGraphCard(
            state = state,
            localInKg = localInKg,
            unit = unit,
            onWeekCountChange = onWeekCountChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Week Navigation Bar
        WeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Numerical Statistics Section
        BodyweightStatsSection(
            stats = state.stats,
            localInKg = localInKg,
            unit = unit
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Historical Logs List
        BodyweightHistoryList(
            entries = state.entries,
            localInKg = localInKg,
            onEdit = onEdit,
            onDelete = onDelete
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}
