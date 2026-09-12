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
import androidx.compose.runtime.remember
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
                onToggleUnit = { viewModel.setDisplayInKg(!state.displayInKg) },
                onWeekCountChange = viewModel::setWeekCount,
                onPrevWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onLog = viewModel::logWeight,
                onEdit = viewModel::updateEntry,
                onDelete = viewModel::deleteEntry,
                onDeleteRange = viewModel::deleteEntriesInRange,
                onImportClick = onImportClick
            )
        }
    }
}

@Composable
fun BodyweightContent(
    state: BodyweightUiState,
    onToggleUnit: () -> Unit,
    onWeekCountChange: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onLog: (Double) -> Unit,
    onEdit: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteRange: (Long, Long) -> Unit,
    onImportClick: () -> Unit = {}
) {
    val localInKg = state.displayInKg
    val unit = if (localInKg) "kg" else "lbs"

    // Date range label strictly bounded by earliest date
    val weekLabel = remember(state.currentPeriodEnd, state.weekCount, state.earliestDate) {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        val daysSpan = (state.weekCount * 7 - 1).toLong()
        val tentativeStart = state.currentPeriodEnd.minusDays(daysSpan)
        val actualStart = if (state.earliestDate != null && tentativeStart < state.earliestDate) {
            state.earliestDate!!
        } else {
            tentativeStart
        }
        "${actualStart.format(fmt)} - ${state.currentPeriodEnd.format(fmt)}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header controls (Import & Persistent Unit toggle)
        BodyweightTopBar(
            localInKg = localInKg,
            onToggleUnit = onToggleUnit,
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

        // Interactive Line Graph Card (Smooth S-curve, prominent input dots, unbounded scale)
        BodyweightGraphCard(
            state = state,
            localInKg = localInKg,
            unit = unit,
            onWeekCountChange = onWeekCountChange
        )

        Spacer(modifier = Modifier.height(12.dp))

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
}
