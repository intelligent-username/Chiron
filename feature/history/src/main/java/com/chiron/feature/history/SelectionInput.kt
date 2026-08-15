package com.chiron.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * A dropdown + optional custom text field for picking from a list of options
 * or entering a new custom value.
 *
 * Displays an [OutlinedTextField] with the currently selected option (or "Custom…").
 * Tapping opens a dropdown of existing [options] plus a "Custom…" entry at the bottom.
 * When "Custom…" is selected, an additional text field appears for free-text entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionInput(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (selectedOption == "Custom") "Custom\u2026" else selectedOption,
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
                    text = { Text("Custom\u2026") },
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
