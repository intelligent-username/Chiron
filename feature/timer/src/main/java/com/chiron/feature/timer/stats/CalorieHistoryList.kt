package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val FoodLogDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

/** Food history card: same shell, bounded scrolling rows and pagination as the Weight history list. */
@Composable
fun CalorieHistoryList(
    entries: List<CalorieEntry>,
    onEdit: (CalorieEntry) -> Unit,
    onDelete: (CalorieEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageSize by rememberSaveable { mutableIntStateOf(10) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val totalPages = max(1, (entries.size + pageSize - 1) / pageSize)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val displayedEntries = remember(entries, safePage, pageSize) {
        entries.drop(safePage * pageSize).take(pageSize)
    }

    HistoryListCard(
        modifier = modifier,
        title = "Food log",
        countLabel = if (entries.isEmpty()) null
        else "${entries.size} total ${if (entries.size == 1) "entry" else "entries"}"
    ) {
        if (entries.isEmpty()) {
            Text(
                "No food history yet. Log a food or import your history from the options menu.",
                color = CoolGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                displayedEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${formatCalorieNumber(entry.calories)} kcal",
                                color = Color.White,
                                fontFamily = MonospaceFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                entry.name.ifBlank { "Food" },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${entry.date.format(FoodLogDateFormatter)} · " +
                                    "${formatCalorieNumber(entry.protein)} g protein",
                                color = CoolGray,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { onEdit(entry) }) {
                            Text("Edit", color = ElectricBlue)
                        }
                        TextButton(onClick = { onDelete(entry) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            HistoryPaginationFooter(
                pageSize = pageSize,
                safePage = safePage,
                totalPages = totalPages,
                onPageSizeChange = { pageSize = it; currentPage = 0 },
                onPageChange = { currentPage = it }
            )
        }
    }
}
