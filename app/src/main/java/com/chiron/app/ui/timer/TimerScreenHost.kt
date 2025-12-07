package com.chiron.app.ui.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chiron.app.viewmodel.TimerViewModel

@Composable
fun TimerScreenHost(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    TimerScreen(viewModel = viewModel, modifier = modifier)
}
