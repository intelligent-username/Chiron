package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
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
            // Flat borderless search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SolidSlate)
                    .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CoolGray
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(ElectricBlue),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    "Search exercises",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CoolGray
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp), tint = CoolGray)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val displayedList = if (state.searchQuery.isNotBlank()) state.searchResults
            else if (state.showArchived) state.archivedExercises else state.exercises

            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
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
            CreateExerciseDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, icon, description, config ->
                    viewModel.createExercise(name, iconName = icon, description = description, config = config)
                    showCreateDialog = false
                }
            )
        }
    }
}
