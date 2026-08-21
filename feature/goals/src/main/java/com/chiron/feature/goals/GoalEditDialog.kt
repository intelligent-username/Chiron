package com.chiron.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.ui.components.ExerciseAsyncIcon
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.MonospaceFamily
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import com.chiron.feature.goals.GoalFormState

/**
 * Create/edit goal dialog. Binds directly to the ViewModel-owned [GoalFormState]
 * so search, selection and validation stay live.
 */
@Composable
fun GoalEditDialog(
    form: GoalFormState,
    isEditing: Boolean,
    onNameChange: (String) -> Unit,
    onTargetChange: (Int) -> Unit,
    onToggleExercise: (Long) -> Unit,
    onSearchChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Goal" else "New Goal") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    label = { Text("Goal Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Weekly target",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..7).forEach { target ->
                        val selected = form.weeklyTarget == target
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) ElectricBlue.copy(alpha = 0.15f) else SolidSlate)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) ElectricBlue else ThinOutline,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onTargetChange(target) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$target",
                                color = if (selected) ElectricBlue else CoolGray,
                                fontSize = 13.sp,
                                fontFamily = MonospaceFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = form.exerciseSearchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(form.searchResults, key = { it.id }) { exercise ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleExercise(exercise.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseAsyncIcon(
                                iconName = exercise.iconName,
                                contentDescription = exercise.name,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = exercise.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = exercise.id in form.selectedExerciseIds,
                                onCheckedChange = { onToggleExercise(exercise.id) },
                                colors = CheckboxDefaults.colors(checkedColor = ElectricBlue)
                            )
                        }
                    }
                }

                if (showValidationError && form.selectedExerciseIds.isEmpty()) {
                    Text(
                        text = "Select at least one exercise",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (form.selectedExerciseIds.isEmpty()) {
                        showValidationError = true
                    } else {
                        showValidationError = false
                        onSave()
                    }
                },
                enabled = form.name.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Cancel") }
        }
    )
}
