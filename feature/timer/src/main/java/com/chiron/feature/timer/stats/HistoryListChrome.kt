package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

internal val HistoryCardShape = RoundedCornerShape(16.dp)

/** Shared shell for the Weight and Food history cards. */
@Composable
internal fun HistoryListCard(
    modifier: Modifier = Modifier,
    title: String,
    countLabel: String?,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = HistoryCardShape,
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (countLabel != null) {
                        Text(countLabel, color = CoolGray, fontSize = 12.sp)
                    }
                }
                action?.invoke()
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ThinOutline, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            content()
        }
    }
}

/** Page-size stepper plus pager shared by every history list. */
@Composable
internal fun HistoryPaginationFooter(
    pageSize: Int,
    safePage: Int,
    totalPages: Int,
    onPageSizeChange: (Int) -> Unit,
    onPageChange: (Int) -> Unit
) {
    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = ThinOutline, thickness = 0.5.dp)
    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Show:", color = CoolGray, fontSize = 12.sp)
            IconButton(
                onClick = { onPageSizeChange((pageSize - 5).coerceIn(5, 50)) },
                enabled = pageSize > 5,
                modifier = Modifier.size(28.dp)
            ) {
                Text("-", color = if (pageSize > 5) Color.White else CoolGray.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
            Text(
                "$pageSize",
                color = ElectricBlue,
                fontFamily = MonospaceFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            IconButton(
                onClick = { onPageSizeChange((pageSize + 5).coerceIn(5, 50)) },
                enabled = pageSize < 50,
                modifier = Modifier.size(28.dp)
            ) {
                Text("+", color = if (pageSize < 50) Color.White else CoolGray.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(onClick = { if (safePage > 0) onPageChange(safePage - 1) }, enabled = safePage > 0) {
                Text("<", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "${safePage + 1} / $totalPages",
                color = Color.White,
                fontFamily = MonospaceFamily,
                fontSize = 12.sp
            )
            TextButton(
                onClick = { if (safePage < totalPages - 1) onPageChange(safePage + 1) },
                enabled = safePage < totalPages - 1
            ) {
                Text(">", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
