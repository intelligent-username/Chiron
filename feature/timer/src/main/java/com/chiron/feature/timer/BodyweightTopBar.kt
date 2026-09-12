package com.chiron.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BodyweightTopBar(
    localInKg: Boolean,
    onToggleUnit: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(onClick = onImportClick) {
            Text("Import")
        }
        OutlinedButton(
            onClick = onToggleUnit,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(if (localInKg) "kg" else "lbs")
        }
    }
}
