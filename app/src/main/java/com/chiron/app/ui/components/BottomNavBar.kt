package com.chiron.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavTab(val label: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.DateRange),
    EXERCISES("Exercises", Icons.Default.FitnessCenter),
    TIMER("Timer", Icons.Default.Timer),
    VOLUME("Volume", Icons.Default.ShowChart)
}

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    isVolumeMode: Boolean = false
) {
    NavigationBar(modifier = modifier) {
        val visibleTabs = NavTab.entries.filter { it != NavTab.VOLUME }
        visibleTabs.forEach { tab ->
            val labelStr = if (tab == NavTab.HISTORY && isVolumeMode) "Volume" else tab.label
            val iconVec = if (tab == NavTab.HISTORY && isVolumeMode) Icons.Default.ShowChart else tab.icon
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(iconVec, contentDescription = labelStr) },
                label = { Text(labelStr) }
            )
        }
    }
}
