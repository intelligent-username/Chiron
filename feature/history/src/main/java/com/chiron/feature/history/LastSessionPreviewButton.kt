package com.chiron.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp

/**
 * A press-and-hold dot button that signals a last-session preview should be shown.
 *
 * While the user holds the button, [onPreviewActive] is called with `true`.
 * When released it is called with `false`.
 *
 * Tracks the pointer by ID only — ignores bounds so that layout changes
 * (extra set-pill rows appearing) never cancel the gesture.
 *
 * @param size Diameter of the outer circle in dp.
 * @param dotSize Diameter of the inner dot in dp.
 * @param onPreviewActive Callback for press state changes.
 */
@Composable
fun LastSessionPreviewButton(
    modifier: Modifier = Modifier,
    size: Int = 28,
    dotSize: Int = 10,
    onPreviewActive: (Boolean) -> Unit
) {
    val currentCallback by rememberUpdatedState(onPreviewActive)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentCallback(true)
                    try {
                        // Wait until the tracked pointer lifts — ignore position / bounds
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == pointerId }
                            if (tracked == null || tracked.changedToUp()) break
                        }
                    } finally {
                        currentCallback(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dotSize.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }
}
