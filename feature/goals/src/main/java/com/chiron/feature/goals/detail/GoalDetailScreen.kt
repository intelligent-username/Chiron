package com.chiron.feature.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.model.Exercise
import com.chiron.core.ui.components.ExerciseAsyncIcon
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate

@Composable
fun GoalDetailScreen(
    goal: GoalWithProgress,
    allGoals: List<GoalWithProgress>,
    currentIndex: Int,
    exercises: List<Exercise>,
    weekStart: LocalDate,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    motion: GoalMotionState? = null
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val progress = if (goal.weeklyTarget > 0) (goal.daysDone.toFloat() / goal.weeklyTarget).coerceIn(0f, 1f) else 0f
    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val accent = accents[currentIndex.coerceAtLeast(0) % accents.size]
    val density = LocalDensity.current
    val radius = with(density) { 20.dp.toPx() }
    val stroke = with(density) { 10.dp.toPx() }
    val hidden = motion?.hides(goal.id, true) == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CoolGray)
            }
            Text(
                text = goal.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Archive, contentDescription = "Archive", tint = CoolGray)
            }
        }

        val completed = goal.daysDone >= goal.weeklyTarget

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
                .goalAnchor(motion, goal.id, true, false, radius, stroke, progress, accent),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (hidden) Color.Transparent else SolidSlate),
            border = if (hidden) null else BorderStroke(1.dp, ThinOutline.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (hidden) 0.dp else 2.dp)
        ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { if (currentIndex > 0) onNavigate(currentIndex - 1) },
                enabled = currentIndex > 0,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous goal", tint = if (currentIndex > 0) Color.White else CoolGray.copy(alpha = 0.4f))
            }
            GoalDonut(
                progress = progress,
                modifier = Modifier.size(160.dp)
                    .goalAnchor(motion, goal.id, true, true, radius, stroke, progress, accent),
                strokeWidth = 10.dp,
                accentColor = accent,
                drawRing = !hidden,
                animateProgress = false
            ) {
                Text(
                    text = "${goal.daysDone}/${goal.weeklyTarget}",
                    color = if (completed) accent else Color.White,
                    fontSize = 20.sp,
                    fontFamily = MonospaceFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { if (currentIndex < allGoals.size - 1) onNavigate(currentIndex + 1) },
                enabled = currentIndex < allGoals.size - 1,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next goal", tint = if (currentIndex < allGoals.size - 1) Color.White else CoolGray.copy(alpha = 0.4f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        GoalDayStrip(
            weekStart = weekStart,
            dayStatus = goal.dayStatus
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (completed) {
                "Target hit!"
            } else {
                val needed = (goal.weeklyTarget - goal.daysDone).coerceAtLeast(0)
                "${goal.daysDone} of ${goal.weeklyTarget} days — $needed more needed"
            },
            color = if (completed) accent else CoolGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) {
                Text("Edit")
            }
            TextButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Exercises",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (exercises.isEmpty()) {
            Text(
                text = "No exercises",
                color = CoolGray,
                fontSize = 14.sp
            )
        } else {
            exercises.forEach { exercise ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExerciseAsyncIcon(
                        iconName = exercise.iconName,
                        contentDescription = exercise.name,
                        modifier = Modifier.size(51.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = exercise.name,
                        color = CoolGray,
                        fontSize = 22.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showDeleteConfirmation) {
        GoalDeleteDialog(
            goalName = goal.name,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}
