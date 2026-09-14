package com.chiron.feature.timer

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Top-level Timer screen coordinating the tab strip, active tab content,
 * and bottom control buttons.
 */
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    val shouldKeepScreenOn = state.isCountdownRunning || state.isStopwatchRunning || state.isMetronomeRunning
    DisposableEffect(shouldKeepScreenOn) {
        view.keepScreenOn = shouldKeepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.timerFinished.collect {
            val resId = context.resources.getIdentifier("beep", "raw", context.packageName)
            if (resId != 0) {
                val mediaPlayer = MediaPlayer.create(context, resId)
                mediaPlayer?.setOnCompletionListener { it.release() }
                mediaPlayer?.start()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom inline tab strip
        TimerTabStrip(
            activeTab = state.activeTab,
            onTabSelected = { viewModel.selectTab(it) }
        )

        // Content area for current tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (state.activeTab) {
                TimerTab.TIMER -> CountdownContent(viewModel = viewModel)
                TimerTab.STOPWATCH -> StopwatchContent(viewModel = viewModel)
                TimerTab.METRONOME -> MetronomeContent(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Shared bottom control buttons
        TimerControlButtons(
            state = state,
            viewModel = viewModel
        )
    }
}
