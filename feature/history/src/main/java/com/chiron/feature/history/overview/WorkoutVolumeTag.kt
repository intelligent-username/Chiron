package com.chiron.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.core.common.formatWorkoutVolume
import com.chiron.core.ui.theme.ThinOutline

@Composable
internal fun WorkoutVolumeTag(
    volumeLbs: Double,
    displayInKg: Boolean,
    modifier: Modifier = Modifier
) {
    val label = formatWorkoutVolume(volumeLbs, displayInKg)
    val unit = if (displayInKg) "kg" else "lbs"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ThinOutline)
            .clearAndSetSemantics { contentDescription = "Workout volume: $label $unit" }
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
