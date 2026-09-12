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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var showDeleteRangeDialog by rememberSaveable { mutableStateOf(false) }

    val unit = if (localInKg) "kg" else "lbs"

    val totalPages = max(1, (entries.size + pageSize - 1) / pageSize)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val displayedEntries = remember(entries, safePage, pageSize) {
        entries.drop(safePage * pageSize).take(pageSize)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header: Title, Count, and Mass-Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (entries.isNotEmpty()) {
                        Text("${entries.size} total weigh-in logs", color = CoolGray, fontSize = 12.sp)
                    }
                }

                if (entries.isNotEmpty()) {
                    TextButton(onClick = { showDeleteRangeDialog = true }) {
                        Text("Delete Range", color = Color(0xFFFF7B72), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ThinOutline, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            if (entries.isEmpty()) {
                Text(
                    "No weigh-ins recorded yet",
                    color = CoolGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // Paginated log rows
            displayedEntries.forEach { entry ->
                val isEditing = editingId == entry.id
                val display = if (localInKg) UnitConversion.lbsToKg(entry.weightLbs) else entry.weightLbs

                if (isEditing) {
                    val parsedDate = runCatching { LocalDate.parse(editDateIso) }.getOrDefault(LocalDate.now())
                    val currentTime = LocalTime.of(editHour, editMinute)
                    val formattedDate = parsedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
                    val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("h:mm a"))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
                        shape = RoundedCornerShape(14.dp),
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
                                            ).show()
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

                            if (editError != null) {
                                Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                                            editError = "Weight must be 20-1500 lbs"
                                            return@Button
                                        }

                                        val selectedDate = runCatching { LocalDate.parse(editDateIso) }.getOrDefault(LocalDate.now())
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

            // Pagination Controls Footer
            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = ThinOutline, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page Size Stepper: [ - ] 10 rows [ + ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Show:", color = CoolGray, fontSize = 12.sp)
                        IconButton(
                            onClick = {
                                val nextSize = (pageSize - 5).coerceIn(5, 50)
                                pageSize = nextSize
                                currentPage = 0
                            },
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
                            onClick = {
                                val nextSize = (pageSize + 5).coerceIn(5, 50)
                                pageSize = nextSize
                                currentPage = 0
                            },
                            enabled = pageSize < 50,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", color = if (pageSize < 50) Color.White else CoolGray.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Back & Forth Navigator: < Page X of Y >
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = { if (safePage > 0) currentPage = safePage - 1 },
                            enabled = safePage > 0
                        ) {
                            Text("<", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${safePage + 1} / $totalPages",
                            color = Color.White,
                            fontFamily = MonospaceFamily,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = { if (safePage < totalPages - 1) currentPage = safePage + 1 },
                            enabled = safePage < totalPages - 1
                        ) {
                            Text(">", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Mass-Delete Date Range Dialog
    if (showDeleteRangeDialog) {
        val zone = ZoneId.systemDefault()
        val defaultStart = remember(entries) {
            val minUtc = entries.minOfOrNull { it.timestampUtc }
            if (minUtc != null) {
                Instant.ofEpochMilli(minUtc).atZone(zone).toLocalDate().toString()
            } else {
                LocalDate.now().minusMonths(1).toString()
            }
        }
        val defaultEnd = remember { LocalDate.now().toString() }

        var startInput by rememberSaveable { mutableStateOf(defaultStart) }
        var endInput by rememberSaveable { mutableStateOf(defaultEnd) }
        var rangeError by rememberSaveable { mutableStateOf<String?>(null) }

        val parsedStart = runCatching { LocalDate.parse(startInput.trim()) }.getOrNull()
        val parsedEnd = runCatching { LocalDate.parse(endInput.trim()) }.getOrNull()

        val matchingCount = remember(parsedStart, parsedEnd, entries) {
            if (parsedStart != null && parsedEnd != null && !parsedStart.isAfter(parsedEnd)) {
                val sUtc = parsedStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val eUtc = parsedEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                entries.count { it.timestampUtc in sUtc..eUtc }
            } else 0
        }

        AlertDialog(
            onDismissRequest = { showDeleteRangeDialog = false },
            containerColor = SolidSlate,
            title = {
                Text("Delete Logs by Date Range", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Delete all weigh-in entries between the start and end dates (inclusive).",
                        color = CoolGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = { startInput = it },
                        label = { Text("Start Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endInput,
                        onValueChange = { endInput = it },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    if (parsedStart == null || parsedEnd == null) {
                        Text("Please use YYYY-MM-DD format", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else if (parsedStart.isAfter(parsedEnd)) {
                        Text("Start date must be on or before end date", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else {
                        Text(
                            "$matchingCount log${if (matchingCount == 1) "" else "s"} will be deleted",
                            color = if (matchingCount > 0) Color(0xFFFF7B72) else CoolGray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    if (rangeError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(rangeError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (parsedStart == null || parsedEnd == null || parsedStart.isAfter(parsedEnd)) {
                            rangeError = "Invalid date range"
                            return@TextButton
                        }
                        val sUtc = parsedStart.atStartOfDay(zone).toInstant().toEpochMilli()
                        val eUtc = parsedEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                        onDeleteRange(sUtc, eUtc)
                        showDeleteRangeDialog = false
                    },
                    enabled = parsedStart != null && parsedEnd != null && !parsedStart.isAfter(parsedEnd) && matchingCount > 0,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7B72))
                ) {
                    Text("Delete $matchingCount Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRangeDialog = false }) {
                    Text("Cancel", color = CoolGray)
                }
            }
        )
    }
}

private fun formatHistoryTimestamp(timestampUtc: Long): String {
    val fmt = DateTimeFormatter.ofPattern("EEEE MMM d, h:mm a")
    return runCatching {
        Instant.ofEpochMilli(timestampUtc).atZone(ZoneId.systemDefault()).format(fmt)
    }.getOrDefault("Unknown date")
}
