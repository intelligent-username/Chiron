package com.chiron.app.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val playerState by SpotifyManager.playerState.collectAsState()
    val isConnected by SpotifyManager.isConnected.collectAsState()
    val isConnecting by SpotifyManager.isConnecting.collectAsState()
    val needsAuthFlow by SpotifyManager.needsAuthFlow.collectAsState()
    val connectionError by SpotifyManager.connectionError.collectAsState()
    val albumArt by SpotifyManager.albumArt.collectAsState()

    val context = LocalContext.current
    
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        SpotifyManager.handleAuthResponse(result.resultCode, result.data, context)
    }

    // Attempt a silent connect on mount
    LaunchedEffect(Unit) {
        if (!isConnected && !isConnecting) {
            SpotifyManager.connect(context)
        }
    }

    // Auto-redirect to auth screen ONLY if Spotify tells us auth is needed
    LaunchedEffect(needsAuthFlow) {
        if (needsAuthFlow) {
            try {
                authLauncher.launch(SpotifyManager.getAuthIntent(context))
            } catch (e: Exception) {
                // Fallback handled
            }
        }
    }

    val track = if (isConnected) playerState?.track else null

    val idleTextAsAnnotated = remember(needsAuthFlow, connectionError, isConnecting, isConnected, track) {
        buildAnnotatedString {
            when {
                needsAuthFlow -> {
                    append("Spotify: Needs Authorization (Tap to login)")
                }
                isConnecting -> {
                    append("Connecting to Spotify…")
                }
                isConnected && track == null -> {
                    append("Nothing playing in Spotify")
                }
                else -> {
                    // Catch-all for disconnected/timeout/crash
                    append("Unknown Error. Try ")
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append("Launching Spotify?")
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color(0xFF121212))
            .clickable {
                if (!isConnected) {
                    if (needsAuthFlow) {
                        try {
                            authLauncher.launch(SpotifyManager.getAuthIntent(context))
                        } catch (e: Exception) {
                            SpotifyManager.connect(context)
                        }
                    } else {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        } catch (e: Exception) {}
                        SpotifyManager.connect(context)
                    }
                } else {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    } catch (e: Exception) {}
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (!isConnected || track == null) {
            // Idle / connecting state — always visible so the user knows the feature is active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF535353),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = idleTextAsAnnotated,
                    color = Color(0xFF535353),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            return@Column
        }

        val state = playerState!!
        val duration = track.duration.toFloat()

        // Local state for 60fps slider smoothness
        var localPosition by remember { mutableFloatStateOf(state.playbackPosition.toFloat()) }
        var isDragging by remember { mutableStateOf(false) }
        var lastSeekEventTime by remember { mutableLongStateOf(0L) }

        // Interpolate the playback time locally between Spotify IPC updates
        LaunchedEffect(state, isDragging) {
            if (!isDragging) {
                // Prevent 'rubber-banding': block the stale Spotify state from resetting our UI 
                // directly after a manual seek. We trust our local UI state for ~1.5 seconds.
                if (System.currentTimeMillis() - lastSeekEventTime > 1500) {
                    localPosition = state.playbackPosition.toFloat()
                }

                if (!state.isPaused) {
                    val speed = state.playbackSpeed
                    var lastTime = System.currentTimeMillis()
                    while (true) {
                        kotlinx.coroutines.delay(50) // ~20fps updates
                        val now = System.currentTimeMillis()
                        val delta = (now - lastTime) * speed
                        localPosition = (localPosition + delta).coerceIn(0f, duration)
                        lastTime = now
                    }
                }
            }
        }

        // Seek bar (visible always, functional only for Premium)
        if (duration > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                WaveSliderBackground(
                    progress = if (duration > 0) localPosition / duration else 0f,
                    isPaused = state.isPaused,
                    modifier = Modifier.fillMaxSize()
                )
                Slider(
                    value = localPosition,
                    onValueChange = { 
                        isDragging = true
                        localPosition = it 
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        lastSeekEventTime = System.currentTimeMillis()
                        SpotifyManager.seekTo(localPosition.toLong())
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1DB954),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            albumArt?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.name,
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SpotifyManager.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(onClick = { SpotifyManager.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Play" else "Pause",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { SpotifyManager.skipNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun WaveSliderBackground(
    progress: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF1DB954),
    inactiveColor: Color = Color(0xFF535353)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wavePhase"
    )

    // Smoothly animate the amplitude depending on whether it's paused
    val targetAmplitude = if (isPaused) 1.5f else 8f
    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(600), label = "waveAmplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val progressX = (width * progress).coerceIn(0f, width)

        // Draw inactive track (straight line)
        drawLine(
            color = inactiveColor,
            start = androidx.compose.ui.geometry.Offset(progressX, midY),
            end = androidx.compose.ui.geometry.Offset(width, midY),
            strokeWidth = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        if (progressX > 0f) {
            // Draw active track (waves)
            val path = Path()
            path.moveTo(0f, midY)
            
            val waveLength = 120f // pixels per wave cycle
            val step = 4f // precision

            var x = 0f
            while (x <= progressX) {
                val normalizedX = x / waveLength
                val yOffset = amplitude * sin(normalizedX * 2 * PI.toFloat() - phase) * (x / progressX).coerceAtMost(1f) // Scale amplitude up slightly towards the thumb
                path.lineTo(x, midY + yOffset)
                x += step
            }
            if (x < progressX) {
                val yOffset = amplitude * sin((progressX / waveLength) * 2 * PI.toFloat() - phase)
                path.lineTo(progressX, midY + yOffset)
            }

            drawPath(
                path = path,
                color = activeColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
}
