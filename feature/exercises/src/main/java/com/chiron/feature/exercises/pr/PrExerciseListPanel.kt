package com.chiron.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.model.Exercise
import com.chiron.core.ui.components.ExerciseAsyncIcon
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@Composable
fun PrExerciseListPanel(
    uiState: ExercisesUiState,
    exercisesWithPrs: List<Exercise>,
    selectedExercise: Exercise?,
    focusManager: FocusManager,
    viewModel: ExercisesViewModel,
    onSelectExercise: (Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedList = if (uiState.prSearchQuery.isNotBlank()) {
        val ids = exercisesWithPrs.map { it.id }.toSet()
        uiState.prSearchResults.filter { it.id in ids }
    } else {
        exercisesWithPrs
    }

    Column(modifier = modifier.width(180.dp).fillMaxHeight()) {
        // Flat borderless search input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = CoolGray
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = uiState.prSearchQuery,
                onValueChange = viewModel::updatePrSearchQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(ElectricBlue),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (uiState.prSearchQuery.isEmpty()) {
                            Text(
                                "Search",
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolGray
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (uiState.prSearchQuery.isNotBlank()) {
                IconButton(onClick = { viewModel.clearPrSearch() }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp), tint = CoolGray)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (displayedList.isEmpty()) {
                item {
                    Text(
                        text = if (uiState.prSearchQuery.isNotBlank()) "No matching exercises" else "No PRs yet.\nFinish some sets!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
            items(displayedList) { exercise ->
                val isSelected = selectedExercise?.id == exercise.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) SolidSlate else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = ThinOutline,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectExercise(exercise) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExerciseAsyncIcon(
                            iconName = exercise.iconName,
                            contentDescription = exercise.name,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
