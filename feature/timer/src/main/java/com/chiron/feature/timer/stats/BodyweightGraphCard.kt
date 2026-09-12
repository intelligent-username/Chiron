package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import kotlin.math.roundToInt

@Composable
fun BodyweightGraphCard(
    state: BodyweightUiState,
    localInKg: Boolean,
    unit: String,
    onWeekCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val current = state.stats.current
                val currentDisplay = if (current != null) {
                    val v = if (localInKg) UnitConversion.lbsToKg(current) else current
                    "${UnitConversion.formatNumber(v)} $unit"
                } else "-- $unit"

                Text(
                    text = currentDisplay,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            if (state.points.isEmpty() || state.points.all { it.weightLbs == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bodyweight data for this period",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                BodyweightLineGraph(
                    points = state.points,
                    localInKg = localInKg,
                    mode = state.mode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            // Week-range slider: 2 weeks min, max from first log to today.
            val maxWeeks = kotlin.math.max(3, state.maxWeekCount)
            val currentWeekVal = state.weekCount.toFloat().coerceIn(2f, maxWeeks.toFloat())
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = currentWeekVal,
                onValueChange = { onWeekCountChange(it.roundToInt()) },
                valueRange = 2f..maxWeeks.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}
