package com.chiron.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    existingLocations: List<String> = emptyList()
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedLocation by remember { mutableStateOf("") }
    var customLocationInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load custom locations from settings
    val customLocations by settingsRepository?.customLocationsFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    // Merge custom locations with existing workout locations (unique, sorted)
    val allLocations = remember(customLocations, existingLocations) {
        (customLocations + existingLocations).distinct().sorted()
    }

    // Calculate day tag from selected date
    val dayTag = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateIso = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    // Determine which location to use
    val finalLocation = if (selectedLocation == "Custom" && customLocationInput.isNotBlank()) {
        customLocationInput
    } else if (selectedLocation.isNotBlank()) {
        selectedLocation
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workout") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date display
                Text(
                    text = "Date: $dateIso ($dayTag)",
                    style = MaterialTheme.typography.bodyLarge
                )

                // Location dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedLocation == "Custom") "Custom..." else selectedLocation,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Location") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // Show all locations (from history + custom)
                        allLocations.forEach { location ->
                            DropdownMenuItem(
                                text = { Text(location) },
                                onClick = {
                                    selectedLocation = location
                                    customLocationInput = ""
                                    expanded = false
                                }
                            )
                        }
                        
                        // Add divider if there are locations
                        if (allLocations.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        
                        // "Custom" option to add new location
                        DropdownMenuItem(
                            text = { Text("Custom...") },
                            onClick = {
                                selectedLocation = "Custom"
                                expanded = false
                            }
                        )
                    }
                }

                // Show custom location input if "Custom" is selected
                if (selectedLocation == "Custom") {
                    OutlinedTextField(
                        value = customLocationInput,
                        onValueChange = { customLocationInput = it },
                        label = { Text("Enter location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (finalLocation.isNotBlank()) {
                        // Save custom location if it's new
                        if (selectedLocation == "Custom" && customLocationInput.isNotBlank()) {
                            scope.launch {
                                settingsRepository?.addCustomLocation(customLocationInput)
                            }
                        }
                        onCreate("Full Body Day", finalLocation, dateIso)
                    }
                },
                enabled = finalLocation.isNotBlank()
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
