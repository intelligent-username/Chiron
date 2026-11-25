package com.chiron.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieHistory
import com.chiron.core.common.CalorieTarget
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.components.WeekNavigator

private val CalorieDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) }
)

private data class PendingCalorieDayDeletion(val date: LocalDate, val entries: List<CalorieEntry>)

@Composable
fun CalorieScreen(
    history: CalorieHistory,
    weightState: BodyweightUiState,
    error: String?,
    onSaveEntries: (List<CalorieEntry>) -> Unit,
    onSaveDay: (List<CalorieEntry>, Set<String>) -> Unit,
    onDelete: (String) -> Unit,
    onSaveTarget: (CalorieTarget) -> Unit,
    graphModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val graphPreferences = remember(context) { context.getSharedPreferences("calorie_graph", android.content.Context.MODE_PRIVATE) }
    var showCalorieGoal by remember { mutableStateOf(graphPreferences.getBoolean("show_goal", true)) }
    var date by rememberSaveable(stateSaver = CalorieDateSaver) { mutableStateOf(LocalDate.now()) }
    var weeks by rememberSaveable { mutableStateOf(2) }
    var graphEnd by rememberSaveable(stateSaver = CalorieDateSaver) { mutableStateOf(LocalDate.now()) }
    var editorSession by rememberFoodEditorState()
    var editorGeneration by remember { mutableIntStateOf(0) }
    var editorHasImmediateMessage by remember { mutableStateOf(false) }
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var targetCalories by rememberSaveable { mutableStateOf("") }
    var targetProtein by rememberSaveable { mutableStateOf("") }
    var targetEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showTransfer by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDayDeletion by remember { mutableStateOf<PendingCalorieDayDeletion?>(null) }
    var deletionMessage by remember { mutableStateOf<String?>(null) }
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
    val target = history.globalTarget()
    val listState = rememberLazyListState()
    var historyPage by rememberSaveable { mutableStateOf(0) }
    var historyPageSize by rememberSaveable { mutableStateOf(10) }
    val dates = entries.map { it.date }.distinct().sortedDescending()
    val safeSize = historyPageSize.coerceIn(5, 50)
    val pages = ((dates.size + safeSize - 1) / safeSize).coerceAtLeast(1)
    val safeHistoryPage = historyPage.coerceIn(0, pages - 1)
    val visibleDates = dates.drop(safeHistoryPage * safeSize).take(safeSize)
    val editInHistory = editorSession?.let {
        it.originalIds != null && it.date in visibleDates
    } == true
    val editorTicket = editorGeneration
    val editor: @Composable () -> Unit = {
        editorSession?.let { session ->
            PersistentFoodEditor(session, history.entries,
                onSessionChange = {
                    if (editorTicket == editorGeneration && editorSession != null) editorSession = it
                },
                onDismiss = {
                    if (editorSession === session) {
                        editorSession = null
                        editorGeneration++
                        editorHasImmediateMessage = false
                    }
                },
                onSave = save@{ savedEntries, originalIds ->
                    if (editorSession !== session) return@save
                    editorSession = null
                    editorGeneration++
                    editorHasImmediateMessage = false
                    if (originalIds != null) onSaveDay(savedEntries, originalIds) else onSaveEntries(savedEntries)
                },
                onImmediateMessage = { editorHasImmediateMessage = true })
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "day") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }
                Column(Modifier.weight(1f).clickable {
                    showStatsDatePicker(context, date) { date = it }
                }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (date == LocalDate.now()) "Today" else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium)
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { date = date.plusDays(1).coerceAtMost(LocalDate.now()) }, enabled = date < today) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }
        }
        item(key = "totals") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                key(date) {
                    val dailyEntries = history.entries.filter { it.date == date }
                    val dailyTarget = target
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val calorieTotal: @Composable () -> Unit = {
                                NutritionTotal("Calories", dailyEntries.sumOf { it.calories }, dailyTarget?.calories, "kcal", true)
                            }
                            val proteinTotal: @Composable () -> Unit = {
                                NutritionTotal("Protein", dailyEntries.sumOf { it.protein }, dailyTarget?.protein, "g", false)
                            }
                            if (maxWidth >= 260.dp && LocalDensity.current.fontScale <= 1.2f) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f)) { calorieTotal() }
                                    Column(Modifier.weight(1f)) { proteinTotal() }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    calorieTotal()
                                    proteinTotal()
                                }
                            }
                        }
                             Box {
                                 IconButton(onClick = { showMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Settings, "Calorie settings") }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Daily targets") }, onClick = {
                                        showMenu = false
                                        targetCalories = target?.calories?.toString() ?: ""
                                        targetProtein = target?.protein?.toString() ?: ""
                                        targetEpochDay = (target?.date ?: LocalDate.now()).toEpochDay()
                                        showTargets = true
                                    })
                                    DropdownMenuItem(
                                        text = { Text("Graph Calorie Goal") },
                                        leadingIcon = { Checkbox(checked = showCalorieGoal, onCheckedChange = null) },
                                        onClick = {
                                            showCalorieGoal = !showCalorieGoal
                                            graphPreferences.edit().putBoolean("show_goal", showCalorieGoal).apply()
                                        }
                                    )
                                    DropdownMenuItem(text = { Text("Import / export") }, onClick = { showMenu = false; showTransfer = true })
                                    if (date != LocalDate.now()) DropdownMenuItem(text = { Text("Back to today") },
                                        onClick = { showMenu = false; date = LocalDate.now() })
                                }
                            }
                    }
                }
            }
        }
        item(key = "log") {
          StatsEditorGeometry(
              editorSession != null && !editInHistory,
              enabled = !editorHasImmediateMessage,
               resetKey = safeHistoryPage to safeSize
          ) {
            if (editorSession != null && !editInHistory) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(12.dp)) { editor() }
                }
            } else Button(onClick = {
                // Opening another slot must never discard an active raw draft.
                if (editorSession == null) {
                    editorGeneration++
                    editorSession = FoodEditorSession(date, listOf(FoodDraft()))
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = editorSession == null,
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Log food", modifier = Modifier.padding(start = 8.dp))
            }
          }
        }
        error?.let { message -> item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error) } }
        item(key = "graph") {
            CalorieComparison(
                history = history,
                state = weightState,
                end = graphWindow.end,
                weeks = displayedWeeks,
                maxWeeks = maxWeeks,
                onWeeksChange = {
                    weeks = it
                    graphEnd = today
                },
                showCalorieGoal = showCalorieGoal,
                modifier = graphModifier
            )
        }
        item(key = "range") {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            WeekNavigator(
                weekLabel = "${graphWindow.start.format(formatter)} – ${graphWindow.end.format(formatter)}",
                canGoPrev = graphWindow.canGoPrevious,
                canGoNext = graphWindow.canGoNext,
                onPrev = { graphEnd = graphWindow.previous().end },
                onNext = { graphEnd = graphWindow.next().end }
            )
        }
        item(key = "history") {
            CalorieHistoryList(
                entries = entries,
                editingDate = editorSession?.date?.takeIf { editInHistory },
                editor = editor,
                editorMotionEnabled = !editorHasImmediateMessage,
                controlledPage = historyPage,
                controlledPageSize = historyPageSize,
                onControlledPageChange = { historyPage = it },
                onControlledPageSizeChange = { historyPageSize = it; historyPage = 0 },
                onEdit = { selectedDate ->
                    if (editorSession != null) return@CalorieHistoryList
                    val dayEntries = history.entries.filter { it.date == selectedDate }
                    if (dayEntries.isNotEmpty()) {
                      editorGeneration++
                      editorSession = FoodEditorSession(
                        selectedDate,
                        dayEntries.map { entry ->
                            FoodDraft(entry.id, entry.name, entry.caloriesPerServing.toString(),
                                entry.proteinPerServing.toString(), entry.servings.toString())
                        },
                        dayEntries.map { it.id }.toSet()
                    )
                    }
                },
                onDelete = { selectedDate ->
                    val snapshot = history.entries.filter { it.date == selectedDate }
                        .sortedBy { it.id }.map { it.copy() }
                    deletionMessage = null
                    pendingDayDeletion = snapshot.takeIf { it.isNotEmpty() }?.let {
                        PendingCalorieDayDeletion(selectedDate, it)
                    }
                }
            )
        }
    }
    if (showTargets) {
        TargetDialog(targetCalories, targetProtein, LocalDate.ofEpochDay(targetEpochDay),
            onCaloriesChange = { targetCalories = it }, onProteinChange = { targetProtein = it },
            onDismiss = { showTargets = false }, onSave = {
            onSaveTarget(it)
            showTargets = false
        })
    }
    if (showTransfer) {
        CalorieTransferDialog(history, showCalorieGoal, onDismiss = { showTransfer = false }, onImport = { imported, targets, graphGoal ->
            onSaveEntries(imported)
            targets.forEach(onSaveTarget)
            graphGoal?.let {
                showCalorieGoal = it
                graphPreferences.edit().putBoolean("show_goal", it).apply()
            }
            imported.maxOfOrNull { it.date }?.let { importedDate ->
                date = importedDate.coerceAtMost(LocalDate.now())
                graphEnd = importedDate.coerceAtMost(LocalDate.now())
            }
        })
    }
    pendingDayDeletion?.let { snapshot ->
        fun closeDeletion() {
            pendingDayDeletion = null
            deletionMessage = null
        }
        AlertDialog(
            onDismissRequest = { closeDeletion() },
            title = { Text("Delete all foods for this day?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete all ${snapshot.entries.size} food entries for " +
                        "${snapshot.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))}? " +
                        "This cannot be undone.")
                    deletionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingDayDeletion !== snapshot) return@TextButton
                    val current = history.entries.filter { it.date == snapshot.date }
                        .sortedBy { it.id }.map { it.copy() }
                    if (current.isEmpty()) {
                        closeDeletion()
                    } else if (!calorieDayUnchanged(snapshot.date, snapshot.entries, history.entries)) {
                        pendingDayDeletion = PendingCalorieDayDeletion(snapshot.date, current)
                        deletionMessage = "Foods for this date changed. Review the updated count and confirm again."
                    } else {
                        closeDeletion()
                        onSaveDay(emptyList(), snapshot.entries.map { it.id }.toSet())
                    }
                }) { Text("Delete day", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { closeDeletion() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NutritionTotal(
    label: String, value: Double, target: Double?, unit: String, prominent: Boolean,
    trailing: @Composable () -> Unit = {}
) {
    val progress by animateFloatAsState(
        targetValue = target?.takeIf { it > 0 }?.let { (value / it).toFloat().coerceIn(0f, 1f) } ?: 0f,
        animationSpec = tween(StatsMotionMillis), label = "nutritionProgress"
    )
    val shownValue = statsPresentedNumber(value, unit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatCalorieNumber(shownValue), fontFamily = MonospaceFamily,
                         style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                target?.let {
                    Text("/ ${formatCalorieNumber(it)} $unit",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                trailing()
            }
        }
        if (target != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TargetDialog(
    calories: String, protein: String, targetDate: LocalDate,
    onCaloriesChange: (String) -> Unit, onProteinChange: (String) -> Unit,
    onDismiss: () -> Unit, onSave: (CalorieTarget) -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Daily targets") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave a target blank to turn it off.")
                OutlinedTextField(calories, onCaloriesChange, label = { Text("Calories") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(protein, onProteinChange, label = { Text("Protein (g)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching {
                CalorieTarget(maxOf(LocalDate.now(), targetDate), calories.takeIf { it.isNotBlank() }?.toDouble(),
                    protein.takeIf { it.isNotBlank() }?.toDouble())
            }.onSuccess(onSave).onFailure { message = "Targets must be positive numbers or blank." }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun formatCalorieNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
