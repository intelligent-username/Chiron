package com.chiron.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.chiron.app.data.entities.SetEntry

/**
 * Dialog for editing the weight and reps of a single [SetEntry].
 *
 * Weight is displayed in the unit dictated by [displayInKg]; on save it is
 * always stored as lbs in the database (converted automatically when kg is active).
 */
@Composable
fun EditSetDialog(
    set: SetEntry,
    displayInKg: Boolean,
    onSave: (SetEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf(set.weightLbs?.toString() ?: "") }
    var reps by remember { mutableStateOf(set.reps?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(if (displayInKg) "Weight (kg)" else "Weight (lbs)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val weightLbs = weight.toDoubleOrNull()?.let {
                        if (displayInKg) it * 2.2046226218 else it
                    }
                    val repsInt = reps.toIntOrNull()
                    val newTimestamp = if (set.weightLbs == null && set.reps == null) {
                        System.currentTimeMillis()
                    } else {
                        set.timestampUtc
                    }
                    onSave(set.copy(weightLbs = weightLbs, reps = repsInt, timestampUtc = newTimestamp))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
