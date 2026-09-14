package com.chiron.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chiron.core.spotify.MiniPlayerBar
import com.chiron.core.ui.components.BottomNavBar
import com.chiron.core.ui.components.NavTab
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

/**
 * Encapsulates the bottom navigation bar and conditionally integrates the Spotify mini-player.
 */
@Composable
fun BottomBarContainer(
    selectedTab: NavTab,
    selectedTabFraction: Float,
    isVolumeMode: Boolean,
    isGoalsMode: Boolean,
    isBodyweightMode: Boolean,
    spotifyEnabled: Boolean,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (spotifyEnabled) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
        ) {
            MiniPlayerBar(drawBackgroundAndBorder = false)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ThinOutline)
            )
            BottomNavBar(
                selectedTab = selectedTab,
                selectedTabFraction = selectedTabFraction,
                isVolumeMode = isVolumeMode,
                isGoalsMode = isGoalsMode,
                isBodyweightMode = isBodyweightMode,
                drawBackgroundAndBorder = false,
                onTabSelected = onTabSelected
            )
        }
    } else {
        BottomNavBar(
            modifier = modifier,
            selectedTab = selectedTab,
            selectedTabFraction = selectedTabFraction,
            isVolumeMode = isVolumeMode,
            isGoalsMode = isGoalsMode,
            isBodyweightMode = isBodyweightMode,
            drawBackgroundAndBorder = true,
            onTabSelected = onTabSelected
        )
    }
}
