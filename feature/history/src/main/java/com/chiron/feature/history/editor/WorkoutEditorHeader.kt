package com.chiron.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.core.common.DateUtils
import com.chiron.core.model.WorkoutSession
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.ThinOutline

/**
 * Full-width header for the workout editor.
 *
 * Contains:
 *  - Editable workout name (with autocomplete dropdown from existing [dayTags])
 *  - Date field with calendar picker
 *  - Location field with autocomplete dropdown from existing [allLocations]
 *  - Multi-line notes field
 *  - Duplicate / Delete icon buttons and a Done text button
 *
 * All state lives in the caller ([WorkoutEditor]); this composable is purely presentational.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorHeader(
    workout: WorkoutSession,
    isEditable: Boolean,
    onEnableEdit: () -> Unit,
    editableDayTag: String,
    onDayTagChange: (String) -> Unit,
    onWorkoutTimeChange: (Long, Long?, String) -> Unit,
    editableLocation: String,
    onLocationChange: (String) -> Unit,
    editableNotes: String,
    onNotesChange: (String) -> Unit,
    dayTags: List<String>,
    allLocations: List<String>,
    onShowDeleteDialog: () -> Unit,
    onShowDuplicateDialog: () -> Unit,
    onResetTimes: (suspend () -> HistoryViewModel.WorkoutTimingReset?)? = null,
    onDone: () -> Unit,
    volumeLbs: Double? = null,
    displayInKg: Boolean = false
) {
    var expandedLocation by remember { mutableStateOf(false) }
    var isTimeDialogOpen by remember { mutableStateOf(false) }

    fun tryUpdateTimes(startDate: String, startTime: String, endDate: String, endTime: String) {
        val parsed = DateUtils.parseWorkoutDateTimes(startDate, startTime, endDate, endTime, workout)
        if (parsed != null) {
            onWorkoutTimeChange(parsed.dateUtc, parsed.endTimeUtc, parsed.dateIso)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Workout name + action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showNameDialog by remember { mutableStateOf(false) }

            Text(
                text = editableDayTag.ifBlank { "Untitled Workout" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clickable(enabled = isEditable) { showNameDialog = true }
            )

            Crossfade(
                targetState = isEditable,
                animationSpec = tween(150),
                label = "header-actions"
            ) { editing ->
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShowDuplicateDialog) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                "Duplicate Workout",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onShowDeleteDialog) {
                            Icon(
                                Icons.Default.Delete,
                                if (workout.archived != 0) "Delete Workout Permanently" else "Archive Workout",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                        TextButton(onClick = onDone) {
                            Text("Done", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    IconButton(onClick = onEnableEdit) {
                        Icon(Icons.Default.ExpandMore, "Edit")
                    }
                }
            }
        }

        val filteredLocations = remember(editableLocation, allLocations) {
            allLocations.filter { it.contains(editableLocation, ignoreCase = true) }.take(5)
        }

        // Row 2: Date + Location - Crossfade entire row for stable layout
        Crossfade(
            targetState = isEditable,
            animationSpec = tween(150),
            label = "header-date-location"
        ) { editing ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: date/time
                if (!editing) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = DateUtils.formatWorkoutCardDate(workout),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Start
                            )
                        )
                        Text(
                            text = "${DateUtils.getStartStr(workout)} - ${DateUtils.getEndStr(workout)}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                } else {
                    Text(
                        text = "${DateUtils.getStartStr(workout)} - ${DateUtils.getEndStr(workout)}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Start
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isTimeDialogOpen = true }
                            .padding(vertical = 8.dp)
                    )
                }

                // Right side: location (view) or location editor (edit)
                if (!editing) {
                    // View mode: show location as text
                    Text(
                        text = editableLocation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.width(160.dp).align(Alignment.CenterVertically).padding(top = 8.dp)
                    )
                } else {
                    // Edit mode: location editor (animated)
                    AnimatedVisibility(
                        visible = isEditable,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                        modifier = Modifier.width(160.dp)
                    ) {
                        Box(
                            modifier = Modifier.width(160.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (volumeLbs != null) {
                                    WorkoutVolumeTag(volumeLbs = volumeLbs, displayInKg = displayInKg)
                                }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (volumeLbs != null) {
                                        BasicTextField(
                                            value = editableLocation,
                                            onValueChange = {
                                                if (isEditable) {
                                                    onLocationChange(it)
                                                    expandedLocation = true
                                                }
                                            },
                                            readOnly = !isEditable,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.End,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics { contentDescription = "Location" },
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    if (editableLocation.isBlank()) {
                                                        Text(
                                                            "Location",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    } else {
                                        TextField(
                                            value = editableLocation,
                                            onValueChange = {
                                                if (!isEditable) return@TextField
                                                onLocationChange(it)
                                                expandedLocation = true
                                            },
                                            readOnly = !isEditable,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.End,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            ),
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                unfocusedIndicatorColor = Color.Transparent,
                                                disabledIndicatorColor = Color.Transparent
                                            ),
                                            singleLine = true,
                                            placeholder = {
                                                Text("Location", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedLocation && filteredLocations.isNotEmpty(),
                                    onDismissRequest = { expandedLocation = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                                ) {
                                    filteredLocations.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { Text(loc) },
                                            onClick = {
                                                onLocationChange(loc)
                                                expandedLocation = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isTimeDialogOpen) {
            WorkoutTimeDialog(
                initialStartDate = DateUtils.getDateStr(workout),
                initialStart = DateUtils.getStartStr(workout),
                initialEndDate = DateUtils.getEndDateStr(workout),
                initialEnd = DateUtils.getEndStr(workout),
                onReset = onResetTimes,
                onDismiss = { isTimeDialogOpen = false },
                onConfirm = { startDate, startTime, endDate, endTime ->
                    tryUpdateTimes(startDate, startTime, endDate, endTime)
                    isTimeDialogOpen = false
                }
            )
        }

        AnimatedVisibility(
            visible = !isEditable && editableLocation.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier
                .width(160.dp)
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            Text(
                text = editableLocation,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }

        // Notes field
        AnimatedVisibility(
            visible = isEditable || editableNotes.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = editableNotes,
                onValueChange = { if (isEditable) onNotesChange(it) },
                readOnly = !isEditable,
                placeholder = {
                    Text(
                        "Add notes…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoolGray
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                label = {
                    Text(
                        "Workout notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoolGray
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = ThinOutline,
                    cursorColor = ElectricBlue,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = ElectricBlue,
                    unfocusedLabelColor = CoolGray,
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    disabledTextColor = CoolGray
                )
            )
        }
    }
}

/** Shared transparent [TextFieldDefaults.colors] for all inline header fields. */
@Composable
internal fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)