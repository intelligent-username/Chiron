package com.chiron.app.ui.components

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest

// Available exercise icons — name matches the icon ID stored in DB, fileName matches assets/icons/
data class ExerciseIcon(val name: String, val fileName: String)

val AVAILABLE_ICONS = listOf(
    ExerciseIcon("dumbell",                "dumbell.svg"),
    ExerciseIcon("benchpress",             "benchpress.svg"),
    ExerciseIcon("chest-press",            "chest-press.svg"),
    ExerciseIcon("incline-bench",          "incline-bench.svg"),
    ExerciseIcon("incline-press-machine",  "incline-press-machine.svg"),
    ExerciseIcon("incline-dumbbell-press", "incline-db-press.svg"),
    ExerciseIcon("overhead-press",         "overhead-press.svg"),

    ExerciseIcon("curl",                   "curl.svg"),
    ExerciseIcon("hammer-curl",            "hammer-curl.svg"),
    ExerciseIcon("preacher-curl",          "preacher-curl.svg"),

    ExerciseIcon("deadlift",               "deadlift.svg"),
    ExerciseIcon("zercher-deadlift",       "zercher-deadlift.svg"),
    ExerciseIcon("farmers-carry",          "farmers-carry.svg"),

    ExerciseIcon("jump",                   "jump.svg"),
    ExerciseIcon("leg-curl",               "leg-curl.svg"),
    ExerciseIcon("leg-extension",          "leg-extension.svg"),
    ExerciseIcon("leg-raise",              "leg-raise.svg"),
    ExerciseIcon("squat",                  "squat.svg"),
    ExerciseIcon("leg-press",              "leg-press.svg"),
    ExerciseIcon("calf-machine",           "calf-machine.svg"),
    ExerciseIcon("hack-squat-machine",     "hack-squat-machine.svg"),
    ExerciseIcon("lunge",                  "lunge.svg"),
    ExerciseIcon("hip-thrust",             "hip-thrust.svg"),

    ExerciseIcon("machine",                "machine.svg"),
    ExerciseIcon("pulldown",               "pulldown.svg"),
    ExerciseIcon("pushdown",               "pushdown.svg"),

    ExerciseIcon("45-plate",               "plate-ta.svg"),
    ExerciseIcon("25-plate",               "plate-tb.svg"),
    ExerciseIcon("20-plate",               "plate-tc.svg"),
    ExerciseIcon("10-plate",               "plate-td.svg"),

    ExerciseIcon("pull-up",                "pull-up.svg"),
    ExerciseIcon("neutral-pullup",         "neutral-pull.svg"),
    ExerciseIcon("push-up",                "push-up.svg"),
    ExerciseIcon("dip",                    "dip.svg"),
    ExerciseIcon("rings",                  "rings.svg"),

    ExerciseIcon("treadmill",              "treadmill.svg"),
    ExerciseIcon("stationary-bike",        "stationary-bike.svg"),
    ExerciseIcon("heart-rate",             "heart-rate.svg"),

    ExerciseIcon("lateral-raise",          "lateral-raise.svg"),
    ExerciseIcon("barbell",                "barbell.svg"),
    ExerciseIcon("smith",                  "smith.svg"),
    ExerciseIcon("cables",                 "cables.svg"),
    ExerciseIcon("bands",                  "bands.svg"),

    ExerciseIcon("fly-machine",            "fly-machine.svg"),
    ExerciseIcon("peck-deck",              "deck.svg"),
    ExerciseIcon("cable-crossover",        "cable-crossover.svg"),

    ExerciseIcon("ab-twister",             "ab-twister.svg"),
    ExerciseIcon("landmine-rotation",      "landmine-rotation.svg"),
    ExerciseIcon("medicine-ball",          "medicine-ball.svg"),

    ExerciseIcon("machine-row",            "machine-row.svg"),
    ExerciseIcon("single-arm-row",         "single-arm-row.svg"),

    ExerciseIcon("itrot",                  "internal-rotation.svg"),
    ExerciseIcon("neck-curl",              "neck-curl.svg"),

    ExerciseIcon("link",                   "link.svg"),
    ExerciseIcon("smiley",                 "smiley.svg"),
    ExerciseIcon("sit-up",                 "sit-up.svg"),
    ExerciseIcon("kettlebell",             "kettlebell.svg"),
)

private fun resolveFileName(iconName: String?): String {
    if (iconName == null) return "dumbell.svg"
    // Try exact match first
    AVAILABLE_ICONS.find { it.name == iconName }?.let { return it.fileName }
    // Try replacing underscores with dashes
    AVAILABLE_ICONS.find { it.name == iconName.replace('_', '-') }?.let { return it.fileName }
    return "dumbell.svg"
}

fun getIconUrl(iconName: String?): String {
    return "file:///android_asset/icons/${resolveFileName(iconName)}"
}

fun prefetchAllIcons(context: Context) {
    val loader = context.imageLoader
    AVAILABLE_ICONS.forEach { icon ->
        loader.enqueue(
            ImageRequest.Builder(context)
                .data("file:///android_asset/icons/${icon.fileName}")
                .build()
        )
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
    val url = remember(iconName) { getIconUrl(iconName) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null
    )
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
                key = { it.name }
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
