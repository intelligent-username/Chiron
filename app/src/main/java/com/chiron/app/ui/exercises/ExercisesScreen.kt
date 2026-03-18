package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.data.entities.Exercise
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.chiron.app.ui.components.IconPicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onOpenDetail: (Long) -> Unit,
    onSearchQueryChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>("default") }
    val context = LocalContext.current

    // Notify parent about search query changes
    LaunchedEffect(state.searchQuery) {
        onSearchQueryChange(state.searchQuery.isNotBlank())
    }

    // Handle back button: clear search if text exists
    val hasSearchQuery = state.searchQuery.isNotBlank()
    androidx.activity.compose.BackHandler(enabled = hasSearchQuery) {
        viewModel.updateSearchQuery("")
    }

    val focusManager = LocalFocusManager.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                label = { Text("Search exercises") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            if (state.showArchived) {
                Text(
                    text = "Tap an archived card to unarchive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            val displayedList = if (state.searchQuery.isNotBlank()) {
                state.searchResults
            } else {
                if (state.showArchived) state.archivedExercises else state.exercises
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(
                    items = displayedList,
                    key = { it.id } // Stable ID for performance
                ) { exercise ->
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
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New exercise")
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    newExerciseName = ""
                    selectedIcon = "default"
                },
                title = { Text("New Exercise") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = newExerciseName,
                            onValueChange = { newExerciseName = it },
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
                    TextButton(
                        onClick = {
                            if (newExerciseName.isNotBlank()) {
                                viewModel.createExercise(newExerciseName, iconName = selectedIcon)
                                showCreateDialog = false
                                newExerciseName = ""
                                selectedIcon = "default"
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateDialog = false
                            newExerciseName = ""
                            selectedIcon = "default"
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ExerciseGridItem(
    exercise: Exercise,
    onClick: () -> Unit,
    showArchived: Boolean = false,
    onUnarchive: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showUnarchiveConfirm by remember { mutableStateOf(false) }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("Unarchive Exercise") },
            text = { Text("Restore \"${exercise.name}\" to active exercises?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnarchive?.invoke()
                        showUnarchiveConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        onClick = if (showArchived) {
            { showUnarchiveConfirm = true }
        } else onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = if (showArchived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
             com.chiron.app.ui.components.ExerciseAsyncIcon(
                 iconName = exercise.iconName,
                 contentDescription = exercise.name,
                 modifier = Modifier.size(32.dp),
                 tint = Color.Unspecified
             )

             Spacer(Modifier.height(4.dp))

             Text(
                 text = exercise.name,
                 style = MaterialTheme.typography.labelSmall,
                 textAlign = TextAlign.Center,
                 maxLines = 3,
                 overflow = TextOverflow.Ellipsis,
                 lineHeight = 11.sp
             )

             if (showArchived) {
                 Spacer(Modifier.height(4.dp))
                 Text(
                     text = "Tap to unarchive",
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.primary,
                     fontSize = 9.sp,
                     textAlign = TextAlign.Center
                 )
             }
        }
    }
}
