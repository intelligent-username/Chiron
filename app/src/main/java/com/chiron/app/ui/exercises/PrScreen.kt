package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.theme.PrGoldDark
import com.chiron.app.util.UnitConversion
import com.chiron.app.viewmodel.ExercisesViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen PR board.
 *
 * Left panel: all exercises. An exercise is listed if it has at least one PR recorded.
 * Right panel: per-exercise breakdown of best weights by rep count.
 * On phones the panels are stacked (exercise list → detail on tap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrScreen(
    viewModel: ExercisesViewModel,
    displayInKg: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Load exercise IDs that have PRs — re-evaluates when archived toggle changes
    var exercisesWithPrs by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    LaunchedEffect(uiState.showArchived) {
        val ids = viewModel.getExerciseIdsWithPrs().toSet()
        val source = if (uiState.showArchived) uiState.archivedExercises else uiState.exercises
        exercisesWithPrs = source.filter { it.id in ids }
    }

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        modifier = modifier,
        // IMPORTANT: tell the nested Scaffold not to re-apply window insets.
        // The outer MainActivity Scaffold already consumed them. Without this,
        // Material3 re-pads from the real screen top, extending this TopAppBar's
        // touch surface behind the main app bar and swallowing its button clicks.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = PrGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Personal Records", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Left panel: exercise list
            LazyColumn(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (exercisesWithPrs.isEmpty()) {
                    item {
                        Text(
                            text = "No PRs yet.\nFinish some sets!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
                items(exercisesWithPrs) { exercise ->
                    val isSelected = selectedExercise?.id == exercise.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedExercise = exercise }
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.chiron.app.ui.components.ExerciseAsyncIcon(
                            iconName = exercise.iconName,
                            contentDescription = exercise.name,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }

            VerticalDivider()

            // ── Right panel: PR detail ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val exercise = selectedExercise
                if (exercise == null) {
                    // Placeholder when nothing selected
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = PrGold.copy(alpha = 0.25f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Select an exercise to see\nyour best performances",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    PrDetailPanel(
                        exercise = exercise,
                        viewModel = viewModel,
                        displayInKg = displayInKg
                    )
                }
            }
        }
    }
}

@Composable
private fun PrDetailPanel(
    exercise: Exercise,
    viewModel: ExercisesViewModel,
    displayInKg: Boolean
) {
    val prs by viewModel.getPrsForExerciseFlow(exercise.id).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        // Exercise header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.chiron.app.ui.components.ExerciseAsyncIcon(
                iconName = exercise.iconName,
                contentDescription = exercise.name,
                modifier = Modifier.size(36.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        if (prs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No PRs recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(prs) { pr ->
                    PrRow(pr = pr, displayInKg = displayInKg)
                }
            }
        }
    }
}

@Composable
private fun PrRow(pr: ExercisePr, displayInKg: Boolean) {
    val weightText = if (displayInKg) {
        val kg = UnitConversion.lbsToDisplayKg(pr.weightLbs)
        "${formatPrWeight(kg)} kg"
    } else {
        "${formatPrWeight(pr.weightLbs)} lbs"
    }

    val repLabel = when (pr.reps) {
        1 -> "1 Rep Max"
        else -> "${pr.reps} Rep Max"
    }

    val dateLabel = remember(pr.timestampUtc) {
        try {
            val date = java.time.Instant.ofEpochMilli(pr.timestampUtc)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            "$dow, $month ${date.dayOfMonth}"
        } catch (e: Exception) {
            ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrGold.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (dateLabel.isNotEmpty()) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }

        Spacer(Modifier.width(24.dp))

        Text(
            text = weightText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PrGoldDark
        )
    }
}

private fun formatPrWeight(value: Double): String {
    val formatted = String.format("%.2f", value)
    return formatted.trimEnd('0').trimEnd('.')
}
