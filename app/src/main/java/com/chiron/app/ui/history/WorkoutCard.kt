package com.chiron.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiron.app.data.entities.WorkoutSession

@Composable
fun WorkoutCard(
    workout: WorkoutSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = workout.dayTag, style = MaterialTheme.typography.titleMedium)
            Text(text = workout.dateIso, style = MaterialTheme.typography.bodyMedium)
            Text(text = workout.locationTag, style = MaterialTheme.typography.bodySmall)
        }
    }
}
