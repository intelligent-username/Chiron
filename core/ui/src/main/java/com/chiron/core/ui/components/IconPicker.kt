package com.chiron.core.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Backward-compatible alias forwarding to [ExerciseIconResolver.getIconUrl]. */
fun getIconUrl(iconName: String?, context: Context? = null): String =
    ExerciseIconResolver.getIconUrl(iconName, context)


@Composable
fun IconPicker(
    selectedIcon: String?,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = "Exercise Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(items = AVAILABLE_ICONS, key = { it.name }) { icon ->
                IconGridItem(
                    icon = icon,
                    isSelected = (icon.name == selectedIcon),
                    onClick = { onIconSelected(icon.name) }
                )
            }
        }
    }
}

@Composable
fun IconPickerDropdown(
    selectedIcon: String?,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 75% width square
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            ExerciseAsyncIcon(
                iconName = selectedIcon,
                contentDescription = "Tap to change icon",
                modifier = Modifier.fillMaxSize(0.8f)
            )
        }

        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = { Text("Choose Icon") },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(items = AVAILABLE_ICONS, key = { it.name }) { icon ->
                            IconGridItem(
                                icon = icon,
                                isSelected = (icon.name == selectedIcon),
                                onClick = {
                                    onIconSelected(icon.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { expanded = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
