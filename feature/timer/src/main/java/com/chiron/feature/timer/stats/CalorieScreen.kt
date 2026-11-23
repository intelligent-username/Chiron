package com.chiron.feature.timer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.components.WeekNavigator

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
    var graphEnd by remember { mutableStateOf(LocalDate.now()) }
    var editingDate by remember { mutableStateOf(date) }
    var editing by remember { mutableStateOf<List<FoodDraft>?>(null) }
    var showTargets by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<CalorieEntry?>(null) }
    val entries = remember(history.entries) { CalorieGraphCalculator.history(history.entries) }
    val today = LocalDate.now()
    val firstDate = history.entries.minOfOrNull { it.date }
    val maxWeeks = firstDate?.let {
        ((ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) + 7) / 7).toInt().coerceIn(2, 520)
    } ?: 2
    val displayedWeeks = weeks.coerceIn(1, maxWeeks)
    val graphWindow = remember(graphEnd, displayedWeeks, history.entries, today) {
        CalorieGraphCalculator.window(graphEnd, displayedWeeks, history.entries, today)
    }
    val target = history.targetFor(date)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (date == LocalDate.now()) "Today" else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium)
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { date = date.plusDays(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Calorie options") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Daily targets") }, onClick = { showMenu = false; showTargets = true })
                        DropdownMenuItem(text = { Text("Import / export") }, onClick = { showMenu = false; showTransfer = true })
                        if (date != LocalDate.now()) DropdownMenuItem(text = { Text("Back to today") },
                            onClick = { showMenu = false; date = LocalDate.now() })
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Crossfade(targetState = date, animationSpec = tween(180), label = "dailyNutrition") { shownDate ->
                    val dailyEntries = history.entries.filter { it.date == shownDate }
                    val dailyTarget = history.targetFor(shownDate)
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NutritionTotal("Calories", dailyEntries.sumOf { it.calories }, dailyTarget?.calories, "kcal", true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NutritionTotal("Protein", dailyEntries.sumOf { it.protein }, dailyTarget?.protein, "g", false)
                    }
                }
            }
        }
        item {
            Button(onClick = { editingDate = date; editing = listOf(FoodDraft()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Log food", modifier = Modifier.padding(start = 8.dp))
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalorieComparison(history, weightState, graphWindow.end, displayedWeeks)
                    Text("Range: $displayedWeeks week${if (displayedWeeks == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = displayedWeeks.toFloat(), onValueChange = {
                        weeks = it.roundToInt()
                        graphEnd = today
                    }, valueRange = 1f..maxWeeks.toFloat(), steps = (maxWeeks - 2).coerceAtLeast(0))
                }
            }
        }
        item {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            WeekNavigator(
                weekLabel = "${graphWindow.start.format(formatter)} – ${graphWindow.end.format(formatter)}",
                canGoPrev = graphWindow.canGoPrevious,
                canGoNext = graphWindow.canGoNext,
                onPrev = { graphEnd = graphWindow.previous().end },
                onNext = { graphEnd = graphWindow.next().end }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Food log", style = MaterialTheme.typography.titleMedium)
                Text("${entries.size} entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entries.isEmpty()) Text("No food history yet. Log a food or import your history from the options menu.",
                modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        items(entries, key = { it.id }) { entry ->
            Text(entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CalorieEntryRow(entry, onEdit = {
                        editingDate = entry.date
                        editing = listOf(FoodDraft(entry.id, entry.name, entry.caloriesPerServing.toString(),
                            entry.proteinPerServing.toString(), entry.servings.toString()))
            }, onDelete = { deleteEntry = entry })
        }
    }
    editing?.let { drafts ->
        FoodDialog(editingDate, drafts, onDismiss = { editing = null }, onSave = {
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
        CalorieTransferDialog(history, onDismiss = { showTransfer = false }, onImport = { imported ->
            onSaveEntries(imported)
            imported.maxOfOrNull { it.date }?.let { importedDate ->
                date = importedDate
                graphEnd = importedDate
            }
        })
    }
    deleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = { deleteEntry = null }, title = { Text("Delete entry?") },
            text = { Text("Delete ${entry.name.ifBlank { "this food" }} from ${entry.date}?") },
            confirmButton = { TextButton(onClick = { onDelete(entry.id); deleteEntry = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("Cancel") } })
    }
}

@Composable
private fun NutritionTotal(label: String, value: Double, target: Double?, unit: String, prominent: Boolean) {
    val progress by animateFloatAsState(
        targetValue = target?.let { (value / it).toFloat().coerceIn(0f, 1f) } ?: 0f,
        animationSpec = tween(220), label = "nutritionProgress"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatCalorieNumber(value), fontFamily = MonospaceFamily,
                        style = if (prominent) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge)
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(target?.let { "/ ${formatCalorieNumber(it)} $unit" } ?: "No target",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (target != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CalorieEntryRow(entry: CalorieEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember(entry.id) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name.ifBlank { "Food" }, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatCalorieNumber(entry.protein)} g protein · ${formatCalorieNumber(entry.servings)} servings",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${formatCalorieNumber(entry.calories)} kcal", fontFamily = MonospaceFamily,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${entry.name.ifBlank { "food entry" }}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
    }
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
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { index, row ->
                    fun change(updated: FoodDraft) { rows = rows.toMutableList().also { it[index] = updated } }
                    Text("Food ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(row.name, { change(row.copy(name = it)) }, label = { Text("Name (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.calories, { change(row.copy(calories = it)) }, label = { Text("Calories per serving") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.protein, { change(row.copy(protein = it)) }, label = { Text("Protein per serving (g)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.servings, { change(row.copy(servings = it)) }, label = { Text("Servings") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
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
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave a target blank to turn it off.")
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(protein, { protein = it }, label = { Text("Protein (g)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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

internal fun formatCalorieNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
