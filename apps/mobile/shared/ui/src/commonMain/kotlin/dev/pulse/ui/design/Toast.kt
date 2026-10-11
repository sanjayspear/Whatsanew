package dev.pulse.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Brief confirmation above the intent bar ("Saved to your shelf"). */
@Composable
fun BoxScope.PulseToast(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).keyboardSafe().padding(bottom = 96.dp),
    ) {
        BasicText(
            message.orEmpty(),
            style = PulseType.label.copy(color = Pulse.colors.textPrimary),
            modifier = Modifier.glass(RoundedCornerShape(50), strength = 2f).testTag("toast").padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
