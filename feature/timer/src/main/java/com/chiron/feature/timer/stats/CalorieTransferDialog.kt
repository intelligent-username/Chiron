package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import com.chiron.core.common.CalorieTextImporter
import com.chiron.core.common.CalorieTextPreview
import com.chiron.core.ui.theme.MonospaceFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun CalorieTransferDialog(
    history: CalorieHistory,
    showCalorieGoal: Boolean,
    onDismiss: () -> Unit,
    onImport: (List<CalorieEntry>, List<CalorieTarget>, Boolean?) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var preview by remember { mutableStateOf<CalorieTextPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val existingById = remember(history.entries) { history.entries.associateBy { it.id } }
    val existingTargetDates = remember(history.targets) { history.targets.map { it.date }.toSet() }
    val conflicts = preview?.let { data ->
        data.entries.count { it.id in existingById } + data.targets.count { it.date in existingTargetDates }
    } ?: 0
    val canImport = !parsing && preview?.let {
        (it.entries.isNotEmpty() || it.targets.isNotEmpty() || it.showCalorieGoal != null) && it.errors.isEmpty()
    } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exporting) "Export calories" else "Import calories") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row {
                    TextButton(onClick = { exporting = false; message = null }) { Text("Import") }
                    TextButton(onClick = { exporting = true; message = null }) { Text("Export") }
                }
                if (exporting) {
                    val dailyText = remember(history.entries) { calorieDailyText(history.entries) }
                    Text("${history.entries.size} entries", style = MaterialTheme.typography.titleMedium)
                    Text("Daily text copies one dated total per day. JSON keeps every food row and the graph goal setting; targets are not exported.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clipboard.setText(AnnotatedString(dailyText))
                            message = "Daily text copied"
                        }) { Text("Copy text") }
                        TextButton(onClick = {
                            val json = org.json.JSONObject(CalorieHistory(entries = history.entries).toJson())
                                .put("showCalorieGoal", showCalorieGoal)
                            clipboard.setText(AnnotatedString(json.toString()))
                            message = "JSON export copied"
                        }) { Text("Copy JSON") }
                    }
                    Text(
                        text = if (dailyText.isBlank()) "Nothing to export yet." else dailyText,
                        fontFamily = MonospaceFamily,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState())
                    )
                } else {
                    Text("Paste a dated calorie / protein log or a previous export.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; preview = null; message = null },
                        enabled = !parsing,
                        label = { Text("Nutrition log") },
                        placeholder = { Text("2026 Counter\nJanuary 1st: 1340, 53") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 200.dp),
                        minLines = 5,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it; preview = null; message = null },
                        enabled = !parsing,
                        label = { Text("Year if no header") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(enabled = !parsing, onClick = {
                        val referenceYear = year.toIntOrNull()
                        if (referenceYear == null || referenceYear !in 1980..2100) {
                            message = "Enter a year between 1980 and 2100"
                        } else {
                            parsing = true
                            preview = null
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { CalorieTextImporter.parse(input, referenceYear) }
                                }
                                result.onSuccess { preview = it }
                                    .onFailure { message = it.message ?: "Could not read this log" }
                                parsing = false
                            }
                        }
                    }) { Text(if (parsing) "Reading…" else "Preview import") }
                    preview?.let { data ->
                        HorizontalDivider()
                        Text("${data.entries.size} entries · ${data.targets.size} targets · $conflicts conflicts", style = MaterialTheme.typography.titleSmall)
                        Text("${data.ignoredLines} headers, averages and weight rows ignored",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (data.entries.isNotEmpty()) {
                            LazyColumn(
                                Modifier.fillMaxWidth().heightIn(max = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(data.entries) { entry ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(entry.date.toString(), style = MaterialTheme.typography.labelMedium)
                                        Text("${formatCalorieNumber(entry.calories)} kcal · ${formatCalorieNumber(entry.protein)} g protein",
                                            fontFamily = MonospaceFamily, style = MaterialTheme.typography.bodySmall)
                                        existingById[entry.id]?.let { old ->
                                            Text("Existing: ${formatCalorieNumber(old.calories)} kcal · ${formatCalorieNumber(old.protein)} g",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (conflicts > 0) Text("Existing entries and targets will stay unchanged unless you choose Replace conflicts.",
                            style = MaterialTheme.typography.bodySmall)
                        data.errors.take(5).forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (data.errors.size > 5) Text("${data.errors.size - 5} more errors", color = MaterialTheme.colorScheme.error)
                        if (data.errors.isNotEmpty()) Text("Correct the marked rows, then preview again. Nothing has been imported.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (!exporting && canImport) {
                Column {
                    Button(onClick = {
                        val data = preview!!
                        onImport(data.entries.filterNot { it.id in existingById },
                            data.targets.filterNot { it.date in existingTargetDates }, data.showCalorieGoal)
                        onDismiss()
                    }) { Text(if (conflicts > 0) "Skip conflicts" else "Import ${preview!!.entries.size} entries") }
                    if (conflicts > 0) TextButton(onClick = {
                        val data = preview!!
                        onImport(data.entries, data.targets, data.showCalorieGoal)
                        onDismiss()
                    }) { Text("Replace conflicts") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** One parseable line per day so the daily text export re-imports through [CalorieTextImporter]. */
fun calorieDailyText(entries: List<CalorieEntry>): String =
    entries.groupBy { it.date }
        .toSortedMap()
        .entries
        .joinToString("\n") { (date, foods) ->
            "$date: ${formatCalorieNumber(foods.sumOf { it.calories })}, ${formatCalorieNumber(foods.sumOf { it.protein })}"
        }
