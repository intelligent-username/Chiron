package com.chiron.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.components.IconPicker

/** Tracking configuration chosen at exercise creation time. */
data class TrackingConfig(
    val isWeightBased: Boolean = true,
    val isRepBased: Boolean = true,   // mutually exclusive with isTimeBased
    val isTimeBased: Boolean = false,
    val isDistanceBased: Boolean = false,
    val isBodyweight: Boolean = false,
    val percentBodyweight: Double = 100.0
) {
    /** True when the config is logically valid (at least one metric enabled). */
    val isValid: Boolean get() = isRepBased || isTimeBased
}

@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String?, description: String?, config: TrackingConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>("default") }
    // Optional description field (optional when creating exercise)
    var description by remember { mutableStateOf("") }

    // Tracking config state
    var weightEnabled by remember { mutableStateOf(true) }
    var distanceEnabled by remember { mutableStateOf(false) }
    // true = reps, false = time
    var useReps by remember { mutableStateOf(true) }
    var bodyweightEnabled by remember { mutableStateOf(false) }
    var percentText by remember { mutableStateOf("100") }

    fun reset() {
        name = ""
        selectedIcon = "default"
        description = ""
        weightEnabled = true
        distanceEnabled = false
        useReps = true
        bodyweightEnabled = false
        percentText = "100"
    }

    val percentValue = percentText.trim().toDoubleOrNull()
    val percentError = if (!bodyweightEnabled) null
        else if (percentValue == null || percentValue <= 0 || percentValue > 200) "Enter a percentage between 1 and 200"
        else null

    val config = TrackingConfig(
        isWeightBased = weightEnabled,
        isRepBased = useReps,
        isTimeBased = !useReps,
        isDistanceBased = distanceEnabled,
        isBodyweight = bodyweightEnabled,
        percentBodyweight = if (bodyweightEnabled) (percentValue ?: 100.0) else 100.0
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

                    // Optional description field (optional when creating exercise)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )


                // Row of two modern toggle cards: Weight and Distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weight Card
                    ModernToggleCard(
                        modifier = Modifier.weight(1f),
                        title = "Weight",
                        checked = weightEnabled,
                        onCheckedChange = { weightEnabled = it }
                    )

                    // Distance Card
                    ModernToggleCard(
                        modifier = Modifier.weight(1f),
                        title = "Distance",
                        checked = distanceEnabled,
                        onCheckedChange = { distanceEnabled = it }
                    )
                }

                // Reps vs Time Segmented Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Primary Metric",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModernSegmentedSelector(
                        selectedOption = if (useReps) "Reps" else "Time",
                        options = listOf("Reps", "Time"),
                        onOptionSelected = { useReps = (it == "Reps") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Track Bodyweight", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = bodyweightEnabled, onCheckedChange = { bodyweightEnabled = it })
                }

                if (bodyweightEnabled) {
                    Text(
                        "Weight you log on sets counts as EXTRA (vest/belt). Leave empty for plain bodyweight sets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = percentText,
                        onValueChange = { percentText = it },
                        label = { Text("% of bodyweight") },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = percentError != null,
                        supportingText = { if (percentError != null) Text(percentError) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("50", "60", "80", "100").forEach { preset ->
                            AssistChip(
                                onClick = { percentText = preset },
                                label = { Text("$preset%") }
                            )
                        }
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
            TextButton(
                onClick = {
                    if (name.isNotBlank() && config.isValid && percentError == null) {
                        onCreate(name, selectedIcon, description, config)
                        reset()
                    }
                },
                enabled = name.isNotBlank() && config.isValid && percentError == null,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(
                onClick = { reset(); onDismiss() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Cancel") }
        }
    )
}

@Composable
fun ModernToggleCard(
    modifier: Modifier = Modifier,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val backgroundColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val textColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (checked) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = if (checked) 0.9f else 0.75f)
            )
        }
    }
}

@Composable
fun ModernSegmentedSelector(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            val backgroundColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}
