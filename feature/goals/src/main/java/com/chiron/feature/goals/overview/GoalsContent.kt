package com.chiron.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import java.time.format.DateTimeFormatter

@Composable
fun GoalsContent(
    state: GoalsUiState,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenGoal: (GoalWithProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekLabel = if (state.isAtCurrentWeek) {
        "This Week"
    } else {
        val end = state.currentWeekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        "${state.currentWeekStart.format(fmt)} – ${end.format(fmt)}"
    }

    Box(modifier = modifier.fillMaxSize()) {
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
