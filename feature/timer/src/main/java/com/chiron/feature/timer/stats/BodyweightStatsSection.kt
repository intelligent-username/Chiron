package com.chiron.feature.timer

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.ui.theme.CoolGray
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            StatRow("Current", stats.current?.let { "${fmt(it)} $unit" } ?: "--")
            val sign = if ((stats.change ?: 0.0) > 0) "+" else ""
            StatRow("Change", stats.change?.let { "$sign${fmt(it)} $unit" } ?: "--")
            StatRow("Average", stats.average?.let { "${fmt(it)} $unit" } ?: "--")
            StatRow("Min", stats.min?.let { "${fmt(it)} $unit" } ?: "--")
            StatRow("Max", stats.max?.let { "${fmt(it)} $unit" } ?: "--")
            StatRow("Entries", "${stats.count}")
        }
    }
}

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CoolGray, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
