package com.chiron.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ChironSplashScreen(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Background colors matching the app theme
    val bgColor = if (isDark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // ── Logo pulse animation ─────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // ── Entry fade-in ────────────────────────────────────────────
    var contentAlpha by remember { mutableFloatStateOf(0f) }
    val contentAlphaAnim by animateFloatAsState(
        targetValue = contentAlpha,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )
    LaunchedEffect(Unit) {
        contentAlpha = 1f
    }

    // ── Progress bar (shimmer sweep) ─────────────────────────────
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
        ),
        label = "shimmer"
    )

    // When loading is done, animate the bar to full (100%)
    var progressTarget by remember { mutableFloatStateOf(0.15f) }  // start non-zero so it looks alive
    val progressAnim by animateFloatAsState(
        targetValue = if (isLoading) progressTarget else 1f,
        animationSpec = tween(if (isLoading) 800 else 400, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // Slowly creep the progress while loading
    LaunchedEffect(isLoading) {
        if (isLoading) {
            // Animate to 85% over ~2s to simulate progress
            kotlinx.coroutines.delay(400)
            progressTarget = 0.5f
            kotlinx.coroutines.delay(700)
            progressTarget = 0.75f
            kotlinx.coroutines.delay(700)
            progressTarget = 0.88f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlphaAnim),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Glow halo ──────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Glow circle behind the logo
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .alpha(glowAlpha)
                        .blur(30.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryColor, Color.Transparent)
                            )
                        )
                )

                // Logo image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("android.resource://com.chiron.app/drawable/ic_launcher_foreground")
                        .crossfade(true)
                        .build(),
                    contentDescription = "Chiron logo",
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoScale)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── App name ───────────────────────────────────────────
            Text(
                text = "Chiron",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Your training, your records",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // ── Progress bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(surfaceVariant.copy(alpha = 0.4f))
            ) {
                // Filled portion
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnim)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.8f),
                                    primaryColor
                                )
                            )
                        )
                )

                // Shimmer sweep on top (only while loading)
                if (isLoading) {
                    val s0 = (shimmerProgress - 0.3f).coerceIn(0f, 0.98f)
                    val s1 = shimmerProgress.coerceIn(s0 + 0.01f, 0.99f)
                    val s2 = (shimmerProgress + 0.3f).coerceIn(s1 + 0.01f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressAnim)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        s0 to Color.Transparent,
                                        s1 to Color.White.copy(alpha = 0.45f),
                                        s2 to Color.Transparent,
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status label
            Text(
                text = if (isLoading) "Loading…" else "Ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
