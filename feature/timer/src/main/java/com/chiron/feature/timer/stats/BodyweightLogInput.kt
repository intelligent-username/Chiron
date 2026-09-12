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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

@Composable
fun BodyweightLogInput(
    localInKg: Boolean,
    onLog: (Double) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSubmitMs by rememberSaveable { mutableStateOf(0L) }
    val unit = if (localInKg) "kg" else "lbs"

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
        lastSubmitMs = now
        localError = null
        onLog(lbs)
        text = ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SolidSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThinOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
