package com.chiron.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

object MetronomeController {
    const val ACTION_START = "com.chiron.app.metronome.START"
    const val ACTION_PAUSE = "com.chiron.app.metronome.PAUSE"
    const val ACTION_STOP = "com.chiron.app.metronome.STOP"
    const val ACTION_TOGGLE = "com.chiron.app.metronome.TOGGLE"

    private lateinit var appContext: Context

    val bpm = MutableStateFlow(60)
    val tickAsset = MutableStateFlow("Tick1.mp3")
    val isRunning = MutableStateFlow(false)
    val isActive = MutableStateFlow(false)

    fun init(context: Context) { appContext = context.applicationContext }

    fun start() {
        if (!isActive.value) {
            isActive.value = true
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, MetronomeService::class.java).setAction(ACTION_START)
            )
        }
        isRunning.value = true
    }

    fun pause() { isRunning.value = false }

    fun stop() {
        isActive.value = false
        isRunning.value = false
        appContext.stopService(Intent(appContext, MetronomeService::class.java))
    }

    fun toggle() { if (isRunning.value) pause() else start() }

    fun setBpm(value: Int) { bpm.value = value.coerceIn(20, 300) }

    fun setTickAsset(asset: String) { tickAsset.value = asset }
}