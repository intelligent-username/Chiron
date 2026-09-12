package com.chiron.feature.timer

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BodyweightLogInput(
    localInKg: Boolean,
    onLog: (Double, Long) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSubmitMs by rememberSaveable { mutableStateOf(0L) }
    var logDateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val unit = if (localInKg) "kg" else "lbs"
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val selectedDate = runCatching { LocalDate.parse(logDateIso) }.getOrDefault(today)
        .coerceAtMost(today)
    val dateLabel = if (selectedDate == today) "Today"
        else selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    fun showDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                logDateIso = LocalDate.of(year, month + 1, dayOfMonth).coerceAtMost(today).toString()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    fun submit() {
        val now = System.currentTimeMillis()
        if (now - lastSubmitMs < 500L) return
        val parsed = text.trim().toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) {
            localError = "Enter a valid number"
            return
        }
        val lbs = if (localInKg) UnitConversion.kgToLbs(parsed) else parsed
        if (lbs <= 0.0) {
            localError = "Enter a weight above 0"
            return
        }
        if (lbs < 20.0 || lbs > 1500.0) {
            localError = "Enter a weight between 20 and 1500 lbs"
            return
        }
        val stampUtc = if (selectedDate == today) now
        else selectedDate.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        lastSubmitMs = now
        localError = null
        onLog(lbs, stampUtc)
        text = ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        logDateIso = selectedDate.minusDays(1).toString()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("<", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showDatePicker() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateLabel,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                IconButton(
                    onClick = {
                        logDateIso = selectedDate.plusDays(1).coerceAtMost(today).toString()
                    },
                    enabled = selectedDate < today,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        ">",
                        color = if (selectedDate < today) Color.White else CoolGray.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { submit() },
                    enabled = text.trim().isNotEmpty()
                ) {
                    Text("Log")
                }
            }

            val msg = localError ?: error
            if (msg != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}
