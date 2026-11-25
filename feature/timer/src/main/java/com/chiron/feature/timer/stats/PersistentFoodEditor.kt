package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.foodSuggestions
import java.time.LocalDate
import java.util.UUID

internal data class FoodDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val servings: String = "1"
)

internal data class FoodEditorSession(
    val date: LocalDate,
    val rows: List<FoodDraft>,
    val originalIds: Set<String>? = null
)

// Flat primitive records preserve raw input and the original replacement set independently.
private val FoodEditorSessionSaver = listSaver<FoodEditorSession?, Any>(
    save = { session ->
        if (session == null) {
            listOf(false)
        } else {
            buildList {
                add(true)
                add(session.date.toEpochDay())
                add(session.originalIds != null)
                val originalIds = session.originalIds.orEmpty()
                add(originalIds.size)
                addAll(originalIds)
                add(session.rows.size)
                session.rows.forEach { row ->
                    addAll(listOf(row.id, row.name, row.calories, row.protein, row.servings))
                }
            }
        }
    },
    restore = { saved ->
        if (!(saved[0] as Boolean)) {
            null
        } else {
            var index = 1
            val date = LocalDate.ofEpochDay(saved[index++] as Long)
            val replacing = saved[index++] as Boolean
            val originalCount = saved[index++] as Int
            val originalIds = List(originalCount) { saved[index++] as String }.toSet()
            val rowCount = saved[index++] as Int
            val rows = List(rowCount) {
                FoodDraft(
                    id = saved[index++] as String,
                    name = saved[index++] as String,
                    calories = saved[index++] as String,
                    protein = saved[index++] as String,
                    servings = saved[index++] as String
                )
            }
            FoodEditorSession(date, rows, originalIds.takeIf { replacing })
        }
    }
)

@Composable
internal fun rememberFoodEditorState(): MutableState<FoodEditorSession?> =
    rememberSaveable(stateSaver = FoodEditorSessionSaver) {
        mutableStateOf<FoodEditorSession?>(null)
    }

@Composable
internal fun PersistentFoodEditor(
    session: FoodEditorSession,
    history: List<CalorieEntry>,
    onSessionChange: (FoodEditorSession) -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<CalorieEntry>, Set<String>?) -> Unit,
    onImmediateMessage: () -> Unit = {}
) {
    val rows = session.rows
    var message by remember(session.date, session.originalIds) { mutableStateOf<String?>(null) }
    // Keep removed slots only as empty, shrinking geometry: no outgoing input,
    // focus owner, suggestion popup, or submit callback is retained.
    val slots = remember { FoodRowSlots(rows.map { it.id }) }
    rows.forEach { if (it.id !in slots.ids) slots.ids += it.id }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Log food · ${session.date}", style = MaterialTheme.typography.titleMedium)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
             ) {
                 slots.ids.forEach { id ->
                     key(id) {
                       val index = rows.indexOfFirst { it.id == id }
                       val row = rows.getOrNull(index)
                       var suggestionMessage by remember { mutableStateOf(false) }
                       StatsFoodRowGeometry(row != null, id in slots.initialIds) {
                        if (row != null) {
                        fun change(updated: FoodDraft) {
                            onSessionChange(session.copy(rows = rows.toMutableList().also { it[index] = updated }))
                        }
                        var menuOpen by remember { mutableStateOf(false) }
                        val suggestions = remember(history, row.name) { foodSuggestions(history, row.name) }
                        Text("Food ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                row.name, {
                                    change(row.copy(name = it))
                                    menuOpen = true
                                    suggestionMessage = false
                                }, label = { Text("Name (optional)") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = menuOpen && suggestions.isNotEmpty(),
                                onDismissRequest = { menuOpen = false },
                                properties = PopupProperties(focusable = false)
                            ) {
                                suggestions.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(suggestion.name)
                                                Text(
                                                    "${suggestion.caloriesPerServing} kcal · ${suggestion.proteinPerServing} g protein per serving",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuOpen = false
                                             if (row.calories.isNotEmpty() || row.protein.isNotEmpty()) {
                                                 onImmediateMessage()
                                                 suggestionMessage = true
                                            } else {
                                                change(row.copy(
                                                    name = suggestion.name,
                                                    calories = suggestion.caloriesPerServing.toString(),
                                                    protein = suggestion.proteinPerServing.toString()
                                                ))
                                                suggestionMessage = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            row.calories, { change(row.copy(calories = it)) },
                            label = { Text("Calories per serving") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            row.protein, { change(row.copy(protein = it)) },
                            label = { Text("Protein per serving (g)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            row.servings, { change(row.copy(servings = it)) },
                            label = { Text("Servings") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                         if (rows.size > 1) TextButton(onClick = {
                            onSessionChange(session.copy(rows = rows.filterIndexed { i, _ -> i != index }))
                         }) { Text("Remove row") }
                        }
                       }
                       if (row != null && suggestionMessage) Text(
                           "Clear calories and protein before choosing a food.",
                           style = MaterialTheme.typography.bodySmall,
                           color = MaterialTheme.colorScheme.onSurfaceVariant
                       )
                    }
                }
                TextButton(onClick = {
                    onSessionChange(session.copy(rows = rows + FoodDraft()))
                }) { Text("+ Add row") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val parsed = runCatching {
                    rows.map { row ->
                        CalorieEntry(
                            row.id, session.date, row.name.trim(), row.calories.toDouble(),
                            row.protein.toDouble(), row.servings.toDouble()
                        )
                    }
                }
                 parsed.onSuccess { onSave(it, session.originalIds) }.onFailure {
                     onImmediateMessage()
                    message = "Enter non-negative calories and protein and a positive number of servings for every row."
                }
            }) { Text("Save") }
        }
    }
}

private class FoodRowSlots(initial: List<String>) {
    val initialIds = initial.toSet()
    val ids = initial.toMutableList()
}
