package com.chiron.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.prefs.DistanceUnit
import com.chiron.app.util.UnitConversion

/**
 * Config-driven dialog for editing a single [SetEntry].
 *
 * Shows only the fields enabled by [exercise]'s tracking config.
 * Placeholder sets (all enabled metrics null) are allowed; partial fills are blocked.
 *
 * Storage is always canonical (lbs, seconds, meters). Conversion happens at UI boundary.
 */
@Composable
fun EditSetDialog(
    set: SetEntry,
    exercise: Exercise,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    onSave: (SetEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasWeight   = exercise.isWeightBased == 1
    val hasReps     = exercise.isRepBased == 1
    val hasTime     = exercise.isTimeBased == 1
    val hasDist     = exercise.isDistanceBased == 1

    // Initialise fields from set values, converting to display units for the UI
    var weightField by remember {
        mutableStateOf(
            if (hasWeight && set.weightLbs != null) {
                val v = if (displayInKg) UnitConversion.lbsToDisplayKg(set.weightLbs) else set.weightLbs
                UnitConversion.formatNumber(v)
            } else ""
        )
    }
    var repsField by remember { mutableStateOf(set.reps?.toString() ?: "") }
    var timeField by remember {
        // Show as seconds for editing (simpler for users)
        mutableStateOf(set.durationSeconds?.toString() ?: "")
    }
    var distanceField by remember {
        mutableStateOf(
            if (hasDist && set.distanceMeters != null) {
                val v = if (distanceUnit == DistanceUnit.FEET)
                    UnitConversion.metersToFeet(set.distanceMeters) else set.distanceMeters
                UnitConversion.formatNumber(v)
            } else ""
        )
    }

    // Validate: either all enabled metrics are blank (placeholder) or all must be valid
    fun validate(): Boolean {
        val weightOk  = !hasWeight  || weightField.isBlank()   || (weightField.toDoubleOrNull() ?: -1.0) >= 0
        val repsOk    = !hasReps    || repsField.isBlank()      || (repsField.toIntOrNull() ?: -1) >= 0
        val timeOk    = !hasTime    || timeField.isBlank()      || (timeField.toIntOrNull() ?: -1) >= 0
        val distOk    = !hasDist    || distanceField.isBlank()  || (distanceField.toDoubleOrNull() ?: -1.0) >= 0

        val allBlank = (!hasWeight || weightField.isBlank()) &&
                (!hasReps || repsField.isBlank()) &&
                (!hasTime || timeField.isBlank()) &&
                (!hasDist || distanceField.isBlank())

        val allFilled = (!hasWeight || weightField.isNotBlank()) &&
                (!hasReps || repsField.isNotBlank()) &&
                (!hasTime || timeField.isNotBlank()) &&
                (!hasDist || distanceField.isNotBlank())

        return (allBlank || allFilled) && weightOk && repsOk && timeOk && distOk
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasWeight) {
                    OutlinedTextField(
                        value = weightField,
                        onValueChange = { weightField = it },
                        label = { Text(if (displayInKg) "Weight (kg)" else "Weight (lbs)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                if (hasReps) {
                    OutlinedTextField(
                        value = repsField,
                        onValueChange = { repsField = it },
                        label = { Text("Reps") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (hasTime) {
                    OutlinedTextField(
                        value = timeField,
                        onValueChange = { timeField = it },
                        label = { Text("Duration (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (hasDist) {
                    OutlinedTextField(
                        value = distanceField,
                        onValueChange = { distanceField = it },
                        label = { Text("Distance (${distanceUnit.displayLabel.lowercase()})") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!validate()) return@TextButton

                    // Determine if this was previously a placeholder
                    val wasPlaceholder = set.weightLbs == null && set.reps == null &&
                            set.durationSeconds == null && set.distanceMeters == null

                    val weightLbs = weightField.toDoubleOrNull()?.let {
                        if (displayInKg) UnitConversion.kgToLbs(it) else it
                    }
                    val repsInt = repsField.toIntOrNull()
                    val durationSec = timeField.toIntOrNull()
                    val distMeters = distanceField.toDoubleOrNull()?.let {
                        if (distanceUnit == DistanceUnit.FEET) UnitConversion.feetToMeters(it) else it
                    }

                    // A set is newly completed if it was a placeholder and now has any metric
                    val isNowCompleted = wasPlaceholder &&
                            (weightLbs != null || repsInt != null || durationSec != null || distMeters != null)

                    val newTimestamp = if (isNowCompleted) System.currentTimeMillis() else set.timestampUtc

                    onSave(
                        set.copy(
                            weightLbs = weightLbs,
                            reps = repsInt,
                            durationSeconds = durationSec,
                            distanceMeters = distMeters,
                            timestampUtc = newTimestamp
                        )
                    )
                },
                enabled = validate()
            ) { Text("Save", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Cancel") }
            }
        }
    )
}
