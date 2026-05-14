package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.components.IconPicker

/** Tracking configuration chosen at exercise creation time. */
data class TrackingConfig(
    val isWeightBased: Boolean = true,
    val isRepBased: Boolean = true,   // mutually exclusive with isTimeBased
    val isTimeBased: Boolean = false,
    val isDistanceBased: Boolean = false
) {
    /** True when the config is logically valid (at least one metric enabled). */
    val isValid: Boolean get() = isRepBased || isTimeBased
}

@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String?, config: TrackingConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>("default") }

    // Tracking config state
    var weightEnabled by remember { mutableStateOf(true) }
    var distanceEnabled by remember { mutableStateOf(false) }
    // true = reps, false = time
    var useReps by remember { mutableStateOf(true) }

    fun reset() {
        name = ""
        selectedIcon = "default"
        weightEnabled = true
        distanceEnabled = false
        useReps = true
    }

    val config = TrackingConfig(
        isWeightBased = weightEnabled,
        isRepBased = useReps,
        isTimeBased = !useReps,
        isDistanceBased = distanceEnabled
    )

    AlertDialog(
        onDismissRequest = { reset(); onDismiss() },
        title = { Text("New Exercise") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tracking config section
                Text(
                    "Tracking",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Weight toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Track Weight", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = weightEnabled, onCheckedChange = { weightEnabled = it })
                }

                // Distance toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Track Distance", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = distanceEnabled, onCheckedChange = { distanceEnabled = it })
                }

                // Reps vs Time (segmented two-state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Count by", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Reps",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (useReps) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = !useReps,
                            onCheckedChange = { useReps = !it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            "Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!useReps) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()
                Box(modifier = Modifier.height(300.dp)) {
                    IconPicker(
                        selectedIcon = selectedIcon,
                        onIconSelected = { selectedIcon = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && config.isValid) {
                    onCreate(name, selectedIcon, config)
                    reset()
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { reset(); onDismiss() }) { Text("Cancel") }
        }
    )
}
