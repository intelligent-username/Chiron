package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.components.IconPicker

@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>("default") }

    fun reset() { name = ""; selectedIcon = "default" }

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
                if (name.isNotBlank()) {
                    onCreate(name, selectedIcon)
                    reset()
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { reset(); onDismiss() }) { Text("Cancel") }
        }
    )
}
