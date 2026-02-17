package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.data.entities.Exercise
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.chiron.app.ui.components.IconPicker
import androidx.compose.material3.HorizontalDivider

@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>("default") }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search exercises") }
            )

            Spacer(Modifier.height(12.dp))

            val list = if (state.searchQuery.isNotBlank()) state.searchResults else state.exercises

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(list) { exercise ->
                    ExerciseGridItem(
                        exercise = exercise,
                        onClick = { onOpenDetail(exercise.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New exercise")
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
        }
    }
}
