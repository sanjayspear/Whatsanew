package dev.pulse.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction

/**
 * The one text input used across Pulse (intent bar, "Other" category, future forms).
 *
 * Keyboard safety is the *container's* job: anything hosting this field must pad by
 * `WindowInsets.ime` (see [Modifier.keyboardSafe]). Submitting hides the keyboard so the
 * generated surface is visible instead of the keyboard.
 */
@Composable
fun PulseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderColor: androidx.compose.ui.graphics.Color? = null,
    testTag: String = "pulse-text-field",
) {
    val c = Pulse.colors
    val keyboard = LocalSoftwareKeyboardController.current
    Box(modifier) {
        if (value.isEmpty()) BasicText(placeholder, style = PulseType.body.copy(color = placeholderColor ?: c.textSecondary))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PulseType.body.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (value.isNotBlank()) {
                    keyboard?.hide()
                    onSubmit(value)
                }
            }),
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}
