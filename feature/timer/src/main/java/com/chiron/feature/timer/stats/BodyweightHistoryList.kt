package com.chiron.feature.timer

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun BodyweightHistoryList(
    entries: List<BodyWeightEntry>,
    localInKg: Boolean,
    onEdit: (Long, Double, Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteRange: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    // Pagination state: customizable page size bounded 5..50, default 10
    var pageSize by rememberSaveable { mutableIntStateOf(10) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    // Inline row editing state
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editWeightText by rememberSaveable { mutableStateOf("") }
    var editDateIso by rememberSaveable { mutableStateOf("") }
    var editHour by rememberSaveable { mutableIntStateOf(12) }
    var editMinute by rememberSaveable { mutableIntStateOf(0) }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }

    // Mass-delete dialog state
    var showDeleteRangeDialog by remember { mutableStateOf(false) }

    val unit = if (localInKg) "kg" else "lbs"

    val safePageSize = pageSize.coerceIn(5, 50)
    val totalPages = max(1, (entries.size + safePageSize - 1) / safePageSize)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    LaunchedEffect(safePage, safePageSize) {
        currentPage = safePage
        pageSize = safePageSize
    }
    val displayedEntries = remember(entries, safePage, safePageSize) {
        entries.drop(safePage * safePageSize).take(safePageSize)
    }

    val deleteRangeAction: (@Composable () -> Unit)? = if (entries.isNotEmpty()) {
        {
            TextButton(onClick = { showDeleteRangeDialog = true }) {
                Text("Delete Range", color = Color(0xFFFF7B72), fontSize = 13.sp)
            }
        }
    } else null

    HistoryListCard(
        modifier = modifier,
        title = "History",
        countLabel = if (entries.isNotEmpty()) "${entries.size} total weigh-in logs" else null,
        action = deleteRangeAction
    ) {

            if (entries.isEmpty()) {
                Text(
                    "No weigh-ins recorded yet",
                    color = CoolGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // Paginated log rows
             displayedEntries.forEachIndexed { index, entry ->
               key(safePage, safePageSize, entry.id) {
                if (index > 0) HorizontalDivider(color = ThinOutline.copy(alpha = 0.7f), thickness = 1.dp)
                val isEditing = editingId == entry.id
                val display = if (localInKg) UnitConversion.lbsToKg(entry.weightLbs) else entry.weightLbs

                StatsEditorGeometry(isEditing, enabled = editError == null) {
                if (isEditing) {
                    val parsedDate = runCatching { LocalDate.parse(editDateIso) }.getOrDefault(LocalDate.now())
                    val currentTime = LocalTime.of(editHour, editMinute)
                    val formattedDate = parsedDate.format(EditDateFormatter)
                    val formattedTime = currentTime.format(EditTimeFormatter)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
                        shape = EditCardShape,
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editWeightText,
                                onValueChange = {
                                    editWeightText = it
                                    editError = null
                                },
                                label = { Text("Weight ($unit)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Date Picker Box
                                Column(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                                        .clickable {
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    editDateIso = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                                },
                                                parsedDate.year,
                                                parsedDate.monthValue - 1,
                                                parsedDate.dayOfMonth
                                            ).apply {
                                                datePicker.maxDate = System.currentTimeMillis()
                                            }.show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "DATE",
                                        color = CoolGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formattedDate,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Edit",
                                            color = ElectricBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Time Picker Box
                                Column(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                                        .clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    editHour = hourOfDay
                                                    editMinute = minute
                                                },
                                                editHour,
                                                editMinute,
                                                false
                                            ).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "TIME",
                                        color = CoolGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formattedTime,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = MonospaceFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Edit",
                                            color = ElectricBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        editingId = null
                                        editError = null
                                    }
                                ) {
                                    Text("Cancel", color = CoolGray, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val parsed = editWeightText.trim().toDoubleOrNull()
                                        if (parsed == null || !parsed.isFinite()) {
                                            editError = "Enter a valid number"
                                            return@Button
                                        }
                                        val lbs = if (localInKg) UnitConversion.kgToLbs(parsed) else parsed
                                        if (lbs <= 0.0) {
                                            editError = "Enter weight > 0"
                                            return@Button
                                        }
                                        if (lbs < 20.0 || lbs > 1500.0) {
                                            editError = if (localInKg) "Weight must be 9-680 kg" else "Weight must be 20-1500 lbs"
                                            return@Button
                                        }

                                        val selectedDate = runCatching { LocalDate.parse(editDateIso) }.getOrDefault(LocalDate.now())
                                        if (selectedDate.isAfter(LocalDate.now())) {
                                            editError = FUTURE_WEIGHT_DATE_ERROR
                                            return@Button
                                        }
                                        val selectedTime = LocalTime.of(editHour, editMinute)
                                        val newTimestampUtc = selectedDate
                                            .atTime(selectedTime)
                                            .atZone(zone)
                                            .toInstant()
                                            .toEpochMilli()

                                        onEdit(entry.id, lbs, newTimestampUtc)
                                        editingId = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${UnitConversion.formatNumber(display)} $unit",
                                color = Color.White,
                                fontFamily = MonospaceFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(formatHistoryTimestamp(entry.timestampUtc), color = CoolGray, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                val zdt = Instant.ofEpochMilli(entry.timestampUtc).atZone(zone)
                                editingId = entry.id
                                editWeightText = UnitConversion.formatNumber(display)
                                editDateIso = zdt.toLocalDate().toString()
                                editHour = zdt.hour
                                editMinute = zdt.minute
                                editError = null
                            }
                        ) {
                            Text("Edit", color = ElectricBlue)
                        }
                        TextButton(onClick = { onDelete(entry.id) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                }
                // Validation is not part of the resizing editor body.
                if (isEditing && editError != null) {
                    Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
               }
            }

        if (entries.isNotEmpty()) {
            HistoryPaginationFooter(
                pageSize = safePageSize,
                safePage = safePage,
                totalPages = totalPages,
                onPageSizeChange = { pageSize = it.coerceIn(5, 50); currentPage = 0 },
                onPageChange = { currentPage = it.coerceIn(0, totalPages - 1) }
            )
        }
    }

    if (showDeleteRangeDialog) {
        WeightRangeDeletionDialog(
            entries = entries,
            onDismiss = { showDeleteRangeDialog = false },
            onDeleteRange = onDeleteRange
        )
    }
}

private enum class RangeDeletionStage { Choose, Confirm, Final }

@Composable
private fun WeightRangeDeletionDialog(
    entries: List<BodyWeightEntry>,
    onDismiss: () -> Unit,
    onDeleteRange: (Long, Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var startInput by remember {
        mutableStateOf(entries.minOfOrNull { it.timestampUtc }?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
        } ?: LocalDate.now(zone).minusMonths(1).toString())
    }
    var endInput by remember { mutableStateOf(LocalDate.now(zone).toString()) }
    var stage by remember { mutableStateOf(RangeDeletionStage.Choose) }
    var review by remember { mutableStateOf<WeightRangeReview?>(null) }
    var rangeError by remember { mutableStateOf<String?>(null) }
    var finalReady by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val preview = remember(startInput, endInput, entries, zone) {
        reviewWeightRange(startInput, endInput, entries, zone)
    }
    val frozen = review

    fun close() {
        closed = true
        review = null
        finalReady = false
        stage = RangeDeletionStage.Choose
        onDismiss()
    }
    fun requireFreshReview() {
        review = null
        finalReady = false
        stage = RangeDeletionStage.Choose
        rangeError = "Logs or time zone changed. Review the range and count again."
    }

    // This pause only unlocks the final button. It never authorizes or submits deletion.
    LaunchedEffect(stage, frozen) {
        finalReady = false
        if (stage == RangeDeletionStage.Final && frozen != null && !closed) {
            delay(2_000)
            finalReady = true
        }
    }

    AlertDialog(
        onDismissRequest = { close() },
        containerColor = SolidSlate,
        title = {
            Text(when (stage) {
                RangeDeletionStage.Choose -> "Review range deletion"
                RangeDeletionStage.Confirm -> "Confirm range deletion"
                RangeDeletionStage.Final -> "Final deletion confirmation"
            })
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stage == RangeDeletionStage.Choose) {
                    Text("Choose inclusive local dates. Review the affected logs before confirming.")
                    OutlinedTextField(
                        startInput, { startInput = it; rangeError = null },
                        label = { Text("Start date (YYYY-MM-DD)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        endInput, { endInput = it; rangeError = null },
                        label = { Text("End date (YYYY-MM-DD)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text("Time zone: ${zone.id}")
                    if (preview == null) {
                        Text("Enter a valid ordered date range containing at least one log.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("${preview.startDate} through ${preview.endDate} (inclusive)")
                        Text("${preview.entries.size} weigh-in logs selected")
                    }
                } else if (frozen != null) {
                    Text("${frozen.startDate} through ${frozen.endDate} (inclusive)")
                    Text("Time zone: ${frozen.zoneId}")
                    Text("Delete all ${frozen.entries.size} weigh-in logs in this range? This cannot be undone.")
                    if (stage == RangeDeletionStage.Final && !finalReady) {
                        Text("Wait 2 seconds, then confirm deletion.")
                    }
                }
                rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (stage) {
                RangeDeletionStage.Choose -> TextButton(
                    enabled = preview != null && !closed,
                    onClick = {
                        if (closed || stage != RangeDeletionStage.Choose) return@TextButton
                        val current = reviewWeightRange(startInput, endInput, entries, ZoneId.systemDefault())
                        if (current == null) {
                            rangeError = "Enter a valid ordered date range containing at least one log."
                        } else {
                            review = current
                            rangeError = null
                            stage = RangeDeletionStage.Confirm
                        }
                    }
                ) { Text("Review deletion") }
                RangeDeletionStage.Confirm -> TextButton(
                    enabled = frozen != null && !closed,
                    onClick = {
                        if (closed || stage != RangeDeletionStage.Confirm || frozen == null ||
                            review !== frozen) return@TextButton
                        if (!weightRangeUnchanged(frozen, entries, ZoneId.systemDefault())) {
                            requireFreshReview()
                        } else {
                            finalReady = false
                            stage = RangeDeletionStage.Final
                        }
                    }
                ) { Text("Confirm") }
                RangeDeletionStage.Final -> TextButton(
                    enabled = finalReady && frozen != null && !closed,
                    onClick = {
                        if (closed || stage != RangeDeletionStage.Final || !finalReady ||
                            frozen == null || review !== frozen) return@TextButton
                        if (!weightRangeUnchanged(frozen, entries, ZoneId.systemDefault())) {
                            requireFreshReview()
                        } else {
                            close()
                            onDeleteRange(frozen.startUtc, frozen.endUtc)
                        }
                    }
                ) { Text("Confirm deletion", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancel") } }
    )
}

private val EditCardShape = RoundedCornerShape(14.dp)
private val EditDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val EditTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val HistoryTimestampFormatter = DateTimeFormatter.ofPattern("EEEE MMM d, h:mm a")

private fun formatHistoryTimestamp(timestampUtc: Long): String {
    return runCatching {
        Instant.ofEpochMilli(timestampUtc).atZone(ZoneId.systemDefault()).format(HistoryTimestampFormatter)
    }.getOrDefault("Unknown date")
}
