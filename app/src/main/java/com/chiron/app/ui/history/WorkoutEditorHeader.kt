package com.chiron.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.app.data.entities.WorkoutSession
import java.time.Instant
import java.time.LocalDate
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
    editableDayTag: String,
    onDayTagChange: (String) -> Unit,
    editableDate: String,
    onDateChange: (String) -> Unit,
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
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = remember(editableDate) {
            try {
                LocalDate.parse(editableDate)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                        onDateChange(newDate)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
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
            val filteredDayTags = remember(editableDayTag, dayTags) {
                dayTags
                    .filter { it.contains(editableDayTag, ignoreCase = true) && it != editableDayTag }
                    .take(5)
            }

            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = editableDayTag,
                    onValueChange = {
                        onDayTagChange(it)
                        expandedName = true
                    },
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
                    modifier = Modifier.fillMaxWidth()
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
            }
        }

        // Row 2: Date + Location
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = editableDate,
                onValueChange = onDateChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                colors = transparentTextFieldColors(),
                modifier = Modifier.width(160.dp),
                singleLine = true,
                placeholder = { Text("YYYY-MM-DD") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, "Select Date", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            val filteredLocations = remember(editableLocation, allLocations) {
                allLocations.filter { it.contains(editableLocation, ignoreCase = true) }.take(5)
            }

            Box(modifier = Modifier.width(160.dp)) {
                TextField(
                    value = editableLocation,
                    onValueChange = {
                        onLocationChange(it)
                        expandedLocation = true
                    },
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
            placeholder = { Text("Add notes...", style = MaterialTheme.typography.bodyMedium) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = transparentTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
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
