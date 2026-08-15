package com.chiron.core.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import com.chiron.core.ui.theme.CoolGray

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Format milliseconds using polymorphic PlaybackTimeFormatter */
private fun formatMs(ms: Float, totalDurationMs: Float = ms): String {
    return PlaybackTimeFormatter.forDuration(totalDurationMs).format(ms)
}

@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    drawBackgroundAndBorder: Boolean = true
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val playerState by SpotifyManager.playerState.collectAsState()
    val isConnected by SpotifyManager.isConnected.collectAsState()
    val isConnecting by SpotifyManager.isConnecting.collectAsState()
    val needsAuthFlow by SpotifyManager.needsAuthFlow.collectAsState()
    val connectionError by SpotifyManager.connectionError.collectAsState()
    val albumArt by SpotifyManager.albumArt.collectAsState()
    val resolvedActiveColor = ElectricBlue
    val resolvedInactiveColor = ThinOutline

    val context = LocalContext.current

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        SpotifyManager.handleAuthResponse(result.resultCode, result.data, context)
    }

    // Attempt a silent connect on mount, but never while auth is pending.
    LaunchedEffect(Unit) {
        if (!isConnected && !isConnecting && !needsAuthFlow) {
            SpotifyManager.connect(context)
        }
    }

    val track = if (isConnected) playerState?.track else null

    val idleText = remember(needsAuthFlow, connectionError, isConnecting, isConnected, track) {
        buildAnnotatedString {
            when {
                needsAuthFlow -> append("Spotify: Needs Authorization (Tap to login)")
                connectionError != null -> { append("Spotify: "); append(connectionError); append(" (Tap to retry)") }
                isConnecting -> append("Connecting to Spotify…")
                isConnected && track == null -> append("Nothing playing in Spotify")
                else -> append("Spotify: Disconnected (Tap to connect)")
            }
        }
    }

    // Shared handler: open Spotify app (or trigger auth/connect if not connected)
    val openSpotify: () -> Unit = {
        if (!isConnected) {
            if (needsAuthFlow) {
                val activity = context.findActivity()
                if (activity != null) {
                    runCatching {
                        authLauncher.launch(SpotifyManager.getAuthIntent(activity))
                    }.getOrElse {
                        SpotifyManager.connect(context, interactive = true)
                    }
                } else {
                    SpotifyManager.connect(context, interactive = true)
                }
            } else {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (launchIntent != null) context.startActivity(launchIntent)
                } catch (e: Exception) {}
                SpotifyManager.connect(context, interactive = true)
            }
        } else {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launchIntent != null) context.startActivity(launchIntent)
            } catch (e: Exception) {}
        }
    }

    // Outer Column: always clickable → opens Spotify.
    Column(
        modifier = modifier
            .then(
                if (drawBackgroundAndBorder) {
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolidSlate)
                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .clickable { openSpotify() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ── Idle / connecting state ────────────────────────────────────────
        if (!isConnected || track == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = CoolGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = idleText,
                    color = CoolGray,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            return@Column
        }

        // ── Playing state ──────────────────────────────────────────────────
        val state = playerState!!
        val duration = track.duration.toFloat()
        val isPodcast = track.isPodcast

        val secondaryText = remember(track, isPodcast) {
            if (isPodcast) {
                runCatching { track.album.name }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { track.artist.name }.getOrNull().orEmpty()
            } else {
                runCatching { track.artist.name }.getOrNull().orEmpty()
            }
        }

        // Local state for smooth slider
        var localPosition by remember { mutableFloatStateOf(state.playbackPosition.toFloat()) }
        var isDragging by remember { mutableStateOf(false) }
        var lastSeekEventTime by remember { mutableLongStateOf(0L) }

        LaunchedEffect(state, isDragging) {
            if (!isDragging) {
                if (System.currentTimeMillis() - lastSeekEventTime > 1500) {
                    localPosition = state.playbackPosition.toFloat()
                }
                if (!state.isPaused) {
                    val speed = state.playbackSpeed
                    var lastTime = System.currentTimeMillis()
                    while (true) {
                        kotlinx.coroutines.delay(50)
                        val now = System.currentTimeMillis()
                        val delta = (now - lastTime) * speed
                        localPosition = (localPosition + delta).coerceIn(0f, duration)
                        lastTime = now
                    }
                }
            }
        }

        if (expanded) {
            // ── EXPANDED VIEW ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Cover art or placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolidSlate)
                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                        .clickable { expanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt!!.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "No Album Art",
                            tint = CoolGray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Track name
                Text(
                    text = track.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Artist / show name
                Text(
                    text = secondaryText,
                    color = CoolGray,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wave seek bar
                if (duration > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
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
                                thumbColor = ElectricBlue,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val timeFormatter = remember(duration) { PlaybackTimeFormatter.forDuration(duration) }

                    // Timestamp row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = timeFormatter.format(localPosition),
                            color = CoolGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = timeFormatter.format(duration),
                            color = CoolGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Playback controls row: Shuffle, Prev/Rewind, Play/Pause, Next/Forward, Repeat
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    // 1. Shuffle
                    val isShuffling = state.playbackOptions.isShuffling
                    val shuffleTint = if (isShuffling) ElectricBlue else CoolGray
                    IconButton(
                        onClick = { SpotifyManager.toggleShuffle() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        ShuffleIcon(
                            tint = shuffleTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. Skip Previous or Replay 10s
                    if (isPodcast) {
                        IconButton(onClick = { SpotifyManager.seekBack10s() }) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Back 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { SpotifyManager.skipPrevious() }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // 3. Play / Pause
                    IconButton(
                        onClick = { SpotifyManager.togglePlayPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Play" else "Pause",
                            tint = ElectricBlue,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    // 4. Skip Next or Forward 10s
                    if (isPodcast) {
                        IconButton(onClick = { SpotifyManager.seekForward10s() }) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { SpotifyManager.skipNext() }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // 5. Repeat
                    val repeatMode = state.playbackOptions.repeatMode
                    val repeatTint = if (repeatMode != 0) ElectricBlue else CoolGray
                    IconButton(
                        onClick = { SpotifyManager.toggleRepeat() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        RepeatIcon(
                            tint = repeatTint,
                            repeatOne = repeatMode == 1,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

        } else {
            // ── COMPACT / MINI VIEW ────────────────────────────────────────

            // Wave seek bar in compact view
            if (duration > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    WaveSliderBackground(
                        progress = if (duration > 0) localPosition / duration else 0f,
                        isPaused = state.isPaused,
                        isCompact = true,
                        activeColor = resolvedActiveColor,
                        inactiveColor = resolvedInactiveColor,
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
                            thumbColor = resolvedActiveColor,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art or placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SolidSlate)
                        .border(1.dp, ThinOutline, RoundedCornerShape(4.dp))
                        .clickable { expanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt!!.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "No Album Art",
                            tint = CoolGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Track info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = secondaryText,
                        color = CoolGray,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPodcast) {
                        IconButton(onClick = { SpotifyManager.seekBack10s() }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        IconButton(onClick = { SpotifyManager.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = { SpotifyManager.togglePlayPause() }) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Play" else "Pause",
                            tint = ElectricBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    if (isPodcast) {
                        IconButton(onClick = { SpotifyManager.seekForward10s() }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        IconButton(onClick = { SpotifyManager.skipNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
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
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    isCompact: Boolean = false
) {
    val resolvedActiveColor = activeColor ?: ElectricBlue
    val resolvedInactiveColor = inactiveColor ?: ThinOutline

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wavePhase"
    )

    val targetAmplitude = if (isCompact) {
        if (isPaused) 0.5f else 3f
    } else {
        if (isPaused) 1.5f else 8f
    }
    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(600), label = "waveAmplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val progressX = (width * progress).coerceIn(0f, width)

        // 1. Right side: Flat unplayed line using resolvedInactiveColor
        if (progressX < width) {
            drawLine(
                color = resolvedInactiveColor,
                start = androidx.compose.ui.geometry.Offset(progressX, midY),
                end = androidx.compose.ui.geometry.Offset(width, midY),
                strokeWidth = (if (isCompact) 1.5.dp else 2.dp).toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // 2. Left side: Sine wave starting at progressX and propagating leftward to 0f using resolvedActiveColor
        if (progressX > 0f) {
            val step = 4f

            // ───────────────── B. DRAW PRIMARY (PROGRESS) WAVE ─────────────────
            val primaryPath = Path()
            primaryPath.moveTo(progressX, midY)
            val primaryWaveLength = 120f
            var xPrimary = progressX
            while (xPrimary >= 0f) {
                val dx = progressX - xPrimary
                val normalizedX = dx / primaryWaveLength
                // Primary wave decays faster (640dp decay - twice as slow)
                val startRamp = (dx / 30f).coerceAtMost(1f)
                val fade = (1f - (dx / 640f)).coerceIn(0f, 1f)
                val damp = startRamp * fade
                val yOffset = amplitude * sin(normalizedX * 2 * PI.toFloat() + phase) * damp
                primaryPath.lineTo(xPrimary, midY + yOffset)
                xPrimary -= step
            }
            if (xPrimary > -step) {
                val dx = progressX
                val startRamp = (dx / 30f).coerceAtMost(1f)
                val fade = (1f - (dx / 640f)).coerceIn(0f, 1f)
                val damp = startRamp * fade
                val yOffset = amplitude * sin((dx / primaryWaveLength) * 2 * PI.toFloat() + phase) * damp
                primaryPath.lineTo(0f, midY + yOffset)
            }
            drawPath(
                path = primaryPath,
                color = resolvedActiveColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (isCompact) 1.5.dp else 3.dp).toPx())
            )
        }
    }
}

@Composable
fun ShuffleIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // Arrow 1: top-left to bottom-right
            moveTo(0.15f * w, 0.25f * h)
            lineTo(0.35f * w, 0.25f * h)
            cubicTo(0.45f * w, 0.25f * h, 0.55f * w, 0.75f * h, 0.65f * w, 0.75f * h)
            lineTo(0.85f * w, 0.75f * h)
            
            // Arrow 2: bottom-left to top-right
            moveTo(0.15f * w, 0.75f * h)
            lineTo(0.35f * w, 0.75f * h)
            cubicTo(0.45f * w, 0.75f * h, 0.55f * w, 0.25f * h, 0.65f * w, 0.25f * h)
            lineTo(0.85f * w, 0.25f * h)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // Arrow head 1
        val arrowHead1 = Path().apply {
            moveTo(0.75f * w, 0.65f * h)
            lineTo(0.85f * w, 0.75f * h)
            lineTo(0.75f * w, 0.85f * h)
        }
        drawPath(
            path = arrowHead1,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Arrow head 2
        val arrowHead2 = Path().apply {
            moveTo(0.75f * w, 0.15f * h)
            lineTo(0.85f * w, 0.25f * h)
            lineTo(0.75f * w, 0.35f * h)
        }
        drawPath(
            path = arrowHead2,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun RepeatIcon(
    tint: Color,
    repeatOne: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Loop paths (top arrow going right, bottom arrow going left)
        val path = Path().apply {
            // Top arrow
            moveTo(0.2f * w, 0.4f * h)
            lineTo(0.2f * w, 0.3f * h)
            cubicTo(0.2f * w, 0.2f * h, 0.8f * w, 0.2f * h, 0.8f * w, 0.3f * h)
            lineTo(0.8f * w, 0.4f * h)
            
            // Bottom arrow
            moveTo(0.8f * w, 0.6f * h)
            lineTo(0.8f * w, 0.7f * h)
            cubicTo(0.8f * w, 0.8f * h, 0.2f * w, 0.8f * h, 0.2f * w, 0.7f * h)
            lineTo(0.2f * w, 0.6f * h)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Arrow heads
        val arrowHead1 = Path().apply {
            moveTo(0.7f * w, 0.35f * h)
            lineTo(0.8f * w, 0.4f * h)
            lineTo(0.7f * w, 0.45f * h)
        }
        drawPath(
            path = arrowHead1,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        val arrowHead2 = Path().apply {
            moveTo(0.3f * w, 0.55f * h)
            lineTo(0.2f * w, 0.6f * h)
            lineTo(0.3f * w, 0.65f * h)
        }
        drawPath(
            path = arrowHead2,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        if (repeatOne) {
            // Draw a tiny "1" in the center
            drawLine(
                color = tint,
                start = Offset(0.5f * w, 0.42f * h),
                end = Offset(0.5f * w, 0.58f * h),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(0.45f * w, 0.46f * h),
                end = Offset(0.5f * w, 0.42f * h),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

