package com.chiron.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.MonospaceFamily
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.viewmodel.GoalWithProgress
import java.time.LocalDate

@Composable
fun GoalDetailScreen(
    goal: GoalWithProgress,
    exercises: List<Exercise>,
    weekStart: LocalDate,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Archive, contentDescription = "Archive", tint = CoolGray)
            }
        }

        val completed = goal.daysDone >= goal.weeklyTarget

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            GoalDonut(
                progress = goal.daysDone.toFloat() / goal.weeklyTarget,
                modifier = Modifier.size(180.dp),
                strokeWidth = 12.dp
            ) {
                Text(
                    text = "${goal.daysDone}/${goal.weeklyTarget}",
                    color = if (completed) ElectricBlue else Color.White,
                    fontSize = 20.sp,
                    fontFamily = MonospaceFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DayStrip(
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
            color = if (completed) ElectricBlue else CoolGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

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
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExerciseAsyncIcon(
                        iconName = exercise.iconName,
                        contentDescription = exercise.name,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = exercise.name,
                        color = CoolGray,
                        fontSize = 14.sp
                    )
                }
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
        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Goal?") },
            text = { Text("This permanently deletes \"${goal.name}\" and removes it from your goals. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DayStrip(
    weekStart: LocalDate,
    dayStatus: Map<LocalDate, Boolean>
) {
    val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dayLetters.forEachIndexed { index, letter ->
            val date = weekStart.plusDays(index.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = letter,
                    color = CoolGray,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFamily
                )
                Spacer(modifier = Modifier.height(6.dp))
                DayDot(
                    done = dayStatus[date] == true,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
