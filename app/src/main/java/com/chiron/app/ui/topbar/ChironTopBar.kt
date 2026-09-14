package com.chiron.app.ui.topbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.components.NavTab
import com.chiron.core.ui.theme.PrGold

/**
 * Top app bar handling animated title toggling and tab-specific action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChironTopBar(
    selectedTab: NavTab,
    isVolumeMode: Boolean,
    onToggleVolumeMode: () -> Unit,
    isGoalsMode: Boolean,
    onToggleGoalsMode: () -> Unit,
    isBodyweightMode: Boolean,
    onToggleBodyweightMode: () -> Unit,
    showArchivedExercises: Boolean,
    onToggleShowArchived: () -> Unit,
    onOpenPrScreen: () -> Unit,
    onRefreshBodyweight: () -> Unit,
    onOpenPresets: () -> Unit,
    onRefreshVolume: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            when (selectedTab) {
                NavTab.HISTORY -> {
                    AnimatedContent(
                        targetState = isVolumeMode,
                        label = "volume_toggle"
                    ) { mode ->
                        Text(
                            text = if (mode) "Volume" else "History",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleVolumeMode
                            )
                        )
                    }
                }
                NavTab.EXERCISES -> {
                    AnimatedContent(
                        targetState = isGoalsMode,
                        label = "goals_toggle"
                    ) { mode ->
                        Text(
                            text = if (mode) "Goals" else "Exercises",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleGoalsMode
                            )
                        )
                    }
                }
                NavTab.TIMER -> {
                    AnimatedContent(
                        targetState = isBodyweightMode,
                        label = "bodyweight_toggle"
                    ) { mode ->
                        Text(
                            text = if (mode) "Stats" else "Timer",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleBodyweightMode
                            )
                        )
                    }
                }
            }
        },
        actions = {
            when (selectedTab) {
                NavTab.EXERCISES -> {
                    if (!isGoalsMode) {
                        IconButton(onClick = onToggleShowArchived) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = if (showArchivedExercises) "Show active" else "Show archived",
                                tint = if (showArchivedExercises) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenPrScreen) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = "Personal Records", tint = PrGold)
                        }
                    }
                }
                NavTab.TIMER -> {
                    if (isBodyweightMode) {
                        IconButton(onClick = onRefreshBodyweight) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    } else {
                        IconButton(onClick = onOpenPresets) {
                            Icon(Icons.Default.Tune, contentDescription = "Presets")
                        }
                    }
                }
                NavTab.HISTORY -> {
                    if (isVolumeMode) {
                        IconButton(onClick = onRefreshVolume) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
    )
}
