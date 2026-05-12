package com.chiron.app.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch

/**
 * A multi-line notes field that auto-saves when focus leaves (or IME Done is pressed).
 *
 * In preview mode the field is read-only and styled accordingly.
 *
 * @param value         Current draft value.
 * @param onValueChange Called on each keystroke (update draft state externally).
 * @param committed     The last persisted value, used to decide whether to save.
 * @param onCommit      Called with the trimmed value when it differs from [committed].
 * @param isReadOnly    When `true`, renders as disabled (preview mode).
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
            placeholder = { Text("No notes", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Cursive), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Cursive,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            ),
            modifier = modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
            placeholder = { Text("Notes", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Cursive), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Cursive,
                color = MaterialTheme.colorScheme.onSurface
            ),
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    }
}
