package com.chiron.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

// Available exercise icons
data class ExerciseIcon(val name: String, val fileName: String)

val AVAILABLE_ICONS = listOf(
    ExerciseIcon("default", "default.svg"),
    ExerciseIcon("benchpress", "benchpress.svg"),
    ExerciseIcon("chest_press", "chest-press.svg"),
    ExerciseIcon("curl", "curl.svg"),
    ExerciseIcon("deadlift", "deadlift.svg"),
    ExerciseIcon("incline_bench", "incline-bench.svg"),
    ExerciseIcon("jump", "jump.svg"),
    ExerciseIcon("leg_curl", "leg-curl.svg"),
    ExerciseIcon("leg_extension", "leg-extension.svg"),
    ExerciseIcon("leg_raise", "leg-raise.svg"),
    ExerciseIcon("machine", "machine.svg"),
    ExerciseIcon("overhead_press", "overhead-press.svg"),
    ExerciseIcon("pulldown", "pulldown.svg"),
    ExerciseIcon("pushdown", "pushdown.svg"),
    ExerciseIcon("squat", "squat.svg"),
    ExerciseIcon("plate", "plate.svg"),
    ExerciseIcon("pull_up", "pull-up.svg"),
    ExerciseIcon("smiley", "smiley.svg"),
    ExerciseIcon("push_up", "push-up.svg"),
    ExerciseIcon("sit_up", "sit-up.svg"),
    ExerciseIcon("lunge", "lunge.svg"),
    ExerciseIcon("hip_thrust", "hip-thrust.svg"),
    ExerciseIcon("dip", "dip.svg"),
    ExerciseIcon("kettlebell", "kettlebell.svg"),
    ExerciseIcon("lateral_raise", "lateral-raise.svg"),
    ExerciseIcon("barbell", "barbell.svg"),
    ExerciseIcon("medicine_ball", "medicine-ball.svg"),
    ExerciseIcon("stationary_bike", "staionary-bike.svg"), // Note typo in original filename
    ExerciseIcon("leg_press", "leg-press.svg"),
    ExerciseIcon("smith", "smith.svg"),
    ExerciseIcon("cables", "cables.svg"),
    ExerciseIcon("heart_rate", "heart-rate.svg"),
    ExerciseIcon("bands", "bands.svg"),
    ExerciseIcon("rings", "rings.svg")
)

fun getIconUrl(iconName: String?): String {
    val fileName = AVAILABLE_ICONS.find { it.name == iconName }?.fileName ?: "default.svg"
    return "file:///android_asset/fitness_icons/$fileName"
}

@Composable
fun ExerciseAsyncIcon(
    iconName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(getIconUrl(iconName))
            .build(),
        imageLoader = imageLoader,
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
            modifier = Modifier.height(200.dp)
        ) {
            items(AVAILABLE_ICONS) { icon ->
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
