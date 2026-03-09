package com.chiron.app.ui.components

import android.content.Context
import android.graphics.PorterDuff
import android.util.Xml
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import org.xmlpull.v1.XmlPullParser

// Available exercise icons
data class ExerciseIcon(val name: String, val fileName: String)

val AVAILABLE_ICONS = listOf(
    ExerciseIcon("default", "default.xml"),
    ExerciseIcon("benchpress", "benchpress.xml"),
    ExerciseIcon("chest-press", "chest_press.xml"),
    ExerciseIcon("curl", "curl.xml"),
    ExerciseIcon("hammer-curl", "hammer_curl.xml"),
    ExerciseIcon("deadlift", "deadlift.xml"),
    ExerciseIcon("incline-bench", "incline_bench.xml"),
    ExerciseIcon("jump", "jump.xml"),
    ExerciseIcon("leg-curl", "leg_curl.xml"),
    ExerciseIcon("leg-extension", "leg_extension.xml"),
    ExerciseIcon("leg-raise", "leg_raise.xml"),
    ExerciseIcon("machine", "machine.xml"),
    ExerciseIcon("overhead-press", "overhead_press.xml"),
    ExerciseIcon("pulldown", "pulldown.xml"),
    ExerciseIcon("pushdown", "pushdown.xml"),
    ExerciseIcon("squat", "squat.xml"),
    ExerciseIcon("45-plate", "plate_45.xml"),
    ExerciseIcon("25-plate", "plate_25.xml"),    
    ExerciseIcon("20-plate", "plate_20.xml"),    
    ExerciseIcon("10-plate", "plate_10.xml"),    
    ExerciseIcon("pull-up", "pull_up.xml"),
    ExerciseIcon("smiley", "smiley.xml"),
    ExerciseIcon("push-up", "push_up.xml"),
    ExerciseIcon("sit-up", "sit_up.xml"),
    ExerciseIcon("lunge", "lunge.xml"),
    ExerciseIcon("hip-thrust", "hip_thrust.xml"),
    ExerciseIcon("dip", "dip.xml"),
    ExerciseIcon("kettlebell", "kettlebell.xml"),
    ExerciseIcon("lateral-raise", "lateral_raise.xml"),
    ExerciseIcon("barbell", "barbell.xml"),
    ExerciseIcon("medicine-ball", "medicine_ball.xml"),
    ExerciseIcon("stationary-bike", "stationary_bike.xml"),
    ExerciseIcon("leg-press", "leg_press.xml"),
    ExerciseIcon("smith", "smith.xml"),
    ExerciseIcon("cables", "cables.xml"),
    ExerciseIcon("heart-rate", "heart_rate.xml"),
    ExerciseIcon("bands", "bands.xml"),
    ExerciseIcon("rings", "rings.xml"),
    ExerciseIcon("fly-machine", "fly_machine.xml"),
    ExerciseIcon("preacher-curl", "preacher_curl.xml"),
    ExerciseIcon("machine-row", "machine_row.xml"),
    ExerciseIcon("single-arm-row", "single_arm_row.xml"),
    ExerciseIcon("incline-press-machine", "incline_press_machine.xml"),
    ExerciseIcon("treadmill", "treadmill.xml"),
    ExerciseIcon("farmers-carry", "farmers_carry.xml"),
    ExerciseIcon("cable-crossover", "cable_crossover.xml"),
    ExerciseIcon("ab-twister", "ab_twister.xml"),
    ExerciseIcon("landmine-rotation", "landmine_rotation.xml"),
    ExerciseIcon("link", "link.xml"),
    ExerciseIcon("hack-squat-machine", "hack_squat_machine.xml")
)

fun getIconUrl(iconName: String?): String {
    val exactMatch = AVAILABLE_ICONS.find { it.name == iconName }
    val underscoreMatch = if (exactMatch == null && iconName != null) {
        AVAILABLE_ICONS.find { it.name == iconName.replace('_', '-') }
    } else null
    
    // Try VectorDrawable XML first, fallback to SVG
    val xmlFileName = (exactMatch ?: underscoreMatch)?.fileName ?: "default.xml"
    return "file:///android_asset/vector_drawables/$xmlFileName"
}

fun getIconUrlFallback(iconName: String?): String {
    val exactMatch = AVAILABLE_ICONS.find { it.name == iconName }
    val underscoreMatch = if (exactMatch == null && iconName != null) {
        AVAILABLE_ICONS.find { it.name == iconName.replace('_', '-') }
    } else null
    
    // SVG filenames match the icon names (with hyphens), not the XML filenames (with underscores)
    val iconDisplayName = (exactMatch ?: underscoreMatch)?.name
        ?: iconName?.replace('_', '-')
        ?: "default"
    val svgFileName = "$iconDisplayName.svg"
    return "file:///android_asset/icons/$svgFileName"
}

fun prefetchAllIcons(context: Context) {
    val imageLoader = context.imageLoader
    AVAILABLE_ICONS.forEach { icon ->
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(getIconUrl(icon.name))
                .build()
        )
    }
}

private fun resolveIconFileName(iconName: String?): String {
    val exactMatch = AVAILABLE_ICONS.find { it.name == iconName }
    val underscoreMatch = if (exactMatch == null && iconName != null) {
        AVAILABLE_ICONS.find { it.name == iconName.replace('_', '-') }
    } else null
    return (exactMatch ?: underscoreMatch)?.fileName ?: "default.xml"
}

private fun loadVectorDrawableFromAssets(context: Context, fileName: String) = runCatching {
    context.assets.open("vector_drawables/$fileName").use { input ->
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
            eventType = parser.next()
        }
        if (eventType != XmlPullParser.START_TAG || parser.name != "vector") return@runCatching null
        val attrs = Xml.asAttributeSet(parser)
        VectorDrawableCompat.createFromXmlInner(context.resources, parser, attrs, context.theme)
    }
}.getOrNull()

@Composable
fun ExerciseAsyncIcon(
    iconName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val fileName = remember(iconName) { resolveIconFileName(iconName) }
    val vectorDrawable = remember(fileName) { loadVectorDrawableFromAssets(context, fileName) }

    if (vectorDrawable != null) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                val drawable = vectorDrawable.constantState?.newDrawable()?.mutate() ?: vectorDrawable.mutate()
                imageView.setImageDrawable(drawable)
                if (tint != Color.Unspecified) {
                    imageView.setColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN)
                } else {
                    imageView.clearColorFilter()
                }
                imageView.contentDescription = contentDescription
            }
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(getIconUrlFallback(iconName))
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
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
        Text(
            text = "Exercise Icon",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(
                items = AVAILABLE_ICONS,
                key = { it.name } // Unique icon name as key
            ) { icon ->
                val isSelected = icon.name == selectedIcon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onIconSelected(icon.name) }
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
    }
}
