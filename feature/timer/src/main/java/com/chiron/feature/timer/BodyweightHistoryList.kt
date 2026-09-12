package com.chiron.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BodyweightHistoryList(
    entries: List<BodyWeightEntry>,
    localInKg: Boolean,
    onEdit: (Long, Double) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editInKg by rememberSaveable { mutableStateOf(localInKg) }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    var displayLimit by remember { mutableIntStateOf(30) }
    val unit = if (localInKg) "kg" else "lbs"

    val displayedEntries = remember(entries, displayLimit) {
        if (displayLimit >= entries.size) entries else entries.take(displayLimit)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (entries.isNotEmpty()) {
                    Text("${entries.size} logs", color = CoolGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Text("No weigh-ins yet", color = CoolGray, fontSize = 14.sp)
            }

            displayedEntries.forEach { entry ->
                val display = if (localInKg) UnitConversion.lbsToKg(entry.weightLbs) else entry.weightLbs

                if (editingId == entry.id) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val parsed = editText.trim().toDoubleOrNull()
                                if (parsed == null || !parsed.isFinite()) {
                                    editError = "Enter a valid number"
                                    return@TextButton
                                }
                                val lbs = if (editInKg) UnitConversion.kgToLbs(parsed) else parsed
                                if (lbs <= 0.0) {
                                    editError = "Enter a weight above 0"
                                    return@TextButton
                                }
                                if (lbs < 20.0 || lbs > 1500.0) {
                                    editError = "Enter a weight between 20 and 1500 lbs"
                                    return@TextButton
                                }
                                editError = null
                                onEdit(entry.id, lbs)
                                editingId = null
                            }) {
                                Text("Save")
                            }
                            TextButton(onClick = { editingId = null; editError = null }) {
                                Text("Cancel")
                            }
                        }
                        if (editError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${UnitConversion.formatNumber(display)} $unit",
                                color = Color.White,
                                fontFamily = MonospaceFamily,
                                fontSize = 14.sp
                            )
                            Text(formatHistoryTimestamp(entry.timestampUtc), color = CoolGray, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            editingId = entry.id
                            editInKg = localInKg
                            editError = null
                            editText = UnitConversion.formatNumber(display)
                        }) {
                            Text("Edit")
                        }
                        TextButton(onClick = { onDelete(entry.id) }) {
                            Text("Delete")
                        }
                    }
                }
            }

            if (entries.size > displayLimit) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { displayLimit += 50 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show more (${entries.size - displayLimit} remaining)")
                }
            }
        }
    }
}

private fun formatHistoryTimestamp(timestampUtc: Long): String {
    val fmt = DateTimeFormatter.ofPattern("EEEE MMM d, h:mm a")
    return runCatching {
        Instant.ofEpochMilli(timestampUtc).atZone(ZoneId.systemDefault()).format(fmt)
    }.getOrDefault("Unknown date")
}
