package com.chiron.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.TimerPreset

enum class TimerTab { TIMER, STOPWATCH }

data class TimerUiState(
    val activeTab: TimerTab = TimerTab.TIMER,

    // Countdown timer state
    val countdownSeconds: Int = 60,
    val countdownRemaining: Int = 60,
    val isCountdownRunning: Boolean = false,

    // Stopwatch state
    val stopwatchMillis: Long = 0L,
    val isStopwatchRunning: Boolean = false,
    val laps: List<Long> = emptyList(),

    // Presets
    val presets: List<TimerPreset> = emptyList()
)

class TimerViewModel(
    private val repository: ChironRepository
) : ViewModel() {

    private val highRefreshIntervalMs = 8L

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val _timerFinished = MutableSharedFlow<Unit>()
    val timerFinished: SharedFlow<Unit> = _timerFinished.asSharedFlow()

    private var countdownJob: Job? = null
    private var stopwatchJob: Job? = null

    init {
        // Load presets from database
        viewModelScope.launch {
            repository.timerPresetsFlow.collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab switching
    // ─────────────────────────────────────────────────────────────────────────

    fun selectTab(tab: TimerTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Countdown Timer
    // ─────────────────────────────────────────────────────────────────────────

    fun setCountdownPreset(seconds: Int) {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                countdownSeconds = seconds,
                countdownRemaining = seconds,
                isCountdownRunning = false
            )
        }
    }

    fun startCountdown() {
        if (_uiState.value.isCountdownRunning) return

        _uiState.update { it.copy(isCountdownRunning = true) }

        val startTime = System.currentTimeMillis()
        val totalDurationMs = _uiState.value.countdownRemaining * 1000L

        countdownJob = viewModelScope.launch {
            while (_uiState.value.isCountdownRunning) {
                val elapsedMs = System.currentTimeMillis() - startTime
                val remainingSeconds = (totalDurationMs - elapsedMs) / 1000

                if (remainingSeconds <= 0) {
                    _uiState.update { it.copy(countdownRemaining = 0, isCountdownRunning = false) }
                    _timerFinished.emit(Unit)
                    break
                }

                _uiState.update { it.copy(countdownRemaining = remainingSeconds.toInt()) }
                delay(100L) // Check 10x per second for smoother updates
            }
            _uiState.update { it.copy(isCountdownRunning = false) }
        }
    }

    fun pauseCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(isCountdownRunning = false) }
    }

    fun resetCountdown() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isCountdownRunning = false,
                countdownRemaining = it.countdownSeconds
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stopwatch
    // ─────────────────────────────────────────────────────────────────────────

    fun startStopwatch() {
        if (_uiState.value.isStopwatchRunning) return

        _uiState.update { it.copy(isStopwatchRunning = true) }

        val startTime = System.currentTimeMillis() - _uiState.value.stopwatchMillis

        stopwatchJob = viewModelScope.launch {
            while (_uiState.value.isStopwatchRunning) {
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.update { it.copy(stopwatchMillis = elapsed) }
                delay(highRefreshIntervalMs)
            }
        }
    }

    fun pauseStopwatch() {
        stopwatchJob?.cancel()
        _uiState.update { it.copy(isStopwatchRunning = false) }
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        _uiState.update {
            it.copy(
                isStopwatchRunning = false,
                stopwatchMillis = 0L,
                laps = emptyList()
            )
        }
    }

    fun recordLap() {
        val currentMillis = _uiState.value.stopwatchMillis
        _uiState.update { it.copy(laps = it.laps + currentMillis) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer Presets
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun addPreset(label: String, durationSeconds: Int) {
        val preset = TimerPreset(
            durationSeconds = durationSeconds,
            label = label
        )
        repository.insertTimerPreset(preset)
    }

    suspend fun updatePreset(preset: TimerPreset) {
        repository.updateTimerPreset(preset)
    }

    suspend fun deletePreset(preset: TimerPreset) {
        repository.deleteTimerPreset(preset)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        fun formatCountdown(seconds: Int): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return "%02d:%02d".format(mins, secs)
        }

        fun formatStopwatch(millis: Long): String {
            val totalSeconds = millis / 1000
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            val hundredths = (millis % 1000) / 10
            return "%02d:%02d.%02d".format(mins, secs, hundredths)
        }
    }

    class Factory(private val repository: ChironRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimerViewModel(repository) as T
        }
    }
}
