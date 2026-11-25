package com.chiron.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiron.core.common.DateUtils
import com.chiron.core.model.WorkoutSession
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutCard(
    workout: WorkoutSession,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    volumeLbs: Double? = null,
    displayInKg: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = workout.dayTag.ifBlank { "Untitled Workout" },
                    modifier = Modifier.workoutPreviewAnchor(workout.id, "title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateUtils.formatWorkoutCardDate(workout),
                    modifier = Modifier.workoutPreviewAnchor(workout.id, "date"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CoolGray
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .align(if (volumeLbs == null) Alignment.Bottom else Alignment.Top),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (volumeLbs != null) {
                    WorkoutVolumeTag(volumeLbs = volumeLbs, displayInKg = displayInKg,
                        modifier = Modifier.workoutPreviewAnchor(workout.id, "volume"))
                }
                if (workout.locationTag.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .workoutPreviewAnchor(workout.id, "location")
                            .clip(RoundedCornerShape(4.dp))
                            .background(ThinOutline)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = workout.locationTag,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
