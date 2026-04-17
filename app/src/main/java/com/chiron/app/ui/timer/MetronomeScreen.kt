package com.chiron.app.ui.timer

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chiron.app.ui.components.WheelPicker
import com.chiron.app.viewmodel.TimerViewModel
import kotlinx.coroutines.isActive

@Composable
fun MetronomeContent(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sweepProgress = remember { Animatable(0f) }
    val availableTicks = remember {
        (context.assets.list("audio")
            ?.filter { it.endsWith(".mp3", ignoreCase = true) }
            ?.sortedBy { it.lowercase() }
            ?: listOf("Tick1.mp3", "Tick2.mp3", "Tick3.mp3"))
    }

    LaunchedEffect(viewModel) {
        viewModel.metronomeTick.collect {
            val fileName = state.metronomeTickAsset
            try {
                val afd = context.assets.openFd("audio/$fileName")
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mediaPlayer.setOnPreparedListener { it.start() }
                mediaPlayer.setOnCompletionListener { it.release() }
                mediaPlayer.prepareAsync()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(state.isMetronomeRunning, state.metronomeBpm) {
        if (!state.isMetronomeRunning) {
            sweepProgress.snapTo(0f)
            return@LaunchedEffect
        }

        var target = 1f
        while (isActive && state.isMetronomeRunning) {
            val beatDuration = (60_000 / state.metronomeBpm.coerceAtLeast(1)).toInt()
            sweepProgress.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = beatDuration, easing = LinearEasing)
            )
            target *= -1f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.pauseMetronome()
        }
    }

    val dotOffset = 130.dp * sweepProgress.value
    val selectedTickIndex = (availableTicks.indexOf(state.metronomeTickAsset).takeIf { it >= 0 } ?: 0) + 1
    var expanded by remember { mutableStateOf(false) }
    val tickGradients = listOf(
        listOf(Color(0xFF6BA6FF), Color(0xFF3A5CE0)),
        listOf(Color(0xFF69E0C2), Color(0xFF158A6A)),
        listOf(Color(0xFFFFC47A), Color(0xFFE0792E)),
        listOf(Color(0xFFFF8AA7), Color(0xFFC44368)),
        listOf(Color(0xFFAFA1FF), Color(0xFF5D57D9)),
        listOf(Color(0xFF89D2E6), Color(0xFF2F7E9F))
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
                .zIndex(5f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Button(
                    onClick = { expanded = !expanded },
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Tick sound",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 20.dp)
                .zIndex(6f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            availableTicks.forEachIndexed { idx, tick ->
                val isSelected = state.metronomeTickAsset == tick
                AnimatedVisibility(
                    visible = expanded,
                    enter = scaleIn(
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = idx * 45
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 180,
                            delayMillis = idx * 45
                        )
                    )
                ) {
                    IconButton(
                        onClick = {
                            viewModel.setMetronomeTickAsset(tick)
                            expanded = false
                        },
                        modifier = Modifier.size(if (idx == 0) 40.dp else if (isSelected) 36.dp else 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (idx == 0) 40.dp else if (isSelected) 32.dp else 28.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = tickGradients[idx % tickGradients.size]
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (idx + 1).toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (idx == 0) 13.sp else 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            WheelPicker(
                count = 281,
                value = (state.metronomeBpm - 20).coerceIn(0, 280),
                onValueChange = { index -> viewModel.setMetronomeBpm(index + 20) },
                visibleCount = 3,
                itemHeight = 90.dp,
                wheelWidth = 260.dp,
                animateExternalChanges = false,
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold
                ),
                format = { index -> "${index + 20}" }
            )

            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Tick $selectedTickIndex",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Box(
                    modifier = Modifier
                        .offset(x = dotOffset)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.toggleMetronome() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isMetronomeRunning) MaterialTheme.colorScheme.error else Color(0xFFD66F17),
                            contentColor = if (state.isMetronomeRunning) MaterialTheme.colorScheme.onError else Color(0xFF2F1E0A)
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isMetronomeRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (state.isMetronomeRunning) "Pause" else "Start",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setMetronomeBpm(60) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 20.dp, y = (-18).dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset BPM",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
