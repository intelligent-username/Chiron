package com.chiron.app.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import com.chiron.app.BuildConfig

object SpotifyManager {

    private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
    private const val REDIRECT_URI = "com.chiron.app://callback"

    private var appRemote: SpotifyAppRemote? = null

    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState

    private val _albumArt = MutableStateFlow<android.graphics.Bitmap?>(null)
    val albumArt: StateFlow<android.graphics.Bitmap?> = _albumArt

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _needsAuthFlow = MutableStateFlow(false)
    val needsAuthFlow: StateFlow<Boolean> = _needsAuthFlow

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    private var timeoutJob: kotlinx.coroutines.Job? = null
    private var sleepJob: kotlinx.coroutines.Job? = null
    private val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun getAuthIntent(context: android.content.Context): android.content.Intent {
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID, 
            AuthorizationResponse.Type.TOKEN, 
            REDIRECT_URI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-read-playback-state"))
        return AuthorizationClient.createLoginActivityIntent(
            context as android.app.Activity, 
            builder.build()
        )
    }

    fun handleAuthResponse(resultCode: Int, data: android.content.Intent?, context: Context) {
        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.d("SpotifyManager", "Auth successful, token received. Connecting remote...")
                _needsAuthFlow.value = false
                connect(context, response.accessToken)
            }
            AuthorizationResponse.Type.ERROR -> {
                Log.e("SpotifyManager", "Auth error: ${response.error}")
                _connectionError.value = "Auth Error: ${response.error}"
                _isConnecting.value = false
            }
            else -> {
                Log.e("SpotifyManager", "Auth failed or cancelled")
                _connectionError.value = "Login cancelled"
                _isConnecting.value = false
            }
        }
    }

    fun connect(context: Context, token: String? = null) {
        // Stop any previous hang
        timeoutJob?.cancel()
        
        if (_isConnected.value) return
        
        _isConnecting.value = true
        _connectionError.value = null
        _needsAuthFlow.value = false

        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        // Start 10s timeout
        timeoutJob = managerScope.launch {
            kotlinx.coroutines.delay(10000)
            if (_isConnecting.value && !_isConnected.value) {
                Log.e("SpotifyManager", "Connection timed out")
                _isConnecting.value = false
                _connectionError.value = "Timeout: Make sure Spotify is open and logged in."
            }
        }

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                timeoutJob?.cancel()
                appRemote = remote
                _isConnected.value = true
                _isConnecting.value = false
                _connectionError.value = null
                Log.d("SpotifyManager", "Connected successfully")
                subscribeToPlayerState()
            }

            override fun onFailure(error: Throwable) {
                timeoutJob?.cancel()
                Log.e("SpotifyManager", "Connection failed: ${error.message}", error)
                _isConnected.value = false
                _isConnecting.value = false
                
                // Spotify throws this exact exception when the Dashboard config (SHA-1, Client ID, Redirect URI)
                // does not PERFECTLY match the calling app, OR if the Spotify app cached a previous rejection.
                if (error is com.spotify.android.appremote.api.error.UserNotAuthorizedException || 
                    error.message?.contains("Explicit user authorization is required") == true) {
                    _connectionError.value = "Needs Authorization"
                    _needsAuthFlow.value = true
                } else {
                    _connectionError.value = error.message ?: "Unknown connection error"
                }

                appRemote = null
            }
        })
    }

    fun disconnect() {
        timeoutJob?.cancel()
        sleepJob?.cancel()
        appRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        _isConnected.value = false
        _isConnecting.value = false
        _connectionError.value = null
        _playerState.value = null
        _albumArt.value = null
        appRemote = null
    }

    fun scheduleDisconnect() {
        sleepJob?.cancel()
        sleepJob = managerScope.launch {
            kotlinx.coroutines.delay(5 * 60 * 1000L) // 5 minutes
            disconnect()
        }
    }

    fun cancelScheduledDisconnect() {
        sleepJob?.cancel()
    }

    private fun subscribeToPlayerState() {
        appRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { state ->
            _playerState.value = state
            
            val imageUri = state.track?.imageUri
            if (imageUri != null) {
                appRemote?.imagesApi?.getImage(imageUri)?.setResultCallback { bitmap ->
                    _albumArt.value = bitmap
                }
            } else {
                _albumArt.value = null
            }
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
