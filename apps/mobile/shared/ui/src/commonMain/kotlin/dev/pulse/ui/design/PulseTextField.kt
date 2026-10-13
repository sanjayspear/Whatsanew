package dev.pulse.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.input.KeyboardType

/**
 * The one text input used across Pulse (intent bar, "Other" category, future forms).
 *
 * Keyboard safety is the *container's* job: anything hosting this field must pad by
 * `WindowInsets.ime` (see [Modifier.keyboardSafe]). Submitting hides the keyboard so the
 * generated surface is visible instead of the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PulseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderColor: androidx.compose.ui.graphics.Color? = null,
    testTag: String = "pulse-text-field",
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Search,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** Forms submit on "Done"/"Next" too, and may submit blank values (clearing a field). */
    allowBlankSubmit: Boolean = false,
) {
    val c = Pulse.colors
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep the field visible above the keyboard: once it is focused and the keyboard has finished
    // opening (insets applied), ask the enclosing scroller to bring it into view.
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(focused, imeVisible) {
        if (focused && imeVisible) {
            delay(150)
            requester.bringIntoView()
        }
    }
    Box(modifier.bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }) {
        if (value.isEmpty()) BasicText(placeholder, style = PulseType.body.copy(color = placeholderColor ?: c.textSecondary))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = PulseType.body.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (value.isNotBlank() || allowBlankSubmit) {
                        keyboard?.hide()
                        onSubmit(value)
                    }
                },
                onDone = {
                    keyboard?.hide()
                    if (value.isNotBlank() || allowBlankSubmit) onSubmit(value)
                },
            ),
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}
