package com.chiron.app.ui.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.viewmodel.ExercisesViewModel

@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onOpenDetail: (Long) -> Unit,
    onSearchQueryChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.searchQuery) { onSearchQueryChange(state.searchQuery.isNotBlank()) }
    androidx.activity.compose.BackHandler(enabled = state.searchQuery.isNotBlank()) {
        viewModel.updateSearchQuery("")
    }

    val focusManager = LocalFocusManager.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                label = { Text("Search exercises") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )


            Spacer(Modifier.height(12.dp))

            val displayedList = if (state.searchQuery.isNotBlank()) state.searchResults
            else if (state.showArchived) state.archivedExercises else state.exercises

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                items(items = displayedList, key = { it.id }) { exercise ->
                    ExerciseGridItem(
                        exercise = exercise,
                        onClick = { onOpenDetail(exercise.id) },
                        showArchived = state.showArchived,
                        onUnarchive = { viewModel.unarchiveExercise(exercise.id) }
                    )
                }
            }
        }

        if (!state.showArchived) {
            FloatingActionButton(onClick = { showCreateDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New exercise")
            }
        }

        if (showCreateDialog) {
            CreateExerciseDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, icon, config ->
                    viewModel.createExercise(name, iconName = icon, config = config)
                    showCreateDialog = false
                }
            )
        }
    }
}
