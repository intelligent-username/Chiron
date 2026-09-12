package com.chiron.feature.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.feature.history.VolumeContent
import com.chiron.feature.history.VolumeUiState
import com.chiron.feature.history.VolumeViewModel
import java.time.LocalDate

@Composable
fun ExerciseVolumeSection(
    exerciseId: Long,
    volumeViewModel: VolumeViewModel,
    volumeState: VolumeUiState,
    displayInKg: Boolean,
    onOpenWorkoutFromDate: ((Long, LocalDate) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Volume Trend",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        VolumeContent(
            state = volumeState,
            displayInKg = displayInKg,
            onModeChange = volumeViewModel::setMode,
            onWeekCountChange = volumeViewModel::setWeekCount,
            onPrevWeek = volumeViewModel::goToPreviousWeek,
            onNextWeek = volumeViewModel::goToNextWeek,
            onToggleAbridgeGaps = volumeViewModel::toggleAbridgeGaps,
            onPointTap = { point ->
                onOpenWorkoutFromDate?.invoke(exerciseId, point.date)
            },
            scrollable = false
        )
    }
}
