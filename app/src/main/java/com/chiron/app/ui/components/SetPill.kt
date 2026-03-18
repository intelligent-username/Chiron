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
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.util.UnitConversion

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

    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = if (isPr) {
        PrGold.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isPr) PrGold else MaterialTheme.colorScheme.outline
    
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
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = baseStyle.copy(fontSize = responsiveFontSize),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun formatNumber(value: Double): String {
    val formatted = String.format("%.2f", value)
    return formatted.trimEnd('0').trimEnd('.')
}
