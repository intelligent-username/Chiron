package com.chiron.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

/**
 * Card displaying a full superset group — a horizontal row of [SupersetExerciseColumn]s
 * plus shared notes, a superset toggle, and a ± exercise-count stepper.
 *
 * The card title is editable inline. Control callbacks are kept minimal: the caller
 * owns only high-level actions (delete, add exercise); all superset-entry mutations
 * are dispatched through [viewModel].
 */
@Composable
fun SupersetCard(
    entries: List<ExerciseEntry>,
    viewModel: HistoryViewModel,
    displayInKg: Boolean,
    allEntries: List<ExerciseEntry>,
    workoutId: Long,
    supersetNumber: Int,
    onSetClick: (Long, Int) -> Unit,
    onAddSet: (Long) -> Unit,
    onDeleteSuperset: () -> Unit,
    onRequestAddExercise: (fromIncrement: Boolean) -> Unit
) {
    val startEntry = entries.firstOrNull() ?: return
    val scope = rememberCoroutineScope()
    val supersetKey = startEntry.groupId ?: startEntry.id

    var exerciseNotes by remember(supersetKey) { mutableStateOf(startEntry.notes ?: "") }
    var committedExerciseNotes by remember(supersetKey) { mutableStateOf(startEntry.notes ?: "") }
    var isSupersetEnabled by remember(startEntry.id) { mutableStateOf(true) }
    var isEditingTitle by rememberSaveable(supersetKey) { mutableStateOf(false) }
    var supersetTitle by rememberSaveable(supersetKey) { mutableStateOf("Superset $supersetNumber") }
    var draftSupersetTitle by rememberSaveable(supersetKey) { mutableStateOf("Superset $supersetNumber") }
    var numExercisesInSuperset by remember(startEntry.id, startEntry.numExercisesInSuperset) {
        mutableIntStateOf(startEntry.numExercisesInSuperset.coerceAtLeast(2))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header row: icon, editable title, delete button ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExerciseAsyncIcon(
                        iconName = "link",
                        contentDescription = "Superset",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    if (isEditingTitle) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = draftSupersetTitle,
                                onValueChange = { draftSupersetTitle = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            TextButton(onClick = {
                                supersetTitle = draftSupersetTitle.ifBlank { "Superset $supersetNumber" }
                                draftSupersetTitle = supersetTitle
                                isEditingTitle = false
                            }) { Text("Save") }
                            TextButton(onClick = {
                                draftSupersetTitle = supersetTitle
                                isEditingTitle = false
                            }) { Text("Cancel") }
                        }
                    } else {
                        Text(
                            text = supersetTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable {
                                draftSupersetTitle = supersetTitle
                                isEditingTitle = true
                            }
                        )
                    }
                }

                IconButton(onClick = onDeleteSuperset, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        "Delete superset",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Horizontal scroll of exercise columns ──────────────────────────
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxVisibleColumns = 3
                val spacing = 12.dp
                val columnWidth = (maxWidth - spacing * (maxVisibleColumns - 1)) / maxVisibleColumns

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SupersetExerciseColumn(
                            entry = entry,
                            viewModel = viewModel,
                            displayInKg = displayInKg,
                            workoutId = workoutId,
                            modifier = Modifier.width(columnWidth),
                            onSetClick = { setIndex -> onSetClick(entry.id, setIndex) },
                            onAddSet = { onAddSet(entry.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Shared notes field ─────────────────────────────────────────────
            ExerciseNotesField(
                value = exerciseNotes,
                onValueChange = { exerciseNotes = it },
                committed = committedExerciseNotes,
                onCommit = { normalized ->
                    committedExerciseNotes = normalized
                    viewModel.updateExerciseEntry(startEntry.copy(notes = normalized.ifBlank { null }))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Superset toggle + count stepper ────────────────────────────────
            SupersetCounterRow(
                isSupersetEnabled = isSupersetEnabled,
                onSupersetToggle = { enabled ->
                    isSupersetEnabled = enabled
                    scope.launch {
                        val groupId = startEntry.groupId ?: startEntry.id
                        if (!enabled) {
                            val linked = allEntries.filter {
                                it.id == startEntry.id || it.groupId == groupId
                            }
                            linked.forEach { e ->
                                viewModel.updateExerciseEntry(
                                    e.copy(sequenceType = "NONE", groupId = null, numExercisesInSuperset = 2)
                                )
                            }
                        } else {
                            viewModel.updateExerciseEntry(
                                startEntry.copy(
                                    sequenceType = "SUPERSET_START",
                                    groupId = groupId,
                                    numExercisesInSuperset = numExercisesInSuperset.coerceAtLeast(2)
                                )
                            )
                        }
                    }
                },
                numExercisesInSuperset = numExercisesInSuperset,
                onDecrement = {
                    if (numExercisesInSuperset > 2) {
                        val newCount = numExercisesInSuperset - 1
                        numExercisesInSuperset = newCount
                        scope.launch {
                            val groupId = startEntry.groupId ?: startEntry.id
                            val sorted = entries.sortedBy { it.slotIndex }
                            if (sorted.size > newCount) {
                                sorted.drop(newCount).forEach { e ->
                                    viewModel.deleteExerciseEntry(workoutId, e.id)
                                }
                            }
                            sorted.take(newCount).forEachIndexed { i, e ->
                                val type = when {
                                    i == 0 -> "SUPERSET_START"
                                    i == newCount - 1 -> "SUPERSET_END"
                                    else -> "SUPERSET_MIDDLE"
                                }
                                viewModel.updateExerciseEntry(
                                    e.copy(sequenceType = type, groupId = groupId, numExercisesInSuperset = newCount)
                                )
                            }
                        }
                    }
                },
                onIncrement = {
                    if (numExercisesInSuperset < 5) {
                        val newCount = numExercisesInSuperset + 1
                        numExercisesInSuperset = newCount
                        if (entries.size < newCount) onRequestAddExercise(true)
                        scope.launch {
                            val groupId = startEntry.groupId ?: startEntry.id
                            entries.forEachIndexed { i, e ->
                                val type = when {
                                    i == 0 -> "SUPERSET_START"
                                    i == entries.lastIndex -> "SUPERSET_END"
                                    else -> "SUPERSET_MIDDLE"
                                }
                                viewModel.updateExerciseEntry(
                                    e.copy(sequenceType = type, groupId = groupId, numExercisesInSuperset = newCount)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
