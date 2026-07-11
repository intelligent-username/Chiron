package com.chiron.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.CoolGray

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
    drawBackgroundAndBorder: Boolean = true
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
                    val labelStr = if (tab == NavTab.HISTORY && isVolumeMode) "Volume" else tab.label
                    val iconVec = if (tab == NavTab.HISTORY && isVolumeMode) Icons.Default.ShowChart else tab.icon
                    val isSelected = selectedTab == tab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.03f else 0.97f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                iconVec,
                                contentDescription = labelStr,
                                tint = if (isSelected) ElectricBlue else CoolGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                labelStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) ElectricBlue else CoolGray
                            )
                        }
                    }
                }
            }
        }
    }
}
