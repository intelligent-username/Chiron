package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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

/**
 * A press-and-hold dot button that signals a last-session preview should be shown.
 *
 * While the user holds the button, [onPreviewActive] is called with `true`.
 * When released it is called with `false`.
 *
 * Uses [awaitEachGesture] + [waitForUpOrCancellation] so that layout changes
 * caused by the preview (e.g. extra set pills) never cancel the gesture.
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
    // Always capture the latest lambda even if the coroutine outlives a recomposition.
    val currentCallback by rememberUpdatedState(onPreviewActive)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentCallback(true)
                    waitForUpOrCancellation()
                    currentCallback(false)
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
