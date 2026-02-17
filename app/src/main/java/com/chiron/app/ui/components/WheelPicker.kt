package com.chiron.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    itemHeight: Dp = 100.dp,
    textStyle: TextStyle = MaterialTheme.typography.displayMedium,
    format: (Int) -> String = { "%02d".format(it) }
) {
    // Start in the middle of Int.MAX_VALUE to allow infinite scrolling in both directions
    val startScrollIndex = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % count) + value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startScrollIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    // Sync state: Snap to the nearest valid item if value changes externally
    LaunchedEffect(value) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentMod = currentIndex % count
        if (currentMod != value) {
             val diff = value - currentMod
             // Scroll to the target index smoothly but quickly
             listState.animateScrollToItem(currentIndex + diff)
        }
    }

    // Report value changes when scrolling stops or index changes
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { index -> index % count }
            .distinctUntilChanged()
            .collect { normalizedIndex ->
                onValueChange(normalizedIndex)
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .width(120.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = Int.MAX_VALUE) { index ->
                val itemValue = index % count
                // Determine if this item is the selected one (snapped to center)
                // Note: listState.firstVisibleItemIndex updates as we scroll.
                // For a perfect "wheel" effect, we'd use layoutInfo to calculate distance from center.
                // Here we use a simpler derived state check.
                val isSelected by remember { 
                    derivedStateOf { index == listState.firstVisibleItemIndex } 
                }

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format(itemValue),
                        style = textStyle.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isSelected) textStyle.fontSize else (textStyle.fontSize.value * 0.7f).sp
                        ),
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.3f)
                    )
                }
            }
        }
    }
}
