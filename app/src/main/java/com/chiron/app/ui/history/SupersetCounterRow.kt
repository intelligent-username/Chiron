package com.chiron.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The "Superset" toggle row plus the ± exercise-count stepper.
 *
 * Handles its own layout; callers only need to wire up state changes.
 *
 * @param isSupersetEnabled      Current toggle state.
 * @param onSupersetToggle       Called when the switch is flipped.
 * @param numExercisesInSuperset Current exercise count (shown in stepper).
 * @param onDecrement            Called when − is pressed (guard against min=2 externally).
 * @param onIncrement            Called when + is pressed (guard against max=5 externally).
 * @param exercisesNeeded        If > 0, an "Add N Exercise(s)" button is shown.
 * @param onAddExercises         Called when the "Add N Exercise(s)" button is pressed.
 */
@Composable
fun SupersetCounterRow(
    isSupersetEnabled: Boolean,
    onSupersetToggle: (Boolean) -> Unit,
    numExercisesInSuperset: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    exercisesNeeded: Int = 0,
    onAddExercises: (() -> Unit)? = null,
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
        Switch(
            checked = isSupersetEnabled,
            onCheckedChange = onSupersetToggle
        )
    }

    if (isSupersetEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Exercises in superset:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+") }
            }
        }

        if (exercisesNeeded > 0 && onAddExercises != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAddExercises,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text("Add $exercisesNeeded Exercise${if (exercisesNeeded > 1) "s" else ""}")
            }
        }
    }
}
