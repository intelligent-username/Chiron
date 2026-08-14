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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.chiron.app.data.ChironRepository
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.service.MetronomeController

enum class TimerTab { TIMER, STOPWATCH, METRONOME }

data class TimerUiState(
    val activeTab: TimerTab = TimerTab.TIMER,

    // Countdown timer state
    val countdownSeconds: Int = 60,
    val countdownRemaining: Int = 60,
    val isCountdownRunning: Boolean = false,
    val isConstantCycling: Boolean = false,

    // Stopwatch state
    val stopwatchMillis: Long = 0L,
    val isStopwatchRunning: Boolean = false,
    val laps: List<Long> = emptyList(),

    // Metronome state
    val metronomeBpm: Int = 60,
    val metronomeTickAsset: String = "Tick1.mp3",
    val isMetronomeRunning: Boolean = false,

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

        // Mirror metronome controller state into the UI state so the UI stays
        // in sync when the metronome is started/paused from the notification.
        viewModelScope.launch {
            combine(
                MetronomeController.isRunning,
                MetronomeController.bpm,
                MetronomeController.tickAsset
            ) { running, bpm, asset -> Triple(running, bpm, asset) }
                .collect { (running, bpm, asset) ->
                    _uiState.update {
                        it.copy(
                            isMetronomeRunning = running,
                            metronomeBpm = bpm,
                            metronomeTickAsset = asset
                        )
                    }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab switching
    // ─────────────────────────────────────────────────────────────────────────

    fun selectTab(tab: TimerTab) {
        if (tab != TimerTab.METRONOME) {
            stopMetronome()
        }
        _uiState.update { it.copy(activeTab = tab) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metronome
    // ─────────────────────────────────────────────────────────────────────────

    fun setMetronomeBpm(bpm: Int) {
        MetronomeController.setBpm(bpm)
    }

    fun setMetronomeTickAsset(assetFileName: String) {
        MetronomeController.setTickAsset(assetFileName)
    }

    fun startMetronome() {
        MetronomeController.start()
    }

    fun pauseMetronome() {
        MetronomeController.pause()
    }

    fun stopMetronome() {
        MetronomeController.stop()
    }

    fun toggleMetronome() {
        MetronomeController.toggle()
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

    fun toggleConstantCycling() {
        _uiState.update { it.copy(isConstantCycling = !it.isConstantCycling) }
    }

    fun startCountdown() {
        if (_uiState.value.isCountdownRunning) return

        _uiState.update { it.copy(isCountdownRunning = true) }

        var startTime = System.currentTimeMillis()
        var totalDurationMs = _uiState.value.countdownRemaining * 1000L

        countdownJob = viewModelScope.launch {
            while (_uiState.value.isCountdownRunning) {
                val elapsedMs = System.currentTimeMillis() - startTime
                val remainingMs = totalDurationMs - elapsedMs

                if (remainingMs <= 0) {
                    if (_uiState.value.isConstantCycling) {
                        _uiState.update { it.copy(countdownRemaining = it.countdownSeconds) }
                        _timerFinished.emit(Unit)
                        
                        startTime = System.currentTimeMillis()
                        totalDurationMs = _uiState.value.countdownSeconds * 1000L
                        continue
                    } else {
                        _uiState.update { it.copy(countdownRemaining = it.countdownSeconds, isCountdownRunning = false) }
                        _timerFinished.emit(Unit)
                        break
                    }
                }

                val displaySeconds = kotlin.math.ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
                _uiState.update { it.copy(countdownRemaining = displaySeconds) }
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

    override fun onCleared() {
        countdownJob?.cancel()
        stopwatchJob?.cancel()
        // Metronome is stopped by the UI's DisposableEffect on dispose; the
        // ViewModel outlives the composable, so stopping here is redundant.
        super.onCleared()
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
            if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TimerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
