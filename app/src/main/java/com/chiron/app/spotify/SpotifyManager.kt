package com.chiron.app.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.chiron.app.BuildConfig

object SpotifyManager {

    private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
    private const val REDIRECT_URI = "com.chiron.app://callback"

    private var appRemote: SpotifyAppRemote? = null

    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect(context: Context) {
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                _isConnected.value = true
                Log.d("SpotifyManager", "Connected")
                subscribeToPlayerState()
            }

            override fun onFailure(error: Throwable) {
                Log.e("SpotifyManager", "Connection failed: ${error.message}")
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        appRemote?.let {
            SpotifyAppRemote.disconnect(it)
            _isConnected.value = false
            appRemote = null
        }
    }

    private fun subscribeToPlayerState() {
        appRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { state ->
            _playerState.value = state
        }
    }

    fun togglePlayPause() {
        val state = _playerState.value ?: return
        if (state.isPaused) appRemote?.playerApi?.resume()
        else appRemote?.playerApi?.pause()
    }

    fun skipNext() {
        appRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        appRemote?.playerApi?.skipPrevious()
    }

    /** seekTo is in milliseconds — only works for Spotify Premium users. */
    fun seekTo(positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs)
    }
}
