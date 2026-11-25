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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BodyweightStatsSection(
    stats: BodyweightStats,
    entries: List<BodyWeightEntry>,
    localInKg: Boolean,
    unit: String,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val selection = BodyweightPointCalculator.selectLatestWeights(entries, today, zone)
    val latest = selection.latest
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (latest == null || latest.date == today) "Current weight" else "Latest weight",
                        color = CoolGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        selection.previousDay?.let { previous ->
                            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                                Text(
                                    text = previous.date.format(dateFormatter),
                                    color = CoolGray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${presentedWeight(previous.entry.weightLbs, localInKg)} $unit",
                                    color = CoolGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontFamily = MonospaceFamily
                                )
                            }
                        }
                        Text(
                            text = presentedWeight(latest?.entry?.weightLbs, localInKg),
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
                    if (latest != null && latest.date != today) {
                        Text(
                            text = latest.date.format(dateFormatter),
                            color = CoolGray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Period Change Badge
                if (stats.change != null) {
                    val changeVal = statsPresentedNumber(
                        if (localInKg) UnitConversion.lbsToKg(stats.change) else stats.change,
                        localInKg)
                    val sign = if (changeVal > 0.0) "+" else ""
                    val badgeColor = CoolGray

                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$sign${UnitConversion.formatNumber(changeVal)} $unit in range",
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
                    label = "Average",
                    value = "${presentedWeight(stats.average, localInKg)} $unit",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Lowest",
                    value = "${presentedWeight(stats.min, localInKg)} $unit",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Highest",
                    value = "${presentedWeight(stats.max, localInKg)} $unit",
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
private fun presentedWeight(value: Double?, inKg: Boolean): String {
    if (value == null) return "--"
    val target = if (inKg) UnitConversion.lbsToKg(value) else value
    return UnitConversion.formatNumber(statsPresentedNumber(target, inKg))
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = CoolGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
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
