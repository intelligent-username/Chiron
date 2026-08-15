package com.chiron.feature.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import com.chiron.feature.goals.GoalWithProgress
import com.chiron.feature.goals.GoalsViewModel
import java.time.format.DateTimeFormatter

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.goalForm.collectAsState()

    androidx.activity.compose.BackHandler(enabled = state.isDetailOpen || state.showEditDialog) {
        if (state.showEditDialog) {
            viewModel.dismissDialog()
        } else {
            viewModel.closeDetail()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.isDetailOpen -> {
                // The detail REPLACES the list — it never overlays it.
                state.selectedGoal?.let { goal ->
                    GoalDetailScreen(
                        goal = goal,
                        exercises = state.selectedGoalExercises,
                        weekStart = state.currentWeekStart,
                        onEdit = { viewModel.openEditDialog(goal) },
                        onArchive = viewModel::archiveSelected,
                        onDelete = viewModel::deleteSelected,
                        onBack = viewModel::closeDetail,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                GoalsContent(
                    state = state,
                    onPrevWeek = viewModel::goToPreviousWeek,
                    onNextWeek = viewModel::goToNextWeek,
                    onOpenGoal = viewModel::openDetail
                )
            }
        }

        if (!state.isDetailOpen && !state.isLoading) {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New goal")
            }
        }

        if (state.showEditDialog) {
            GoalEditDialog(
                form = form,
                isEditing = state.selectedGoal != null,
                onNameChange = viewModel::updateFormName,
                onTargetChange = viewModel::updateFormTarget,
                onToggleExercise = viewModel::toggleExerciseSelection,
                onSearchChange = viewModel::updateExerciseSearchQuery,
                onSave = viewModel::saveGoal,
                onDismiss = viewModel::dismissDialog
            )
        }
    }
}

@Composable
private fun GoalsContent(
    state: com.chiron.feature.goals.GoalsUiState,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenGoal: (GoalWithProgress) -> Unit
) {
    val weekLabel = if (state.isAtCurrentWeek) {
        "This Week"
    } else {
        val end = state.currentWeekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        "${state.currentWeekStart.format(fmt)} – ${end.format(fmt)}"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.goals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No goals yet — create one with +",
                    color = CoolGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = state.goals.size,
                    key = { index -> state.goals[index].id }
                ) { index ->
                    val goal = state.goals[index]
                    GoalCard(
                        goal = goal,
                        index = index,
                        onOpen = onOpenGoal
                    )
                }
            }
        }

        GoalWeekNavigator(
            weekLabel = weekLabel,
            canGoPrev = !state.isAtFirstWeek,
            canGoNext = !state.isAtCurrentWeek,
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun GoalCard(
    goal: GoalWithProgress,
    index: Int,
    onOpen: (GoalWithProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val completed = goal.daysDone >= goal.weeklyTarget

    // Cycle through the theme's primary/secondary/tertiary accents so each card
    // gets its own hue — all derived from MaterialTheme so the miniplayer's
    // media-color matching flows through automatically.
    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val containers = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    val accent = accents[index % accents.size]
    val container = containers[index % containers.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .clickable { onOpen(goal) }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                GoalDonut(
                    progress = goal.daysDone.toFloat() / goal.weeklyTarget,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    accentColor = accent
                )
                Text(
                    text = goal.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${goal.daysDone}/${goal.weeklyTarget} days",
                        color = if (completed) accent else CoolGray,
                        fontSize = 13.sp,
                        fontFamily = MonospaceFamily
                    )
                    Text(
                        text = "${goal.exerciseCount} exercises",
                        color = CoolGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Goals-specific week navigator: a narrow centered pill docked at the bottom
 * (distinct from Volume's full-width top navigator). Shows "This Week" when
 * the current week is selected.
 */
@Composable
private fun GoalWeekNavigator(
    weekLabel: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onPrev, enabled = canGoPrev, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous week",
                modifier = Modifier.size(20.dp),
                tint = if (canGoPrev) MaterialTheme.colorScheme.primary else Color(0xFF30363D)
            )
        }
        Text(
            text = weekLabel,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next week",
                modifier = Modifier.size(20.dp),
                tint = if (canGoNext) MaterialTheme.colorScheme.primary else Color(0xFF30363D)
            )
        }
    }
}
