package com.chiron.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.core.common.DistanceUnit
import com.chiron.core.database.ChironRepository
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.ui.components.ExerciseAsyncIcon
import com.chiron.core.ui.components.SetPill
import com.chiron.core.ui.theme.ThinOutline
import kotlinx.coroutines.launch

/**
 * Card for a single (non-superset) exercise entry.
 *
 * Displays the exercise icon and name, all set pills, an editable notes field,
 * and superset controls (toggle + ± count stepper).
 *
 * Supports press-and-hold last-session preview (single exercise and superset variants).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseEntryCard(
    entry: ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    allEntries: List<ExerciseEntry>,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteEntry: () -> Unit,
    onOpenPrForExercise: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit,
    onOpenSetInWorkout: (Long) -> Unit = {},
    workoutId: Long,
    onRequestAddExercise: () -> Unit,
    isEditable: Boolean,
    highlightedEntryId: Long? = null,
    highlightedSetIndex: Int? = null
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var showReplaceExercise by remember(entry.id) { mutableStateOf(false) }
    var exerciseNotes by remember { mutableStateOf(entry.notes ?: "") }
    var committedExerciseNotes by remember(entry.id) { mutableStateOf(entry.notes ?: "") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var isPreviewingLastSession by remember { mutableStateOf(false) }
    var lastSessionPreview by remember { mutableStateOf<ChironRepository.LastSessionPreview?>(null) }
    var lastSessionSupersetPreview by remember { mutableStateOf<ChironRepository.LastSessionSupersetPreview?>(null) }

    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    LaunchedEffect(entry.exerciseId, workoutId) {
        val (solo, superset) = viewModel.getLastSessionCombinedPreview(entry.exerciseId, workoutId)
        lastSessionSupersetPreview = superset
        lastSessionPreview = solo
    }

    val hasHistory = lastSessionPreview != null || lastSessionSupersetPreview != null

    val contentAlpha = if (isPreviewingLastSession) 0.55f else 1f

    if (showReplaceExercise && isEditable) {
        ReplaceWorkoutExerciseDialog(entry, exercise, viewModel, onDismiss = { showReplaceExercise = false })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = MaterialTheme.shapes.medium,
                spotColor = Color.White.copy(alpha = 0.12f),
                ambientColor = Color.White.copy(alpha = 0.06f)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { focusManager.clearFocus() }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ThinOutline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Top row: icon | name+sets | action buttons ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    ExerciseAsyncIcon(
                        iconName = exercise?.iconName,
                        contentDescription = exercise?.name,
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                onClick = { onOpenExerciseDetail(entry.exerciseId) },
                                onLongClickLabel = "Replace exercise",
                                onLongClick = if (isEditable) ({ showReplaceExercise = true }) else null
                            ),
                        tint = Color.Unspecified
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    when {
                        isPreviewingLastSession && lastSessionSupersetPreview != null -> {
                            Text(
                                "Superset",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                lastSessionSupersetPreview!!.exercises.forEach { ep ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            ep.exerciseName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            ep.sets.forEach { set ->
                                                SetPill(
                                                    set = set,
                                                    displayInKg = displayInKg,
                                                    distanceUnit = distanceUnit,
                                                    isPr = set.isPr == 1,
                                                    onClick = { onOpenSetInWorkout(set.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        isPreviewingLastSession && lastSessionPreview != null -> {
                            Text(
                                exercise?.name ?: "Loading…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { onOpenExerciseDetail(entry.exerciseId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                            ) {
                                lastSessionPreview!!.sets.forEach { set ->
                                    SetPill(
                                        set = set,
                                        displayInKg = displayInKg,
                                        distanceUnit = distanceUnit,
                                        isPr = set.isPr == 1,
                                        onClick = { onOpenSetInWorkout(set.id) }
                                    )
                                }
                            }
                        }

                        else -> {
                            val blankSetSize = Modifier.size(width = 56.dp, height = 32.dp)
                            Text(
                                exercise?.name ?: "Loading…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onOpenPrForExercise(entry.exerciseId) },
                                    onLongClickLabel = "Replace exercise",
                                    onLongClick = if (isEditable) ({ showReplaceExercise = true }) else null
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                            ) {
                                sets.forEachIndexed { index, set ->
                                    val isHighlighted = entry.id == highlightedEntryId && (index + 1) == highlightedSetIndex
                                    val isBlank = set.weightLbs == null && set.reps == null &&
                                        set.durationSeconds == null && set.distanceMeters == null
                                    val setModifier = if (isBlank) blankSetSize else Modifier
                                    if (exercise != null) {
                                        SetPill(
                                            set = set,
                                            exercise = exercise!!,
                                            displayInKg = displayInKg,
                                            distanceUnit = distanceUnit,
                                            isPr = set.isPr == 1,
                                            highlighted = isHighlighted,
                                            onClick = { if (isEditable) onSetClick(index + 1) },
                                            modifier = setModifier
                                        )
                                    } else {
                                        SetPill(
                                            set = set,
                                            displayInKg = displayInKg,
                                            distanceUnit = distanceUnit,
                                            isPr = set.isPr == 1,
                                            highlighted = isHighlighted,
                                            onClick = { if (isEditable) onSetClick(index + 1) },
                                            modifier = setModifier
                                        )
                                    }
                                }
                                // The new blank set occupies the add button's previous slot.
                                if (isEditable) {
                                    Box(
                                        modifier = blankSetSize
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                            .clickable(onClick = onAddSet)
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            "Add",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right column: delete + preview button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isEditable) {
                        IconButton(onClick = onDeleteEntry, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Close,
                                "Remove",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (hasHistory) {
                        LastSessionPreviewButton(
                            size = 28,
                            dotSize = 10,
                            onPreviewActive = { isPreviewingLastSession = it }
                        )
                    }
                }
            }

            val isNotesVisible = isEditable || exerciseNotes.isNotBlank() || (isPreviewingLastSession && (lastSessionPreview != null || lastSessionSupersetPreview != null))
            
            if (isNotesVisible) {
                Spacer(modifier = Modifier.height(10.dp))

                // ── Notes section ──────────────────────────────────────────────────
                if (isPreviewingLastSession && (lastSessionPreview != null || lastSessionSupersetPreview != null)) {
                    val previewNotes =
                        (lastSessionSupersetPreview?.notes ?: lastSessionPreview?.notes) ?: ""
                    val dateLabel =
                        lastSessionSupersetPreview?.dateLabel ?: lastSessionPreview?.dateLabel ?: ""

                    ExerciseNotesField(
                        value = previewNotes,
                        onValueChange = {},
                        committed = previewNotes,
                        onCommit = {},
                        isReadOnly = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                } else {
                    if (isEditable) {
                        ExerciseNotesField(
                            value = exerciseNotes,
                            onValueChange = { exerciseNotes = it },
                            committed = committedExerciseNotes,
                            onCommit = { normalized ->
                                committedExerciseNotes = normalized
                                viewModel.updateExerciseEntry(entry.copy(notes = normalized.ifBlank { null }))
                            }
                        )
                    } else {
                        ExerciseNotesField(
                            value = exerciseNotes,
                            onValueChange = {},
                            committed = exerciseNotes,
                            onCommit = {},
                            isReadOnly = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
            }

            // ── Superset controls (hidden during preview) ──────────────────────
            if (isEditable && !isPreviewingLastSession) {
                SupersetCounterRow(
                    isSupersetEnabled = false,
                    onSupersetToggle = { newValue ->
                        if (newValue) {
                            onRequestAddExercise()
                        }
                    },
                    numExercisesInSuperset = 1,
                    onDecrement = {},
                    onIncrement = {
                        onRequestAddExercise()
                    }
                )
            }
        }
    }
}
