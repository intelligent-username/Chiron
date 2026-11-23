package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import java.time.temporal.ChronoUnit

private data class FoodDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val servings: String = "1"
)

@Composable
fun CalorieScreen(
    history: CalorieHistory,
    weightState: BodyweightUiState,
    error: String?,
    onSaveEntries: (List<CalorieEntry>) -> Unit,
    onDelete: (String) -> Unit,
    onSaveTarget: (CalorieTarget) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var weeks by remember { mutableStateOf(2) }
    var editing by remember { mutableStateOf<List<FoodDraft>?>(null) }
    var showTargets by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<CalorieEntry?>(null) }
    val entries = history.entries.filter { it.date == date }
    val target = history.targetFor(date)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { date = date.minusDays(1) }) { Text("Previous") }
            Text(date.toString(), modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { date = date.plusDays(1) }) { Text("Next") }
        }
        val calories = entries.sumOf { it.calories }
        val protein = entries.sumOf { it.protein }
        Text("Calories: ${formatCalorieNumber(calories)}${target?.calories?.let { " / ${formatCalorieNumber(it)}" } ?: ""}")
        Text("Protein: ${formatCalorieNumber(protein)}${target?.protein?.let { " / ${formatCalorieNumber(it)}" } ?: ""} g")
        Row {
            Button(onClick = { editing = listOf(FoodDraft()) }) { Text("Log") }
            TextButton(onClick = { showTargets = true }) { Text("Set targets") }
            TextButton(onClick = { showTransfer = true }) { Text("Import / Export") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        val firstDate = history.entries.minOfOrNull { it.date }
        val maxWeeks = firstDate?.let {
            ((ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0) + 7) / 7).toInt().coerceIn(2, 520)
        } ?: 2
        Text("Range: $weeks weeks")
        Slider(value = weeks.coerceIn(1, maxWeeks).toFloat(), onValueChange = { weeks = it.roundToInt() },
            valueRange = 1f..maxWeeks.toFloat(), steps = (maxWeeks - 2).coerceAtLeast(0))
        CalorieComparison(history, weightState, date, weeks, primaryCalories = true)
        Spacer(Modifier.height(16.dp))
        Text("Entries", style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) Text("No foods logged for this day.")
        entries.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(entry.name.ifBlank { "Food" }, style = MaterialTheme.typography.titleSmall)
                Text("${formatCalorieNumber(entry.calories)} cal · ${formatCalorieNumber(entry.protein)} g protein · ${formatCalorieNumber(entry.servings)} servings")
                Row {
                    TextButton(onClick = {
                        editing = listOf(FoodDraft(entry.id, entry.name, entry.caloriesPerServing.toString(),
                            entry.proteinPerServing.toString(), entry.servings.toString()))
                    }) { Text("Edit") }
                    TextButton(onClick = { deleteEntry = entry }) { Text("Delete") }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
    editing?.let { drafts ->
        FoodDialog(date, drafts, onDismiss = { editing = null }, onSave = {
            onSaveEntries(it)
            editing = null
        })
    }
    if (showTargets) {
        TargetDialog(date, target, onDismiss = { showTargets = false }, onSave = {
            onSaveTarget(it)
            showTargets = false
        })
    }
    if (showTransfer) {
        CalorieTransferDialog(history, onDismiss = { showTransfer = false }, onImport = onSaveEntries)
    }
    deleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = { deleteEntry = null }, title = { Text("Delete entry?") },
            text = { Text("Delete ${entry.name.ifBlank { "this food" }} from $date?") },
            confirmButton = { TextButton(onClick = { onDelete(entry.id); deleteEntry = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("Cancel") } })
    }
}

@Composable
private fun CalorieTransferDialog(history: CalorieHistory, onDismiss: () -> Unit, onImport: (List<CalorieEntry>) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<CalorieHistory?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val conflicts = parsed?.entries?.count { entry -> history.entries.any { it.id == entry.id } } ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calorie text import / export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Copy your entries as text, or paste a previous export below. Targets are included in ZIP backups.")
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(CalorieHistory(entries = history.entries).toJson()))
                    message = "Calorie entries copied."
                }) { Text("Copy export") }
                OutlinedTextField(input, {
                    input = it
                    parsed = null
                }, label = { Text("Paste calorie export") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    runCatching { CalorieHistory.parse(input) }
                        .onSuccess { parsed = it; message = null }
                        .onFailure { message = "Invalid calorie export: ${it.message ?: "check the pasted text"}" }
                }) { Text("Preview") }
                parsed?.let { data ->
                    val count = data.entries.count { entry -> history.entries.any { it.id == entry.id } }
                    Text("${data.entries.size} entries, $count existing IDs.")
                    if (count > 0) Text("Choose how to resolve existing entries.")
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            parsed?.let { data ->
                Row {
                    TextButton(onClick = {
                        val existing = history.entries.map { it.id }.toSet()
                        onImport(data.entries.filterNot { it.id in existing })
                        onDismiss()
                    }) { Text(if (conflicts > 0) "Skip conflicts" else "Import") }
                    if (conflicts > 0) TextButton(onClick = { onImport(data.entries); onDismiss() }) { Text("Replace conflicts") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun FoodDialog(
    date: LocalDate,
    initial: List<FoodDraft>,
    onDismiss: () -> Unit,
    onSave: (List<CalorieEntry>) -> Unit
) {
    var rows by remember(initial) { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Log food · $date") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { index, row ->
                    fun change(updated: FoodDraft) { rows = rows.toMutableList().also { it[index] = updated } }
                    Text("Food ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(row.name, { change(row.copy(name = it)) }, label = { Text("Name (optional)") })
                    OutlinedTextField(row.calories, { change(row.copy(calories = it)) }, label = { Text("Calories per serving") })
                    OutlinedTextField(row.protein, { change(row.copy(protein = it)) }, label = { Text("Protein per serving (g)") })
                    OutlinedTextField(row.servings, { change(row.copy(servings = it)) }, label = { Text("Servings") })
                    if (rows.size > 1) TextButton(onClick = { rows = rows.filterIndexed { i, _ -> i != index } }) { Text("Remove row") }
                }
                TextButton(onClick = { rows = rows + FoodDraft() }) { Text("+ Add row") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching {
                    rows.map { row ->
                        CalorieEntry(row.id, date, row.name.trim(), row.calories.toDouble(),
                            row.protein.toDouble(), row.servings.toDouble())
                    }
                }
                parsed.onSuccess(onSave).onFailure { message = "Enter non-negative calories and protein and a positive number of servings for every row." }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TargetDialog(date: LocalDate, target: CalorieTarget?, onDismiss: () -> Unit, onSave: (CalorieTarget) -> Unit) {
    var calories by remember { mutableStateOf(target?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(target?.protein?.toString() ?: "") }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Targets from $date") },
        text = {
            Column {
                Text("Leave a target blank to turn it off.")
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") })
                OutlinedTextField(protein, { protein = it }, label = { Text("Protein (g)") })
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching {
                CalorieTarget(date, calories.takeIf { it.isNotBlank() }?.toDouble(),
                    protein.takeIf { it.isNotBlank() }?.toDouble())
            }.onSuccess(onSave).onFailure { message = "Targets must be positive numbers or blank." }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun formatCalorieNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
