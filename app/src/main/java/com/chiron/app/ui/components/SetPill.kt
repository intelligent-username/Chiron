package com.chiron.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.prefs.DistanceUnit
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.util.UnitConversion

/**
 * Primary SetPill overload: accepts a pre-built display string.
 * All other overloads delegate here.
 */
@Composable
fun SetPill(
    displayText: String,
    isPr: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    val backgroundColor = SolidSlate
    val borderColor = if (isPr) PrGold else ThinOutline

    // Responsive font size: shrink if text is long to fit on one line
    val baseStyle = MaterialTheme.typography.labelLarge
    val responsiveFontSize = when {
        displayText.length > 16 -> baseStyle.fontSize * 0.80f
        displayText.length > 14 -> baseStyle.fontSize * 0.85f
        displayText.length > 12 -> baseStyle.fontSize * 0.90f
        else -> baseStyle.fontSize
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = baseStyle.copy(fontSize = responsiveFontSize),
            color = if (isPr) PrGold else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * Config-aware overload: builds the display string from set + exercise config + prefs.
 */
@Composable
fun SetPill(
    set: SetEntry,
    exercise: Exercise,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    isPr: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SetPill(
        displayText = UnitConversion.formatSet(set, exercise, displayInKg, distanceUnit),
        isPr = isPr,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Legacy weight+reps overload: kept for backward compatibility.
 * All existing call sites (ExerciseEntryCard, SupersetExerciseColumn, etc.) continue to compile.
 */
@Composable
fun SetPill(
    weightLbs: Double?,
    reps: Int?,
    displayInKg: Boolean,
    isPr: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weightText = if (weightLbs != null) {
        if (displayInKg) {
            val kg = UnitConversion.lbsToDisplayKg(weightLbs)
            "${formatNumber(kg)}kgs"
        } else {
            "${formatNumber(weightLbs)}lbs"
        }
    } else "—"

    val repsText = reps?.toString() ?: "—"
    val displayText = "$weightText × $repsText"

    SetPill(
        displayText = displayText,
        isPr = isPr,
        onClick = onClick,
        modifier = modifier
    )
}

private fun formatNumber(value: Double): String {
    val formatted = String.format("%.2f", value)
    return formatted.trimEnd('0').trimEnd('.')
}

/**
 * Fallback overload when Exercise is not available (e.g. preview mode).
 * Infers the display format from the non-null fields of SetEntry.
 */
@Composable
fun SetPill(
    set: SetEntry,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    isPr: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weightText = set.weightLbs?.let {
        if (displayInKg) "${UnitConversion.formatNumber(UnitConversion.lbsToDisplayKg(it))}kgs"
        else "${UnitConversion.formatNumber(it)}lbs"
    }
    val repsText = set.reps?.toString()
    val timeText = set.durationSeconds?.let { UnitConversion.formatDuration(it) }
    val distText = set.distanceMeters?.let {
        if (distanceUnit == DistanceUnit.FEET) "${UnitConversion.formatNumber(UnitConversion.metersToFeet(it))}ft"
        else "${UnitConversion.formatNumber(it)}m"
    }

    val parts = listOfNotNull(weightText, repsText, timeText, distText)
    val displayText = if (parts.isEmpty()) "—" else parts.joinToString(" × ")

    SetPill(
        displayText = displayText,
        isPr = isPr,
        onClick = onClick,
        modifier = modifier
    )
}
