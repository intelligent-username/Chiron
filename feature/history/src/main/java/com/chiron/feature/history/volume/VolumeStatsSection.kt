package com.chiron.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VolumeStatsSection(
    stats: VolumeStats,
    displayInKg: Boolean,
    modifier: Modifier = Modifier
) {
    val unit = if (displayInKg) "kg" else "lbs"
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            StatRow("This week", "${stats.thisWeek.formatVolume(displayInKg)} $unit")
            StatRow("Last week", "${stats.lastWeek.formatVolume(displayInKg)} $unit")
            StatRow("Rolling weekly avg", "${stats.rollingWeeklyAvg.formatVolume(displayInKg)} $unit")
            
            val sign = if (stats.rollingVolChange > 0) "+" else ""
            StatRow("Rolling vol change", "$sign${stats.rollingVolChange.formatVolume(displayInKg)} $unit")
            
            StatRow("Highest ever weekly", "${stats.highestEver.formatVolume(displayInKg)} $unit")
            StatRow("Lowest ever weekly", "${stats.lowestEver.formatVolume(displayInKg)} $unit")
            StatRow("All time total", "${stats.allTimeTotal.formatVolume(displayInKg)} $unit")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8B949E), fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
