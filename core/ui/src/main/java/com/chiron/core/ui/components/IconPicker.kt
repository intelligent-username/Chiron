package com.chiron.core.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import java.io.File

/**
 * Resolves an exercise icon identifier or file name to the corresponding asset SVG file name.
 * Checks direct icon name matches, hyphen/underscore variants, and asset file name equivalents.
 */
private fun resolveFileName(iconName: String?): String {
    if (iconName.isNullOrBlank()) return "dumbell.svg"
    val clean = iconName.trim().lowercase()
    val cleanNoExt = clean.removeSuffix(".svg")
    val cleanHyphen = cleanNoExt.replace('_', '-')

    // 1. Match by ExerciseIcon.name (e.g. "ring-front-lever", "deadhang", "benchpress")
    AVAILABLE_ICONS.find { 
        it.name.equals(cleanNoExt, ignoreCase = true) || 
        it.name.equals(cleanHyphen, ignoreCase = true) 
    }?.let { return it.fileName }

    // 2. Match by ExerciseIcon.fileName (e.g. "ring-fl.svg", "ring-fl", "deadhang.svg")
    AVAILABLE_ICONS.find { 
        it.fileName.equals(clean, ignoreCase = true) || 
        it.fileName.equals("$cleanNoExt.svg", ignoreCase = true) || 
        it.fileName.equals("$cleanHyphen.svg", ignoreCase = true) 
    }?.let { return it.fileName }

    return "dumbell.svg"
}

/**
 * Returns an image URL suitable for Coil. Checks custom user-imported icons in `filesDir/icons`
 * and custom exercise images in `filesDir/images/exercises/` before falling back to bundled assets.
 */
fun getIconUrl(iconName: String?, context: Context? = null): String {
    if (iconName.isNullOrBlank()) return "file:///android_asset/icons/dumbell.svg"

    if (context != null) {
        // Check filesDir/icons/
        val iconsDir = File(context.filesDir, "icons")
        val directFile = File(iconsDir, iconName)
        if (directFile.exists() && directFile.isFile) return Uri.fromFile(directFile).toString()
        val svgName = if (iconName.endsWith(".svg", ignoreCase = true)) iconName else "$iconName.svg"
        val svgFile = File(iconsDir, svgName)
        if (svgFile.exists() && svgFile.isFile) return Uri.fromFile(svgFile).toString()

        // Check filesDir/images/exercises/
        val imagesDir = File(context.filesDir, "images/exercises")
        val imageFile = File(imagesDir, iconName)
        if (imageFile.exists() && imageFile.isFile) return Uri.fromFile(imageFile).toString()
    }

    val resolved = resolveFileName(iconName)
    return "file:///android_asset/icons/$resolved"
}

fun prefetchAllIcons(context: Context) {
    val loader = context.imageLoader
    AVAILABLE_ICONS.forEach { icon ->
        loader.enqueue(ImageRequest.Builder(context).data("file:///android_asset/icons/${icon.fileName}").build())
    }
}

@Composable
fun ExerciseAsyncIcon(
    iconName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val url = remember(iconName) { getIconUrl(iconName, context) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .listener(
                    onError = { _, result ->
                        android.util.Log.e("ExerciseAsyncIcon", "Failed to load icon '$iconName' from url '$url'", result.throwable)
                    }
                )
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null
        )
    }
}

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
                val isSelected = icon.name == selectedIcon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color(0xFF64B5F6) else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onIconSelected(icon.name) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExerciseAsyncIcon(iconName = icon.name, contentDescription = icon.name, modifier = Modifier.size(40.dp))
                }
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
            androidx.compose.material3.AlertDialog(
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
                            val isSelected = icon.name == selectedIcon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF64B5F6) else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onIconSelected(icon.name)
                                        expanded = false
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ExerciseAsyncIcon(
                                    iconName = icon.name,
                                    contentDescription = icon.name,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { expanded = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
