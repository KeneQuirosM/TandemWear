package app.tandemwear.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * TandemWear palette (docs/TandemWear-mockups.pdf). Dark only: AMOLED watches, black
 * background saves power. Text/background pairs used together keep ≥ 4.5:1 contrast:
 * textPrimary/textSecondary/textMuted on background or surface, onPrimary on primary,
 * white on decline.
 */
object TandemColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF1F2426)
    val surfaceButton = Color(0xFF2C3134)

    val textPrimary = Color(0xFFE3E2E6)
    val textSecondary = Color(0xFFC4C7CA)
    val textMuted = Color(0xFFA9ADB1)

    val primary = Color(0xFF8CD3E8)
    val onPrimary = Color(0xFF00363F)
    val primaryContainer = Color(0xFF0F4A57)
    val onPrimaryContainer = Color(0xFFBEEBF7)

    val success = Color(0xFF7EE2A8)
    val successContainer = Color(0xFF1E5E3A)
    val warning = Color(0xFFF6C98A)

    val decline = Color(0xFFB3261E)
    val onDecline = Color(0xFFFFFFFF)
    val accept = Color(0xFF7EE2A8)
    val onAccept = Color(0xFF00391C)

    /** Connection-state accents (brief v2): teal = connected, amber = reconnecting, soft red = action needed. */
    val stateConnected = primary
    val stateReconnecting = warning
    val stateAttention = Color(0xFFF2B8B5)
    val stateIdle = Color(0xFF8A9094)

    val error = Color(0xFFF2B8B5)
    val onError = Color(0xFF601410)
}

/**
 * Text sizes in sp, so they follow the system font size setting. Important text (status,
 * sender) uses [title]/[headline]; nothing user-facing goes below [caption].
 */
object TandemType {
    val caption = 12.sp
    val label = 14.sp
    val body = 15.sp
    val title = 16.sp
    val headline = 20.sp
    val callerName = 18.sp
}

private val TandemColorScheme = ColorScheme(
    primary = TandemColors.primary,
    onPrimary = TandemColors.onPrimary,
    primaryContainer = TandemColors.primaryContainer,
    onPrimaryContainer = TandemColors.onPrimaryContainer,
    background = TandemColors.background,
    onBackground = TandemColors.textPrimary,
    onSurface = TandemColors.textPrimary,
    onSurfaceVariant = TandemColors.textSecondary,
    error = TandemColors.error,
    onError = TandemColors.onError
)

@Composable
fun TandemWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TandemColorScheme, content = content)
}
