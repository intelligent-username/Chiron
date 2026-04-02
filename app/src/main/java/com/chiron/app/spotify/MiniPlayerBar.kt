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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val playerState by SpotifyManager.playerState.collectAsState()
    val isConnected by SpotifyManager.isConnected.collectAsState()

    if (!isConnected || playerState?.track == null) return

    val state = playerState!!
    val track = state.track
    val duration = track.duration.toFloat()
    val position = state.playbackPosition.toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Seek bar (visible always, functional only for Premium)
        if (duration > 0) {
            Slider(
                value = position,
                onValueChange = { SpotifyManager.seekTo(it.toLong()) },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1DB954),
                    activeTrackColor = Color(0xFF1DB954),
                    inactiveTrackColor = Color(0xFF535353)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
