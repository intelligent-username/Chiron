package com.chiron.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiron.app.di.ServiceLocator
import com.chiron.app.spotify.SpotifyManager
import com.chiron.app.ui.ChironApp
import com.chiron.app.ui.theme.ChironTheme
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.TimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChironTheme {
                val historyViewModel: HistoryViewModel = viewModel(factory = ServiceLocator.historyViewModelFactory)
                val exercisesViewModel: ExercisesViewModel = viewModel(factory = ServiceLocator.exercisesViewModelFactory)
                val timerViewModel: TimerViewModel = viewModel(factory = ServiceLocator.timerViewModelFactory)

                ChironApp(
                    historyViewModel = historyViewModel,
                    exercisesViewModel = exercisesViewModel,
                    timerViewModel = timerViewModel,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SpotifyManager.cancelScheduledDisconnect()
        lifecycleScope.launch {
            val enabled = ServiceLocator.userSettingsRepository.spotifyEnabledFlow.first()
            if (enabled) SpotifyManager.connect(this@MainActivity)
        }
    }

    override fun onStop() {
        super.onStop()
        SpotifyManager.scheduleDisconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        SpotifyManager.disconnect()
    }
}
