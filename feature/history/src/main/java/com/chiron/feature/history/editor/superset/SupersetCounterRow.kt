package com.chiron.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline

/**
 * A solo exercise toggle, replaced by a membership stepper for supersets.
 *
 * Handles its own layout; callers only need to wire up state changes.
 *
 * @param isSupersetEnabled      Current toggle state.
 * @param onSupersetToggle       Called when the switch is flipped.
 * @param numExercisesInSuperset Current exercise count (shown in stepper).
 * @param onDecrement            Called when − is pressed, including returning to solo.
 * @param onIncrement            Called when + is pressed (guard against max=5 externally).
 */
@Composable
fun SupersetCounterRow(
    isSupersetEnabled: Boolean,
    onSupersetToggle: (Boolean) -> Unit,
    numExercisesInSuperset: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Superset",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isSupersetEnabled) {
            Switch(
                checked = false,
                onCheckedChange = onSupersetToggle,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = CoolGray,
                    uncheckedTrackColor = SolidSlate,
                    uncheckedBorderColor = ThinOutline
                )
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("-") }

                Text(
                    text = numExercisesInSuperset.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = onIncrement,
                    enabled = numExercisesInSuperset < 5,
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+") }
            }
        }

    }
}
