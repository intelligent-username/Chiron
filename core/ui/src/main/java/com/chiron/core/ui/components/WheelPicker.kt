package com.chiron.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    itemHeight: Dp = 100.dp,
    wheelWidth: Dp = 120.dp,
    highlightWidthFraction: Float = 1f,
    highlightHeightScale: Float = 1f,
    animateExternalChanges: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.displayMedium,
    format: (Int) -> String = { "%02d".format(it) }
) {
    // Start in the middle of Int.MAX_VALUE to allow infinite scrolling in both directions
    val startScrollIndex = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % count) + value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startScrollIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // Sync state: Snap to the nearest valid item if value changes externally
    LaunchedEffect(value) {
        if (!listState.isScrollInProgress) {
            val currentIndex = listState.firstVisibleItemIndex
            val currentMod = currentIndex % count
            if (currentMod != value) {
                 val diff = value - currentMod
                 if (animateExternalChanges) {
                     // Scroll to the target index smoothly but quickly
                     listState.animateScrollToItem(currentIndex + diff)
                 } else {
                     // Snap instantly to requested value
                     listState.scrollToItem(currentIndex + diff)
                 }
            }
        }
    }

    // Commit value changes only when the wheel settles.
    // This avoids feedback loops where rapid scrolling triggers many external state updates.
    LaunchedEffect(listState, count) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { inProgress -> !inProgress }
            .map {
                val layout = listState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                (layout.visibleItemsInfo.minByOrNull {
                    abs(it.offset + it.size / 2f - center)
                }?.index ?: listState.firstVisibleItemIndex) % count
            }
            .distinctUntilChanged()
            .collect { normalizedIndex -> currentOnValueChange(normalizedIndex) }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .width(wheelWidth),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxWidth(highlightWidthFraction).padding(horizontal = 6.dp)
                .height(itemHeight * 0.9f * highlightHeightScale)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.012f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent, 0.2f to Color.Black,
                            0.8f to Color.Black, 1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = Int.MAX_VALUE) { index ->
                val itemValue = index % count

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format(itemValue),
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = textStyle.fontWeight ?: FontWeight.Medium
                        ),
                        modifier = Modifier.graphicsLayer {
                            val layout = listState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                            val distance = if (item == null) 0f else
                                (item.offset + item.size / 2f - center) / item.size
                            val offset = abs(distance).coerceIn(0f, 2f)
                            scaleX = 1f - 0.18f * offset
                            scaleY = scaleX
                            alpha = (1f - 0.55f * offset).coerceIn(0.15f, 1f)
                            rotationX = -distance.coerceIn(-2f, 2f) * 16f
                            cameraDistance = 12f * density
                        }
                    )
                }
            }
        }
    }
}
