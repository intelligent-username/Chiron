package com.chiron.feature.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@Composable
fun GoalCard(
    goal: GoalWithProgress,
    index: Int,
    onOpen: (GoalWithProgress) -> Unit,
    modifier: Modifier = Modifier,
    motion: GoalMotionState? = null,
    progressKey: Any? = null
) {
    val completed = goal.daysDone >= goal.weeklyTarget

    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val accent = accents[index % accents.size]
    val density = LocalDensity.current
    val radius = with(density) { 16.dp.toPx() }
    val stroke = with(density) { 6.dp.toPx() }
    val progress = if (goal.weeklyTarget > 0) {
        (goal.daysDone.toFloat() / goal.weeklyTarget).coerceIn(0f, 1f)
    } else 0f
    val hidden = motion?.hides(goal.id, false) == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 184.dp)
            .aspectRatio(1f)
            .goalAnchor(motion, goal.id, false, false, radius, stroke, progress, accent),
        colors = CardDefaults.cardColors(containerColor = if (hidden) Color.Transparent else SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = if (hidden) null else BorderStroke(1.dp, ThinOutline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onOpen(goal) }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Week pagination replaces the ring at its actual value immediately.
                key(progressKey) {
                    GoalDonut(
                        progress = progress,
                        modifier = Modifier.size(68.dp)
                            .goalAnchor(motion, goal.id, false, true, radius, stroke, progress, accent),
                        strokeWidth = 6.dp,
                        accentColor = accent,
                        drawRing = !hidden
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = goal.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${goal.daysDone}/${goal.weeklyTarget} days",
                        color = if (completed) accent else CoolGray,
                        fontSize = 13.sp,
                        fontFamily = MonospaceFamily
                    )
                    Text(
                        text = "${goal.exerciseCount} exercises",
                        color = CoolGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
