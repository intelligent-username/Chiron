package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline

@Composable
fun ExerciseGridItem(
    exercise: Exercise,
    onClick: () -> Unit,
    showArchived: Boolean = false,
    onUnarchive: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerAlpha = if (showArchived) 0.4f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .alpha(containerAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(SolidSlate)
            .border(1.dp, ThinOutline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon takes up most of the space above
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ExerciseAsyncIcon(
                iconName = exercise.iconName,
                contentDescription = exercise.name,
                modifier = Modifier.size(56.dp),
                tint = Color.Unspecified
            )
        }

        // Name at the bottom
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}
