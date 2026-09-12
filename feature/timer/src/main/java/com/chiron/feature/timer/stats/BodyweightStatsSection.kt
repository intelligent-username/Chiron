package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@Composable
fun BodyweightStatsSection(
    stats: BodyweightStats,
    localInKg: Boolean,
    unit: String,
    modifier: Modifier = Modifier
) {
    fun fmt(weightLbs: Double?): String {
        if (weightLbs == null) return "--"
        val v = if (localInKg) UnitConversion.lbsToKg(weightLbs) else weightLbs
        return UnitConversion.formatNumber(v)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Hero Row: Current Weight + Period Change Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT",
                        color = CoolGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = fmt(stats.current),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unit,
                            color = CoolGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Period Change Badge
                if (stats.change != null) {
                    val changeVal = if (localInKg) UnitConversion.lbsToKg(stats.change) else stats.change
                    val sign = if (changeVal > 0.0) "+" else ""
                    val isPositive = changeVal > 0.0
                    val isNeutral = kotlin.math.abs(changeVal) < 0.01
                    val badgeColor = when {
                        isNeutral -> CoolGray
                        isPositive -> Color(0xFFFF7B72) // Subtle coral
                        else -> Color(0xFF7EE787) // Subtle mint green
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Net: $sign${UnitConversion.formatNumber(changeVal)} $unit",
                            color = badgeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ThinOutline, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Sub-metrics 3-column layout: Average | Lowest | Highest
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "AVERAGE",
                    value = "${fmt(stats.average)} $unit",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "LOWEST",
                    value = "${fmt(stats.min)} $unit",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "HIGHEST",
                    value = "${fmt(stats.max)} $unit",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-footer: Count of actual inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${stats.count} weigh-in${if (stats.count == 1) "" else "s"} recorded",
                    color = CoolGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = CoolGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = MonospaceFamily
        )
    }
}
