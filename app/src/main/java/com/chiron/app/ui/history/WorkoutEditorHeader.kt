package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.util.DateUtils
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import java.time.Instant
import java.time.ZoneId

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
    onDone: () -> Unit
) {
    var expandedName by remember { mutableStateOf(false) }
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
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Row 1: Workout name + action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filteredDayTags = remember(editableDayTag, dayTags) {
                dayTags
                    .filter { it.contains(editableDayTag, ignoreCase = true) && it != editableDayTag }
                    .take(5)
            }

            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = editableDayTag,
                    onValueChange = {
                        if (!isEditable) return@TextField
                        onDayTagChange(it)
                        expandedName = true
                    },
                    readOnly = !isEditable,
                    textStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = transparentTextFieldColors(),
                    placeholder = {
                        Text(
                            "Untitled Workout",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-16).dp) // offset M3 TextField's built-in start padding
                )

                DropdownMenu(
                    expanded = expandedName && filteredDayTags.isNotEmpty(),
                    onDismissRequest = { expandedName = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    filteredDayTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag) },
                            onClick = {
                                onDayTagChange(tag)
                                expandedName = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditable) {
                IconButton(onClick = onShowDuplicateDialog) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        "Duplicate Workout",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
                } else {
                    IconButton(onClick = onEnableEdit) {
                        Icon(Icons.Default.ExpandMore, "Edit")
                    }
                }
            }
        }

        // Row 2: Date + Location
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${DateUtils.getStartStr(workout)} - ${DateUtils.getEndStr(workout)}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Start
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEditable) { isTimeDialogOpen = true }
                    .padding(vertical = 8.dp)
            )

            if (isTimeDialogOpen) {
                WorkoutTimeDialog(
                    initialStartDate = DateUtils.getDateStr(workout),
                    initialStart = DateUtils.getStartStr(workout),
                    initialEndDate = DateUtils.getEndDateStr(workout),
                    initialEnd = DateUtils.getEndStr(workout),
                    onDismiss = { isTimeDialogOpen = false },
                    onConfirm = { startDate, startTime, endDate, endTime ->
                        tryUpdateTimes(startDate, startTime, endDate, endTime)
                        isTimeDialogOpen = false
                    }
                )
            }

            val filteredLocations = remember(editableLocation, allLocations) {
                allLocations.filter { it.contains(editableLocation, ignoreCase = true) }.take(5)
            }

            Box(modifier = Modifier.width(160.dp)) {
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
                    colors = transparentTextFieldColors(),
                    singleLine = true,
                    placeholder = {
                        Text("Location", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = expandedLocation && filteredLocations.isNotEmpty(),
                    onDismissRequest = { expandedLocation = false },
                    properties = PopupProperties(focusable = false)
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

        // Notes field
        TextField(
            value = editableNotes,
            onValueChange = onNotesChange,
            readOnly = !isEditable,
            placeholder = { Text("Add notes…", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = transparentTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-16).dp),
            minLines = 1,
            maxLines = 3
        )
    }
}

/** Shared transparent [TextFieldDefaults.colors] for all inline header fields. */
@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTimeDialog(
    initialStartDate: String,
    initialStart: String,
    initialEndDate: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var startDate by remember { mutableStateOf(initialStartDate) }
    var start by remember { mutableStateOf(initialStart) }
    var endDate by remember { mutableStateOf(initialEndDate) }
    var end by remember { mutableStateOf(initialEnd) }

    var isDateSectionExpanded by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val startDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            val parts = startDate.split("/")
            java.time.LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch(e: Exception) { null }
    )

    val endDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            val parts = endDate.split("/")
            java.time.LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch(e: Exception) { null }
    )

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { millis ->
                        val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC"))
                        startDate = zdt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { millis ->
                        val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC"))
                        endDate = zdt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Edit Timing", 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDateSectionExpanded = !isDateSectionExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isDateSectionExpanded) "Hide date editing" else "Edit dates",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isDateSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isDateSectionExpanded) "Collapse date editing" else "Expand date editing",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start Time") },
                        placeholder = { Text("14:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    
                    TextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End Time") },
                        placeholder = { Text("15:45") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }

                if (isDateSectionExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true }) {
                            OutlinedTextField(
                                value = startDate,
                                onValueChange = { },
                                label = { Text("Start Date") },
                                placeholder = { Text("21/04/2026") },
                                singleLine = true,
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledIndicatorColor = Color.Transparent,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().clickable { showEndDatePicker = true }) {
                            OutlinedTextField(
                                value = endDate,
                                onValueChange = { },
                                label = { Text("End Date") },
                                placeholder = { Text("21/04/2026") },
                                singleLine = true,
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledIndicatorColor = Color.Transparent,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(startDate, start, endDate, end) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}
