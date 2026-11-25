package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

/**
 * One set of plot dimensions shared by the Weight and Calories tabs. Both charts are the
 * same element with different data, so a tab switch only crossfades series and labels.
 */
internal object StatsChartGeometry {
    val HeaderHeight = 72.dp
    val UnitsHeight = 16.dp
    val RowHeight = 16.dp
    val ChartHeight = 260.dp
    val PlotLeftInset = 56.dp
    val PlotRightInset = 16.dp
    val PlotTopInset = 12.dp
    val PlotBottomInset = 24.dp
    val PlotOverlayGap = 4.dp
}

/**
 * Fixed-slot chart card: header, axis unit labels, plot, and range controls occupy the same
 * space in both tabs regardless of their content.
 */
@Composable
internal fun StatsChartCard(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    units: @Composable () -> Unit,
    chart: @Composable () -> Unit,
    footer: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = StatsChartGeometry.HeaderHeight),
                contentAlignment = Alignment.CenterStart
            ) { header() }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = StatsChartGeometry.UnitsHeight),
                contentAlignment = Alignment.CenterStart
            ) { units() }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(StatsChartGeometry.ChartHeight)) { chart() }
            Spacer(Modifier.height(8.dp))
            footer()
        }
    }
}
