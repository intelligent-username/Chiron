package com.chiron.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun VolumeScreen(
    viewModel: VolumeViewModel,
    displayInKg: Boolean,
    onPointTap: (VolumePoint) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            VolumeContent(
                state = state,
                displayInKg = displayInKg,
                onModeChange = viewModel::setMode,
                onWeekCountChange = viewModel::setWeekCount,
                onPrevWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onToggleAbridgeGaps = viewModel::toggleAbridgeGaps,
                onPointTap = onPointTap
            )
        }
    }
}
