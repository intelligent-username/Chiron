package com.chiron.app.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.ThinOutline
import kotlinx.coroutines.launch

/**
 * A multi-line notes field that auto-saves when focus leaves (or IME Done is pressed).
 *
 * In preview mode the field is read-only and styled accordingly.
 *
 * @param value Current draft value.
 * @param onValueChange Called on each keystroke (update draft state externally).
 * @param committed The last persisted value, used to decide whether to save.
 * @param onCommit Called with the trimmed value when it differs from [committed].
 * @param isReadOnly When `true`, renders as disabled (preview mode).
 * @param focusRequester Optional [FocusRequester] to attach when the field is interactive.
 */
@Composable
fun ExerciseNotesField(
    value: String,
    onValueChange: (String) -> Unit,
    committed: String,
    onCommit: suspend (String) -> Unit,
    isReadOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (isReadOnly && value.isBlank()) return

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun maybeSave() {
        val normalized = value.trim()
        if (normalized != committed.trim()) {
            scope.launch { onCommit(normalized) }
        }
    }

    if (isReadOnly) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            enabled = false,
            placeholder = {
                Text(
                    "No notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolGray.copy(alpha = 0.5f)
                )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = CoolGray
            ),
            modifier = modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = SolidSlate,
                disabledBorderColor = ThinOutline.copy(alpha = 0.3f),
                disabledTextColor = CoolGray
            )
        )
    } else {
        val fieldModifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                if (!state.isFocused) maybeSave()
            }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    "Notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolGray
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            ),
            label = {
                Text(
                    "Exercise notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolGray
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    maybeSave()
                    focusManager.clearFocus()
                }
            ),
            modifier = fieldModifier,
            minLines = 1,
            maxLines = 3,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SolidSlate,
                unfocusedContainerColor = SolidSlate,
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = ThinOutline,
                cursorColor = ElectricBlue,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = ElectricBlue,
                unfocusedLabelColor = CoolGray
            )
        )
    }
}
