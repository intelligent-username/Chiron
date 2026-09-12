package com.chiron.feature.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chiron.feature.timer.TimerViewModel

@Composable
fun TimerScreenHost(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    TimerScreen(viewModel = viewModel, modifier = modifier)
}
