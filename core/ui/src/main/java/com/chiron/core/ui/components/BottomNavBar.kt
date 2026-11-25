package com.chiron.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.ThinOutline
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.CoolGray

enum class NavTab(val label: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.DateRange),
    EXERCISES("Exercises", Icons.Default.FitnessCenter),
    TIMER("Timer", Icons.Default.Timer)
}

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    selectedTabFraction: Float,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    isVolumeMode: Boolean = false,
    isGoalsMode: Boolean = false,
    isBodyweightMode: Boolean = false,
    drawBackgroundAndBorder: Boolean = true,
    exercisesEnabled: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (drawBackgroundAndBorder) {
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                } else {
                    Modifier
                }
            )
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / NavTab.entries.size
        val animatedOffset = tabWidth * selectedTabFraction

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .then(
                    if (drawBackgroundAndBorder) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolidSlate)
                            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            // Sliding indicator pill
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricBlue.copy(alpha = 0.1f))
                        .border(1.dp, ElectricBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                )
            }

            // Tabs
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavTab.entries.forEach { tab ->
                    val labelStr = when {
                        tab == NavTab.HISTORY && isVolumeMode -> "Volume"
                        tab == NavTab.EXERCISES && isGoalsMode -> "Goals"
                        tab == NavTab.TIMER && isBodyweightMode -> "Stats"
                        else -> tab.label
                    }
                    val iconVec = when {
                        tab == NavTab.HISTORY && isVolumeMode -> Icons.AutoMirrored.Filled.ShowChart
                        tab == NavTab.EXERCISES && isGoalsMode -> Icons.Default.Flag
                        tab == NavTab.TIMER && isBodyweightMode -> Icons.AutoMirrored.Filled.ShowChart
                        else -> tab.icon
                    }
                    val isSelected = selectedTab == tab

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                enabled = tab != NavTab.EXERCISES || exercisesEnabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab) }
                            ).semantics {
                                contentDescription = labelStr
                                selected = isSelected
                                role = Role.Tab
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Crossfade(
                                targetState = iconVec,
                                modifier = Modifier.size(22.dp).clearAndSetSemantics {},
                                animationSpec = tween(durationMillis = 150, delayMillis = 0),
                                label = "modeIcon"
                            ) { icon ->
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) ElectricBlue else CoolGray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Crossfade(
                                targetState = labelStr,
                                modifier = Modifier.clearAndSetSemantics {},
                                animationSpec = tween(durationMillis = 150, delayMillis = 0),
                                label = "modeLabel"
                            ) { label ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) ElectricBlue else CoolGray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
