package com.chiron.app.ui.timer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddPresetDialog(
    onDismiss: () -> Unit,
    onSave: (label: String, durationSeconds: Int) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("1") }
    var seconds by remember { mutableStateOf("0") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val min = minutes.toIntOrNull() ?: 0
    val sec = seconds.toIntOrNull() ?: 0
    val totalSeconds = min * 60 + sec
    val isValid = label.isNotBlank() && totalSeconds > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Timer Preset", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; errorMessage = null },
                    label = { Text("Preset Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("e.g., Two Minute Timer") },
                    isError = label.isBlank() && errorMessage != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) minutes = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(":", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = {
                            if (it.isEmpty() || (it.all { c -> c.isDigit() } && it.toIntOrNull()?.let { s -> s <= 59 } != false))
                                seconds = it
                        },
                        label = { Text("Sec") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValid) {
                        errorMessage = when {
                            label.isBlank() -> "Please enter a preset name"
                            totalSeconds == 0 -> "Duration must be greater than 0"
                            else -> "Invalid input"
                        }
                        return@Button
                    }
                    onSave(label, totalSeconds)
                },
                shape = RoundedCornerShape(12.dp),
                enabled = isValid
            ) { Text("Save Preset") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
