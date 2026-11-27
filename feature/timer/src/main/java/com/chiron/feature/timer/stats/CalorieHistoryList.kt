package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.CalorieEntry
import com.chiron.core.common.CalorieTarget
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

private val FoodLogDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun CalorieHistoryList(
    entries: List<CalorieEntry>,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    editingDate: LocalDate? = null,
    editor: @Composable () -> Unit = {},
    editorMotionEnabled: Boolean = true,
    controlledPage: Int? = null,
    controlledPageSize: Int? = null,
    onControlledPageChange: (Int) -> Unit = {},
    onControlledPageSizeChange: (Int) -> Unit = {},
    target: CalorieTarget? = null
) {
    var pageSize by rememberSaveable { mutableIntStateOf(10) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val days = remember(entries) { entries.groupBy { it.date }.toSortedMap(compareByDescending { it }).entries.toList() }
    val safePageSize = (controlledPageSize ?: pageSize).coerceIn(5, 50)
    val totalPages = max(1, (days.size + safePageSize - 1) / safePageSize)
    val safePage = (controlledPage ?: currentPage).coerceIn(0, totalPages - 1)
    LaunchedEffect(safePage, safePageSize) {
        currentPage = safePage
        pageSize = safePageSize
    }
    val displayedDays = remember(days, safePage, safePageSize) {
        days.drop(safePage * safePageSize).take(safePageSize)
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

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
            Column(Modifier.fillMaxWidth()) {
                displayedDays.forEach { day ->
                  // Page identity disposes any in-flight row geometry; pagination is instant.
                  key(safePage, safePageSize, day.key) {
                    StatsEditorGeometry(day.key == editingDate, enabled = editorMotionEnabled) {
                    if (day.key == editingDate) {
                        editor()
                    } else {
                    val calories = day.value.sumOf { it.calories }
                    val protein = day.value.sumOf { it.protein }
                    val numberTextWidth = listOf(
                        foodDayValueText(calories, target?.calories),
                        foodDayValueText(protein, target?.protein)
                    ).maxOf { text ->
                        textMeasurer.measure(
                            text,
                            style = TextStyle(fontFamily = MonospaceFamily, fontSize = 11.sp)
                        ).size.width
                    }
                    val unitTextWidth = textMeasurer.measure(
                        "cal", style = TextStyle(fontFamily = MonospaceFamily, fontSize = 11.sp)
                    ).size.width
                    val numberWidth = with(density) { numberTextWidth.toDp() }
                    val sideWidth = maxOf(
                        112.dp * density.fontScale,
                        with(density) { (numberTextWidth + unitTextWidth).toDp() } + 28.dp
                    )
                    val summary: @Composable () -> Unit = {
                        FoodDayTotals(calories, protein, target, numberWidth, Modifier.width(sideWidth))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            day.key.format(FoodLogDateFormatter),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        summary()
                    }
                    day.value.forEachIndexed { index, entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                              Text(entry.name.ifBlank { "Unnamed food" },
                                  color = MaterialTheme.colorScheme.onSurface,
                                  fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                  maxLines = 1, overflow = TextOverflow.Ellipsis)
                             Text("${formatCalorieNumber(entry.calories)}cal ${formatCalorieNumber(entry.protein)}p",
                                 color = CoolGray, fontFamily = MonospaceFamily, fontSize = 12.sp,
                                 maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index == day.value.lastIndex) {
                            Box(Modifier.padding(start = 12.dp)) {
                                FoodDayActions(day.key, onEdit, onDelete, Modifier.width(sideWidth))
                            }
                        }
                        }
                    }
                    }
                    }
                    HorizontalDivider(color = ThinOutline, thickness = 1.5.dp)
                  }
                }
            }

            HistoryPaginationFooter(
                pageSize = safePageSize,
                safePage = safePage,
                totalPages = totalPages,
                onPageSizeChange = {
                    pageSize = it.coerceIn(5, 50); currentPage = 0
                    onControlledPageSizeChange(pageSize)
                },
                onPageChange = {
                    currentPage = it.coerceIn(0, totalPages - 1)
                    onControlledPageChange(currentPage)
                }
            )
        }
    }
}

private fun foodDayValueText(value: Double, goal: Double?): String =
    formatCalorieNumber(value) + (goal?.let { "/${formatCalorieNumber(it)}" } ?: "")

@Composable
private fun FoodDayTotals(
    calories: Double,
    protein: Double,
    target: CalorieTarget?,
    numberWidth: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, ThinOutline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            FoodDayTotalLine(
                foodDayValueText(calories, target?.calories), "cal", numberWidth
            )
            FoodDayTotalLine(
                foodDayValueText(protein, target?.protein), "p", numberWidth
            )
        }
    }
}

@Composable
private fun FoodDayTotalLine(value: String, unit: String, numberWidth: Dp) {
    val style = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = MonospaceFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, modifier = Modifier.width(numberWidth), style = style, maxLines = 1)
        Text(unit, style = style, maxLines = 1)
    }
}

@Composable
private fun FoodDayActions(
    date: LocalDate,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            onClick = { onEdit(date) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Edit", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                color = ElectricBlue)
        }
        TextButton(
            onClick = { onDelete(date) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Delete", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
