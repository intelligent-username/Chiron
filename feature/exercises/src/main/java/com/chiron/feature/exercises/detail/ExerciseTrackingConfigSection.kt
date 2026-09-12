package com.chiron.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ExerciseTrackingConfigSection(
    weightEnabled: Boolean,
    onWeightEnabledChange: (Boolean) -> Unit,
    distanceEnabled: Boolean,
    onDistanceEnabledChange: (Boolean) -> Unit,
    useReps: Boolean,
    onUseRepsChange: (Boolean) -> Unit,
    bodyweightEnabled: Boolean,
    onBodyweightEnabledChange: (Boolean) -> Unit,
    percentText: String,
    onPercentTextChange: (String) -> Unit,
    percentError: String?,
    showImmutabilityError: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Tracking",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (showImmutabilityError) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "This exercise already contains historical entries. Changing its tracking " +
                        "configuration would create incompatible historical data, so this change " +
                        "cannot be applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Track Weight", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = weightEnabled, onCheckedChange = onWeightEnabledChange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Track Distance", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = distanceEnabled, onCheckedChange = onDistanceEnabledChange)
        }

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
                    onCheckedChange = { onUseRepsChange(!it) },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Track Bodyweight", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = bodyweightEnabled, onCheckedChange = onBodyweightEnabledChange)
        }

        if (bodyweightEnabled) {
            Text(
                "Weight you log on sets counts as EXTRA (vest/belt). Leave empty for plain bodyweight sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = percentText,
                onValueChange = onPercentTextChange,
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
                        onClick = { onPercentTextChange(preset) },
                        label = { Text("$preset%") }
                    )
                }
            }
        }
    }
}
