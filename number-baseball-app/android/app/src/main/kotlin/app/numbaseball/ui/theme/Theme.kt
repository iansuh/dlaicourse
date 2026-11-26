package app.numbaseball.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * "Clean White" (DESIGN_SYSTEM v2 16): Material roles mapped onto the tokens. Flat: every container role is `bg` or
 * `surface2`, so a Material default can never add a tinted (tonal) surface.
 */
private val CleanWhiteColors = lightColorScheme(
    primary = NbColors.accent,
    onPrimary = NbColors.onAccent,
    primaryContainer = NbColors.accentTint,
    onPrimaryContainer = NbColors.ink,
    secondary = NbColors.ink,
    onSecondary = NbColors.onInk,
    secondaryContainer = NbColors.surface2,
    onSecondaryContainer = NbColors.ink,
    tertiary = NbColors.strike,
    onTertiary = NbColors.onAccent,
    background = NbColors.bg,
    onBackground = NbColors.ink,
    surface = NbColors.bg,
    onSurface = NbColors.ink,
    surfaceVariant = NbColors.surface2,
    onSurfaceVariant = NbColors.text2,
    surfaceTint = NbColors.bg,
    surfaceBright = NbColors.bg,
    surfaceDim = NbColors.surface2,
    surfaceContainerLowest = NbColors.bg,
    surfaceContainerLow = NbColors.bg,
    surfaceContainer = NbColors.surface2,
    surfaceContainerHigh = NbColors.bg,
    surfaceContainerHighest = NbColors.surface2,
    inverseSurface = NbColors.inverse,
    inverseOnSurface = NbColors.onInverse,
    inversePrimary = NbColors.inverseAccent,
    outline = NbColors.outline,
    outlineVariant = NbColors.line,
    error = NbColors.danger,
    onError = NbColors.onAccent,
    errorContainer = NbColors.dangerTint,
    onErrorContainer = NbColors.danger,
    scrim = NbColors.scrim,
)

private val CleanWhiteTypography = Typography(
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

/** Light-only theme wired to design/tokens.json 2.0.0 (a system-driven dark palette may follow, DESIGN_SYSTEM 1.3). */
@Composable
fun NumBaseballTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CleanWhiteColors, typography = CleanWhiteTypography, content = content)
}
