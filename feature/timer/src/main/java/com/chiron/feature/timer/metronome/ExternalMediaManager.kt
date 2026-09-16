package com.chiron.feature.timer.metronome

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.view.KeyEvent
import com.chiron.core.spotify.SpotifyManager

object ExternalMediaManager {

    fun togglePlayPause(context: Context) {
        if (forwardPlayPauseViaActiveSessions(context)) return

        if (SpotifyManager.isConnected.value) {
            SpotifyManager.togglePlayPause()
            return
        }

        dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun skipToNext(context: Context) {
        if (forwardViaActiveSessions(context, isNext = true)) return

        if (SpotifyManager.isConnected.value) {
            SpotifyManager.skipNext()
            return
        }

        dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun skipToPrevious(context: Context) {
        if (forwardViaActiveSessions(context, isNext = false)) return

        if (SpotifyManager.isConnected.value) {
            SpotifyManager.skipPrevious()
            return
        }

        dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun forwardPlayPauseViaActiveSessions(context: Context): Boolean {
        if (!ChironNotificationListenerService.isNotificationAccessGranted(context)) {
            return false
        }
        return runCatching {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return false
            val componentName = ComponentName(context, ChironNotificationListenerService::class.java)
            val sessions = mediaSessionManager.getActiveSessions(componentName)
            val externalSession = sessions.firstOrNull { it.packageName != context.packageName }
            if (externalSession != null) {
                val state = externalSession.playbackState?.state
                if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                    externalSession.transportControls.pause()
                } else {
                    externalSession.transportControls.play()
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun forwardViaActiveSessions(context: Context, isNext: Boolean): Boolean {
        if (!ChironNotificationListenerService.isNotificationAccessGranted(context)) {
            return false
        }
        return runCatching {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return false
            val componentName = ComponentName(context, ChironNotificationListenerService::class.java)
            val sessions = mediaSessionManager.getActiveSessions(componentName)
            val externalSession = sessions.firstOrNull { it.packageName != context.packageName }
            if (externalSession != null) {
                if (isNext) {
                    externalSession.transportControls.skipToNext()
                } else {
                    externalSession.transportControls.skipToPrevious()
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun dispatchMediaKeyEvent(context: Context, keyCode: Int) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }
}
