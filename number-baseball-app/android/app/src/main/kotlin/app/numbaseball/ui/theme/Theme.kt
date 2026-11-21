package app.numbaseball.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightGameColors = darkColorScheme(
    primary = NbColors.chalk,
    onPrimary = NbColors.onChalk,
    primaryContainer = NbColors.surface3,
    onPrimaryContainer = NbColors.chalk,
    secondary = NbColors.ball,
    onSecondary = NbColors.onChalk,
    secondaryContainer = NbColors.surface3,
    onSecondaryContainer = NbColors.chalk,
    tertiary = NbColors.strike,
    onTertiary = NbColors.onChalk,
    background = NbColors.bg,
    onBackground = NbColors.chalk,
    surface = NbColors.bg,
    onSurface = NbColors.chalk,
    surfaceVariant = NbColors.surface2,
    onSurfaceVariant = NbColors.text2,
    surfaceContainerLowest = NbColors.bg,
    surfaceContainerLow = NbColors.surface1,
    surfaceContainer = NbColors.surface2,
    surfaceContainerHigh = NbColors.surface3,
    surfaceContainerHighest = NbColors.surface4,
    outline = NbColors.outline,
    outlineVariant = NbColors.line,
    error = NbColors.out,
    onError = NbColors.onChalk,
    scrim = NbColors.scrim,
)

private val NightGameTypography = Typography(
    displaySmall = NbType.display,
    headlineMedium = NbType.headline,
    titleLarge = NbType.title,
    titleMedium = NbType.subtitle,
    bodyLarge = NbType.body,
    bodyMedium = NbType.body,
    bodySmall = NbType.caption,
    labelLarge = NbType.label,
    labelMedium = NbType.labelSmall,
    labelSmall = NbType.micro,
)

/** Dark-only theme wired to design/tokens.json. Phase 2 builds the custom components on top of [NbColors] / [NbType]. */
@Composable
fun NumBaseballTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightGameColors, typography = NightGameTypography, content = content)
}
