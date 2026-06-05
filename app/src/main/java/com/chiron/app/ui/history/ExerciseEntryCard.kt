package com.chiron.app.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.components.SetPill
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

/**
 * Card for a single (non-superset) exercise entry.
 *
 * Displays the exercise icon and name, all set pills, an editable notes field,
 * and superset controls (toggle + ± count stepper).
 *
 * Supports press-and-hold last-session preview (single exercise and superset variants).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEntryCard(
    entry: ExerciseEntry,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    distanceUnit: com.chiron.app.prefs.DistanceUnit,
    allEntries: List<ExerciseEntry>,
    onSetClick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteEntry: () -> Unit,
    onOpenPrForExercise: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit,
    workoutId: Long,
    onRequestAddExercise: () -> Unit,
    isEditable: Boolean
) {
    val sets by viewModel.getSetsForEntry(entry.id).collectAsState(initial = emptyList())
    var exercise by remember { mutableStateOf<com.chiron.app.data.entities.Exercise?>(null) }
    var exerciseNotes by remember { mutableStateOf(entry.notes ?: "") }
    var committedExerciseNotes by remember(entry.id) { mutableStateOf(entry.notes ?: "") }
    var numExercisesInSuperset by remember { mutableIntStateOf(entry.numExercisesInSuperset) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var isPreviewingLastSession by remember { mutableStateOf(false) }
    var lastSessionPreview by remember { mutableStateOf<ChironRepository.LastSessionPreview?>(null) }
    var lastSessionSupersetPreview by remember { mutableStateOf<ChironRepository.LastSessionSupersetPreview?>(null) }

    LaunchedEffect(entry.exerciseId) {
        exercise = viewModel.getExerciseById(entry.exerciseId)
    }

    LaunchedEffect(entry.exerciseId, workoutId) {
        // Always try superset preview first — if the exercise was last done in a superset,
        // this will return the full superset context. Otherwise it returns null and we
        // fall back to the plain solo preview.
        lastSessionSupersetPreview = viewModel.getLastSessionSupersetPreview(entry.exerciseId, workoutId)
        if (lastSessionSupersetPreview == null) {
            lastSessionPreview = viewModel.getLastSessionPreview(entry.exerciseId, workoutId)
        } else {
            lastSessionPreview = null // clear stale solo data if superset is available
        }
    }

    val hasHistory = lastSessionPreview != null || lastSessionSupersetPreview != null

    val currentExercisesInSuperset = remember(allEntries, entry.id) {
        var count = 1
        val currentIndex = allEntries.indexOfFirst { it.id == entry.id }
        if (currentIndex >= 0) {
            for (i in (currentIndex + 1) until allEntries.size) {
                when (allEntries[i].sequenceType) {
                    "SUPERSET_MIDDLE" -> count++
                    "SUPERSET_END" -> { count++; break }
                    else -> break
                }
            }
        }
        count
    }

    var isSupersetEnabled by remember(entry.sequenceType, currentExercisesInSuperset) {
        mutableStateOf(entry.sequenceType == "SUPERSET_START" && currentExercisesInSuperset > 1)
    }

    val exercisesNeededInSuperset = numExercisesInSuperset - currentExercisesInSuperset
    val contentAlpha = if (isPreviewingLastSession) 0.55f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { focusManager.clearFocus() }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Top row: icon | name+sets | action buttons ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExerciseAsyncIcon(
                        iconName = if (isSupersetEnabled) "link" else exercise?.iconName,
                        contentDescription = exercise?.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onOpenPrForExercise(entry.exerciseId) },
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
                                                    onClick = {}
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        isPreviewingLastSession && lastSessionPreview != null -> {
                            Text(
                                exercise?.name ?: "Loading...",
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
                                        onClick = {}
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                exercise?.name ?: "Loading...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onOpenExerciseDetail(entry.exerciseId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                            ) {
                                sets.forEachIndexed { index, set ->
                                    if (exercise != null) {
                                        SetPill(
                                            set = set,
                                            exercise = exercise!!,
                                            displayInKg = displayInKg,
                                            distanceUnit = distanceUnit,
                                            isPr = set.isPr == 1,
                                            onClick = { if (isEditable) onSetClick(index + 1) }
                                        )
                                    } else {
                                        SetPill(
                                            set = set,
                                            displayInKg = displayInKg,
                                            distanceUnit = distanceUnit,
                                            isPr = set.isPr == 1,
                                            onClick = { if (isEditable) onSetClick(index + 1) }
                                        )
                                    }
                                }
                                // Match SetPill size and shape exactly for consistency
                                if (isEditable) {
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
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
                }            }

            Spacer(modifier = Modifier.height(12.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // ── Superset controls (hidden during preview) ──────────────────────
            if (isEditable && !isPreviewingLastSession) {
                SupersetCounterRow(
                    isSupersetEnabled = isSupersetEnabled,
                    onSupersetToggle = { newValue ->
                        if (newValue) {
                            isSupersetEnabled = true
                            val groupId = entry.groupId ?: entry.id
                            scope.launch {
                                viewModel.updateExerciseEntry(
                                    entry.copy(
                                        sequenceType = "SUPERSET_START",
                                        groupId = groupId,
                                        numExercisesInSuperset = numExercisesInSuperset.coerceAtLeast(2)
                                    )
                                )
                            }
                            onRequestAddExercise()
                        } else {
                            isSupersetEnabled = false
                            scope.launch {
                                val groupId = entry.groupId
                                if (entry.sequenceType == "SUPERSET_START" && groupId != null) {
                                    allEntries.filter { it.groupId == groupId }.forEach { e ->
                                        viewModel.updateExerciseEntry(
                                            e.copy(sequenceType = "NONE", groupId = null, numExercisesInSuperset = 2)
                                        )
                                    }
                                }
                                viewModel.updateExerciseEntry(
                                    entry.copy(sequenceType = "NONE", groupId = null, numExercisesInSuperset = 2)
                                )
                            }
                        }
                    },
                    numExercisesInSuperset = numExercisesInSuperset,
                    onDecrement = {
                        if (numExercisesInSuperset > 2) {
                            numExercisesInSuperset--
                            scope.launch {
                                viewModel.updateExerciseEntry(
                                    entry.copy(
                                        numExercisesInSuperset = numExercisesInSuperset,
                                        sequenceType = "SUPERSET_START",
                                        groupId = entry.groupId ?: entry.id
                                    )
                                )
                            }
                        }
                    },
                    onIncrement = {
                        if (numExercisesInSuperset < 5) {
                            numExercisesInSuperset++
                            scope.launch {
                                viewModel.updateExerciseEntry(
                                    entry.copy(
                                        numExercisesInSuperset = numExercisesInSuperset,
                                        sequenceType = "SUPERSET_START",
                                        groupId = entry.groupId ?: entry.id
                                    )
                                )
                            }
                        }
                    },
                    exercisesNeeded = exercisesNeededInSuperset,
                    onAddExercises = { onRequestAddExercise() }
                )
            }
        }
    }
}
