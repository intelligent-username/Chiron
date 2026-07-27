package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.chiron.app.data.entities.Exercise1rmEstimate
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.MonospaceFamily
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.theme.PrGoldDark
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.util.UnitConversion
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.prefs.DistanceUnit
import com.chiron.app.data.pr.PrCategory
import com.chiron.app.data.pr.prCategory
import kotlin.math.roundToInt

@Composable
internal fun PrDetailPanel(
    exercise: Exercise,
    viewModel: ExercisesViewModel,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit
) {
    val prs by viewModel.getPrsForExerciseFlow(exercise.id).collectAsState(initial = emptyList())
    val estimate by viewModel.get1rmEstimateForExerciseFlow(exercise.id).collectAsState(initial = null)
    val category = exercise.prCategory()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            if (category == PrCategory.DISTANCE_WEIGHT && exercise.isRepBased == 1) {
                // Distance & Weight with Reps (e.g. box jumps)
                // Round to nearest hundredth of a metre to avoid floating-point duplicates
                fun Double.roundToHundredth() = kotlin.math.round(this * 100) / 100.0

                val distances = remember(prs) {
                    prs.map { (it.bucket / 100000.0).roundToHundredth() }
                        .filter { it > 0.0 }
                        .distinct()
                        .sorted()
                }
                var selectedDistance by remember(distances) {
                    mutableStateOf(distances.firstOrNull() ?: 0.0)
                }

                // Always show the bar — even with a single distance it labels the column
                DistanceSelectorBar(
                    distances = distances,
                    selectedDistance = selectedDistance,
                    onDistanceSelected = { selectedDistance = it },
                    distanceUnit = distanceUnit
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val filteredPrs = remember(prs, selectedDistance) {
                    prs.filter { (it.bucket / 100000.0).roundToHundredth() == selectedDistance }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPrs) { pr ->
                        // Decode reps from the compound bucket key (distance * 100000 + reps).
                        // Using `bucket % 100000` is wrong for distances < 1 m because the
                        // distance component is itself < 100000 and the modulo is a no-op.
                        // Instead: reconstruct the distance component, then subtract it.
                        val distMeters = (pr.bucket / 100000.0).roundToHundredth()
                        val reps = (pr.bucket - distMeters * 100000.0).roundToInt()
                        val title = if (reps == 1) "1 Rep Max" else "$reps Rep Max"
                        val value = UnitConversion.formatWeight(pr.record, displayInKg)
                        PrRow(title = title, value = value, timestampUtc = pr.timestampUtc)
                    }
                }
            } else {
                // Other categories
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (category == PrCategory.WEIGHT_REPS) {
                        estimate?.let { est ->
                            item {
                                Estimate1rmRow(estimate = est, displayInKg = displayInKg)
                            }
                        }
                    }

                    items(prs) { pr ->
                        val (title, value) = when (category) {
                            PrCategory.WEIGHT_REPS -> {
                                val reps = pr.repsInt
                                val label = if (reps == 1) "1 Rep Max" else "$reps Rep Max"
                                label to UnitConversion.formatWeight(pr.weightLbs, displayInKg)
                            }
                            PrCategory.TIME_WEIGHT -> {
                                val weight = pr.bucket
                                val label = "Duration for ${UnitConversion.formatWeight(weight, displayInKg)}"
                                val durationVal = UnitConversion.formatDuration(pr.record.toInt())
                                label to durationVal
                            }
                            PrCategory.DISTANCE_WEIGHT -> {
                                // Since rep-based was handled above, here it is non-rep based (bucket = weight, record = distance)
                                val weight = pr.bucket
                                val label = "Distance for ${UnitConversion.formatWeight(weight, displayInKg)}"
                                val distVal = UnitConversion.formatDistance(pr.record, distanceUnit)
                                label to distVal
                            }
                            PrCategory.DISTANCE_TIME -> {
                                // bucket = distance, record = duration
                                val distance = pr.bucket
                                val label = "Best Time for ${UnitConversion.formatDistance(distance, distanceUnit)}"
                                val durationVal = UnitConversion.formatDuration(pr.record.toInt())
                                label to durationVal
                            }
                            PrCategory.NONE -> "" to ""
                        }
                        if (title.isNotEmpty()) {
                            PrRow(title = title, value = value, timestampUtc = pr.timestampUtc)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DistanceSelectorBar(
    distances: List<Double>,
    selectedDistance: Double,
    onDistanceSelected: (Double) -> Unit,
    distanceUnit: DistanceUnit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(distances) { dist ->
            val isSelected = dist == selectedDistance
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) ElectricBlue else SolidSlate)
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = ThinOutline,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onDistanceSelected(dist) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = UnitConversion.formatDistance(dist, distanceUnit),
                    style = MaterialTheme.typography.labelMedium.copy(shadow = null),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else CoolGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun PrRow(
    title: String,
    value: String,
    timestampUtc: Long
) {
    val dateLabel = remember(timestampUtc) {
        try {
            val date = java.time.Instant.ofEpochMilli(timestampUtc)
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
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PrGoldDark
        )
    }
}

@Composable
internal fun Estimate1rmRow(estimate: Exercise1rmEstimate, displayInKg: Boolean) {
    val weightText = UnitConversion.formatWeight(estimate.estimateLbs, displayInKg)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Estimated 1RM",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = CoolGray
        )
        Text(
            text = weightText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ElectricBlue
        )
    }
}
