package com.chiron.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.prefs.UserSettingsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (dayTag: String, locationTag: String, dateIso: String) -> Unit,
    settingsRepository: UserSettingsRepository? = null,
    existingLocations: List<String> = emptyList(),
    existingDayTags: List<String> = emptyList()
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    
    // Day Tag State (Default: Full Body Day)
    var selectedDayTag by remember { mutableStateOf("Untitled Workout") }
    var customDayTagInput by remember { mutableStateOf("") }
    
    // Location State
    var selectedLocation by remember { mutableStateOf("") }
    var customLocationInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Load custom locations from settings
    val customLocations by settingsRepository?.customLocationsFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    // Merge locations
    val allLocations = remember(customLocations, existingLocations) {
        (customLocations + existingLocations).distinct().sorted()
    }
    
    // Merge day tags (ensure efficient distinct)
    val allDayTags = remember(existingDayTags) {
        existingDayTags.distinct().sorted()
    }

    // Calculate derived values
    val dateIso = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val dayOfWeek = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Determine final values
    val finalLocation = if (selectedLocation == "Custom" && customLocationInput.isNotBlank()) {
        customLocationInput
    } else if (selectedLocation.isNotBlank()) {
        selectedLocation
    } else {
        ""
    }
    
    val finalDayTag = if (selectedDayTag == "Custom" && customDayTagInput.isNotBlank()) {
        customDayTagInput
    } else if (selectedDayTag.isNotBlank()) {
        selectedDayTag
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workout") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date display
                Text(
                    text = "Date: $dateIso ($dayOfWeek)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Workout Name (Day Tag) Selection
                SelectionInput(
                    label = "Workout Name",
                    options = allDayTags,
                    selectedOption = selectedDayTag,
                    onOptionSelected = { 
                        selectedDayTag = it 
                        if (it != "Custom") customDayTagInput = ""
                    },
                    customInput = customDayTagInput,
                    onCustomInputChange = { customDayTagInput = it }
                )

                // Location Selection
                SelectionInput(
                    label = "Location",
                    options = allLocations,
                    selectedOption = selectedLocation,
                    onOptionSelected = { 
                        selectedLocation = it
                        if (it != "Custom") customLocationInput = ""
                    },
                    customInput = customLocationInput,
                    onCustomInputChange = { customLocationInput = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (finalLocation.isNotBlank() && finalDayTag.isNotBlank()) {
                        // Save custom location if it's new (Day tags are saved implicitly via usage)
                        if (selectedLocation == "Custom" && customLocationInput.isNotBlank()) {
                            scope.launch {
                                settingsRepository?.addCustomLocation(customLocationInput)
                            }
                        }
                        onCreate(finalDayTag, finalLocation, dateIso)
                    }
                },
                enabled = finalLocation.isNotBlank() && finalDayTag.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionInput(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (selectedOption == "Custom") "Custom…" else selectedOption,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
                
                if (options.isNotEmpty()) {
                    HorizontalDivider()
                }
                
                DropdownMenuItem(
                    text = { Text("Custom…") },
                    onClick = {
                        onOptionSelected("Custom")
                        expanded = false
                    }
                )
            }
        }

        if (selectedOption == "Custom") {
            OutlinedTextField(
                value = customInput,
                onValueChange = onCustomInputChange,
                label = { Text("Enter custom $label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
    }
}
