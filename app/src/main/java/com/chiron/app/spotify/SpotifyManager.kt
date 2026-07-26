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

    private var accessToken: String? = null
    private var timeoutJob: kotlinx.coroutines.Job? = null
    private var sleepJob: kotlinx.coroutines.Job? = null
    private var lastLoadedImageUri: com.spotify.protocol.types.ImageUri? = null
    private var lastLoadedTrackUri: String? = null
    private val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun getAuthIntent(activity: Activity): android.content.Intent {
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID, 
            AuthorizationResponse.Type.TOKEN, 
            REDIRECT_URI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-read-playback-state", "user-read-currently-playing"))
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
                accessToken = response.accessToken
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
        lastLoadedImageUri = null
        lastLoadedTrackUri = null
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
            
            val track = state.track
            val trackUri = track?.uri
            val imageUri = track?.imageUri
            
            if (trackUri != lastLoadedTrackUri || imageUri != lastLoadedImageUri) {
                lastLoadedTrackUri = trackUri
                lastLoadedImageUri = imageUri
                loadCoverArt(state)
            }
        }
    }

    private fun loadCoverArt(state: PlayerState) {
        val track = state.track ?: run {
            _albumArt.value = null
            _dominantColor.value = null
            return
        }

        // Method 1 & 2: App Remote SDK local image Uri fallback chain
        val localImageUri = track.imageUri
        if (localImageUri != null) {
            appRemote?.imagesApi?.getImage(localImageUri, com.spotify.protocol.types.Image.Dimension.MEDIUM)
                ?.setResultCallback { bitmap ->
                    if (bitmap != null) {
                        applyBitmap(bitmap)
                    } else {
                        // SDK getImage returned null, fallback to Web API
                        fetchCoverFromWebApi(track.uri)
                    }
                }
                ?.setErrorCallback {
                    // SDK getImage errored, fallback to Web API
                    fetchCoverFromWebApi(track.uri)
                }
        } else {
            // imageUri was null on SDK track payload, fall back directly to Web API
            fetchCoverFromWebApi(track.uri)
        }
    }

    private fun fetchCoverFromWebApi(trackOrEpisodeUri: String?) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _albumArt.value = null
            _dominantColor.value = null
            return
        }

        managerScope.launch(Dispatchers.IO) {
            try {
                // Query Web API with additional_types=episode param (essential for podcasts)
                val url = java.net.URL("https://api.spotify.com/v1/me/player/currently-playing?additional_types=episode")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    val jsonObj = org.json.JSONObject(jsonStr)
                    val item = jsonObj.optJSONObject("item") ?: return@launch
                    
                    var imageUrl: String? = null
                    
                    // 1. Try track album images: item.album.images[]
                    val album = item.optJSONObject("album")
                    if (album != null) {
                        val images = album.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            imageUrl = images.optJSONObject(0)?.optString("url")
                        }
                    }
                    
                    // 2. Try episode-specific images: item.images[]
                    if (imageUrl.isNullOrEmpty()) {
                        val images = item.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            imageUrl = images.optJSONObject(0)?.optString("url")
                        }
                    }
                    
                    // 3. Try show (podcast-level) images: item.show.images[]
                    if (imageUrl.isNullOrEmpty()) {
                        val show = item.optJSONObject("show")
                        if (show != null) {
                            val images = show.optJSONArray("images")
                            if (images != null && images.length() > 0) {
                                imageUrl = images.optJSONObject(0)?.optString("url")
                            }
                        }
                    }

                    if (!imageUrl.isNullOrEmpty()) {
                        val imgUrl = java.net.URL(imageUrl)
                        val bitmap = android.graphics.BitmapFactory.decodeStream(imgUrl.openStream())
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                applyBitmap(bitmap)
                            }
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.e("SpotifyManager", "Error fetching cover from Web API", e)
            }
        }
    }

    private fun applyBitmap(bitmap: android.graphics.Bitmap) {
        _albumArt.value = bitmap
        try {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val colorInt = scaled.getPixel(0, 0)
            scaled.recycle()
            _dominantColor.value = Color(colorInt)
        } catch (e: Exception) {
            _dominantColor.value = null
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
