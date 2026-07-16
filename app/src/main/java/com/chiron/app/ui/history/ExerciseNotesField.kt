package com.chiron.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

    val containerColor = if (isReadOnly) SolidSlate else Color.Transparent
    val borderColor = if (isReadOnly) ThinOutline.copy(alpha = 0.3f) else ThinOutline.copy(alpha = 0.4f)
    val textStyle = if (isReadOnly) {
        MaterialTheme.typography.bodySmall.copy(color = CoolGray)
    } else {
        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    }
    val placeholderText = if (isReadOnly) "No notes" else "Notes"

    Column(modifier = modifier.fillMaxWidth()) {
        if (!isReadOnly) {
            Text(
                text = "Exercise notes",
                style = MaterialTheme.typography.labelSmall,
                color = CoolGray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        val fieldModifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                if (!state.isFocused) maybeSave()
            }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !isReadOnly,
            textStyle = textStyle,
            cursorBrush = SolidColor(ElectricBlue),
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
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerColor, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = textStyle,
                            color = CoolGray.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
