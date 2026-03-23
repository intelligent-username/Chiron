package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.theme.PrGoldDark
import com.chiron.app.util.UnitConversion
import com.chiron.app.viewmodel.ExercisesViewModel

@Composable
internal fun PrDetailPanel(
    exercise: Exercise,
    viewModel: ExercisesViewModel,
    displayInKg: Boolean
) {
    val prs by viewModel.getPrsForExerciseFlow(exercise.id).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExerciseAsyncIcon(
                iconName = exercise.iconName,
                contentDescription = exercise.name,
                modifier = Modifier.size(36.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(12.dp))
            Text(text = exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider()

        if (prs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No PRs recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(prs) { pr -> PrRow(pr = pr, displayInKg = displayInKg) }
            }
        }
    }
}

@Composable
internal fun PrRow(pr: ExercisePr, displayInKg: Boolean) {
    val weightText = if (displayInKg) {
        "${formatPrWeight(UnitConversion.lbsToDisplayKg(pr.weightLbs))} kg"
    } else {
        "${formatPrWeight(pr.weightLbs)} lbs"
    }

    val repLabel = if (pr.reps == 1) "1 Rep Max" else "${pr.reps} Rep Max"

    val dateLabel = remember(pr.timestampUtc) {
        try {
            val date = java.time.Instant.ofEpochMilli(pr.timestampUtc)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            "$dow, $month ${date.dayOfMonth}"
        } catch (e: Exception) { "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrGold.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = repLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (dateLabel.isNotEmpty()) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Text(
            text = weightText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PrGoldDark
        )
    }
}

internal fun formatPrWeight(value: Double): String =
    String.format("%.2f", value).trimEnd('0').trimEnd('.')
