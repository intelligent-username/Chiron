package com.chiron.app.ui.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.components.ExerciseRow
import com.chiron.app.viewmodel.ExercisesViewModel

@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search exercises") }
        )

        Spacer(Modifier.height(12.dp))

        val list = if (state.searchQuery.isNotBlank()) state.searchResults else state.exercises

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    lastSetInfo = null,
                    onClick = { onOpenDetail(exercise.id) }
                )
            }
        }
    }
}
