package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chiron.core.common.UnitConversion
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.bodyweight.ImportDateStrategy
import com.chiron.core.database.bodyweight.WeightImportUnit

private val StubPreviewLbs = listOf(180.5, 179.8, 181.2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyweightImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (BodyweightImportConfig) -> Unit = {},
    displayInKg: Boolean = false
) {
    var unit by remember { mutableStateOf(WeightImportUnit.LBS) }
    var lineStrideText by remember { mutableStateOf("1") }
    var startCharText by remember { mutableStateOf("0") }
    var dateStrategy by remember { mutableStateOf(ImportDateStrategy.ONE_PER_DAY_BACKWARDS) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val config = remember(unit, lineStrideText, startCharText, dateStrategy) {
        BodyweightImportConfig(
            unit = unit,
            lineStride = lineStrideText.toIntOrNull() ?: -1,
            startChar = startCharText.toIntOrNull() ?: -1,
            dateStrategy = dateStrategy
        )
    }
    val errors = remember(config) { config.validate() }
    val canConfirm = errors.isEmpty() && selectedFileName != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Bodyweight File", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UnitDropdown(unit = unit, onSelect = { unit = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = lineStrideText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) lineStrideText = it },
                        label = { Text("Every X lines") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = startCharText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) startCharText = it },
                        label = { Text("Start char Y") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                DateStrategyDropdown(strategy = dateStrategy, onSelect = { dateStrategy = it })
                OutlinedButton(
                    // TODO(DB): launch SAF GetContent picker here and set selectedFileName from uri.
                    // TODO(DB): read via repository importBodyweights(uri, config) after Tier-1 lifted.
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedFileName ?: "Choose file (stub)")
                }
                ImportPreviewStub(displayInKg = displayInKg)
                if (errors.isNotEmpty()) {
                    Text(
                        text = errors.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (selectedFileName == null) {
                    Text(
                        text = "Pick a file to enable Confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                // TODO(DB): wire onConfirm to BodyweightViewModel preview-confirm upsert path.
                onClick = { onConfirm(config) },
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(unit: WeightImportUnit, onSelect: (WeightImportUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = unit.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WeightImportUnit.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStrategyDropdown(
    strategy: ImportDateStrategy,
    onSelect: (ImportDateStrategy) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = strategy.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date strategy") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImportDateStrategy.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ImportPreviewStub(displayInKg: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Preview (stub, first 3 rows)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StubPreviewLbs.forEach { lbs ->
            Text(
                text = UnitConversion.formatWeight(lbs, displayInKg),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Text(
            text = "0 lines could not be read (stub error count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
