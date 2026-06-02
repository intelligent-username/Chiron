package com.chiron.app.spotify

import android.content.Context
import android.util.Log
import android.app.Activity
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
import androidx.compose.ui.graphics.Color

object SpotifyManager {

    private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
    private const val REDIRECT_URI = "com.chiron.app://callback"

    private var appRemote: SpotifyAppRemote? = null

    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState

    private val _albumArt = MutableStateFlow<android.graphics.Bitmap?>(null)
    val albumArt: StateFlow<android.graphics.Bitmap?> = _albumArt

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor

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
    private var lastLoadedImageUri: com.spotify.protocol.types.ImageUri? = null
    private val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun getAuthIntent(activity: Activity): android.content.Intent {
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID, 
            AuthorizationResponse.Type.TOKEN, 
            REDIRECT_URI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-read-playback-state"))
        return AuthorizationClient.createLoginActivityIntent(
            activity,
            builder.build()
        )
    }

    fun handleAuthResponse(resultCode: Int, data: android.content.Intent?, context: Context) {
        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.d("SpotifyManager", "Auth successful, token received. Connecting remote...")
                _needsAuthFlow.value = false
                // After explicit user auth, allow an interactive connect to complete any remaining handshake.
                connect(context, interactive = true)
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

    fun connect(context: Context, interactive: Boolean = false) {
        // Stop any previous hang
        timeoutJob?.cancel()

        if (_isConnected.value || _isConnecting.value) return

        // A pending auth request means the user must go through the Spotify login flow.
        // Silent background reconnects must not clobber that state — bail out.
        if (_needsAuthFlow.value && !interactive) return

        if (CLIENT_ID.isBlank()) {
            _isConnecting.value = false
            _isConnected.value = false
            _connectionError.value = "Spotify not configured (missing Client ID)."
            return
        }

        _isConnecting.value = true
        _connectionError.value = null
        // _needsAuthFlow is intentionally NOT reset here.
        // Only a successful handleAuthResponse() should clear it.

        // Use a silent connect by default. Only show Spotify's auth UI when the user explicitly
        // taps to connect/login; this avoids forcing an auth webview during background reconnects.
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(interactive)
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
                if (imageUri != lastLoadedImageUri) {
                    lastLoadedImageUri = imageUri
                    appRemote?.imagesApi?.getImage(imageUri, com.spotify.protocol.types.Image.Dimension.MEDIUM)?.setResultCallback { bitmap ->
                        _albumArt.value = bitmap
                        // Extract average color for theming
                        if (bitmap != null) {
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
                            val colorInt = scaled.getPixel(0, 0)
                            scaled.recycle()
                            _dominantColor.value = Color(colorInt)
                        } else {
                            _dominantColor.value = null
                        }
                    }
                }
            } else {
                lastLoadedImageUri = null
                _albumArt.value = null
                _dominantColor.value = null
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

    /** Seek relative to the current playback position. No-ops if state/track is unavailable. */
    fun seekBy(deltaMs: Long) {
        val state = _playerState.value ?: return
        val track = state.track ?: return

        val durationMs = track.duration.coerceAtLeast(0L)
        val currentMs = state.playbackPosition.coerceAtLeast(0L)
        val newMs = (currentMs + deltaMs).coerceIn(0L, durationMs)

        seekTo(newMs)
    }

    fun seekBack10s() {
        seekBy(-10_000L)
    }

    fun seekForward10s() {
        seekBy(10_000L)
    }

    fun toggleShuffle() {
        val state = _playerState.value ?: return
        val currentShuffle = state.playbackOptions.isShuffling
        appRemote?.playerApi?.setShuffle(!currentShuffle)
    }

    fun toggleRepeat() {
        val state = _playerState.value ?: return
        // Cycle: 0 (Off) -> 2 (All) -> 1 (One) -> 0 (Off)
        val nextRepeat = when (state.playbackOptions.repeatMode) {
            0 -> 2
            2 -> 1
            else -> 0
        }
        appRemote?.playerApi?.setRepeat(nextRepeat)
    }
}
