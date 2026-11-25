package com.chiron.app.ui.topbar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            val title = when (selectedTab) {
                NavTab.HISTORY -> if (isVolumeMode) "Volume" else "History"
                NavTab.EXERCISES -> if (isGoalsMode) "Goals" else "Exercises"
                NavTab.TIMER -> if (isBodyweightMode) "Stats" else "Timer"
            }
            val toggle = when (selectedTab) {
                NavTab.HISTORY -> onToggleVolumeMode
                NavTab.EXERCISES -> onToggleGoalsMode
                NavTab.TIMER -> onToggleBodyweightMode
            }
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = toggle
                ).semantics { contentDescription = title },
                contentAlignment = Alignment.CenterStart
            ) {
                Crossfade(
                    targetState = title,
                    modifier = Modifier.clearAndSetSemantics {},
                    animationSpec = tween(durationMillis = 150, delayMillis = 0),
                    label = "modeTitle"
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
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
