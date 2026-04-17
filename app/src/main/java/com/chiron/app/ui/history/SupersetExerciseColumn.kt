package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.components.SetPill
import com.chiron.app.viewmodel.HistoryViewModel

/**
 * A single exercise column inside a [SupersetCard].
 *
 * Shows the exercise icon, name, and its set pills in a compact vertical layout.
 * Supports the press-and-hold last-session preview via [LastSessionPreviewButton].
 */
@Composable
fun SupersetExerciseColumn(
    entry: ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    workoutId: Long,
    modifier: Modifier = Modifier,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onOpenPrForExercise: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit
) {
    val sets by viewModel.getSetsForEntry(entry.id)
        .collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var isPreviewingLastSession by remember { mutableStateOf(false) }
    var lastSessionPreview by remember { mutableStateOf<ChironRepository.LastSessionPreview?>(null) }
    val hasHistory = lastSessionPreview != null

    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }
    LaunchedEffect(entry.exerciseId, workoutId) {
        lastSessionPreview = viewModel.getLastSessionPreview(entry.exerciseId, workoutId)
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isPreviewingLastSession)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else
                    Color.Transparent
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = exercise?.name ?: "Loading...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable { onOpenExerciseDetail(entry.exerciseId) }
        )

        ExerciseAsyncIcon(
            iconName = exercise?.iconName,
            contentDescription = exercise?.name,
            modifier = Modifier
                .size(40.dp)
                .clickable { onOpenPrForExercise(entry.exerciseId) },
            tint = Color.Unspecified
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPreviewingLastSession && lastSessionPreview != null) {
                lastSessionPreview!!.sets.forEach { set ->
                    SetPill(
                        weightLbs = set.weightLbs,
                        reps = set.reps,
                        displayInKg = displayInKg,
                        isPr = set.isPr == 1,
                        onClick = {}
                    )
                }
            } else {
                sets.forEachIndexed { index, set ->
                    SetPill(
                        weightLbs = set.weightLbs,
                        reps = set.reps,
                        displayInKg = displayInKg,
                        isPr = set.isPr == 1,
                        onClick = { onSetClick(index + 1) }
                    )
                }

                OutlinedButton(
                    onClick = onAddSet,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .width(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, "Add", modifier = Modifier.size(14.dp))
                }
            }

            if (hasHistory) {
                LastSessionPreviewButton(
                    size = 22,
                    dotSize = 8,
                    onPreviewActive = { isPreviewingLastSession = it }
                )
            }
        }
    }
}
