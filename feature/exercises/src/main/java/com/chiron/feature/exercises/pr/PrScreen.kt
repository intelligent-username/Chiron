package com.chiron.feature.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiron.core.common.DistanceUnit
import com.chiron.core.model.Exercise
import com.chiron.core.ui.theme.PrGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrScreen(
    viewModel: ExercisesViewModel,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    initialExerciseId: Long? = null,
    onClose: () -> Unit,
    onOpenWorkout: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var exercisesWithPrs by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    LaunchedEffect(uiState.showArchived) {
        val ids = viewModel.getExerciseIdsWithPrs().toSet()
        val source = if (uiState.showArchived) uiState.archivedExercises else uiState.exercises
        exercisesWithPrs = source.filter { it.id in ids }
    }

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    LaunchedEffect(initialExerciseId, exercisesWithPrs) {
        val targetId = initialExerciseId ?: return@LaunchedEffect
        val target = exercisesWithPrs.firstOrNull { it.id == targetId }
        if (target != null) {
            selectedExercise = target
        }
    }
    androidx.activity.compose.BackHandler(enabled = uiState.prSearchQuery.isNotBlank()) {
        viewModel.clearPrSearch()
    }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = PrGold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Personal Records", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            PrExerciseListPanel(
                uiState = uiState,
                exercisesWithPrs = exercisesWithPrs,
                selectedExercise = selectedExercise,
                focusManager = focusManager,
                viewModel = viewModel,
                onSelectExercise = { selectedExercise = it }
            )

            VerticalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val exercise = selectedExercise
                if (exercise == null) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = PrGold.copy(alpha = 0.25f), modifier = Modifier.size(64.dp))
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
                        displayInKg = displayInKg,
                        distanceUnit = distanceUnit,
                        onOpenWorkout = onOpenWorkout
                    )
                }
            }
        }
    }
}
