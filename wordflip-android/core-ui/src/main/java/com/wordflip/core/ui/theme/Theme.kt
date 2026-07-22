package com.wordflip.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = AppleLightAccent,
    onPrimary = AppleLightGroupedSurface,
    primaryContainer = AppleLightAccentContainer,
    onPrimaryContainer = AppleLightOnAccentContainer,
    inversePrimary = AppleDarkAccent,
    secondary = AppleLightSecondaryText,
    onSecondary = AppleLightGroupedSurface,
    secondaryContainer = AppleLightSurfaceVariant,
    onSecondaryContainer = AppleLightPrimaryText,
    tertiary = AppleLightAccent,
    onTertiary = AppleLightGroupedSurface,
    tertiaryContainer = AppleLightAccentContainer,
    onTertiaryContainer = AppleLightOnAccentContainer,
    background = AppleLightCanvas,
    surface = AppleLightGroupedSurface,
    surfaceVariant = AppleLightSurfaceVariant,
    surfaceTint = AppleLightAccent,
    inverseSurface = AppleDarkGroupedSurface,
    inverseOnSurface = AppleDarkPrimaryText,
    onBackground = AppleLightPrimaryText,
    onSurface = AppleLightPrimaryText,
    onSurfaceVariant = AppleLightSecondaryText,
    outline = AppleLightSeparator,
    outlineVariant = AppleLightSeparator,
    scrim = AppleDarkCanvas,
    surfaceBright = AppleLightGroupedSurface,
    surfaceDim = AppleLightSurfaceVariant,
    surfaceContainerLowest = AppleLightGroupedSurface,
    surfaceContainerLow = AppleLightGroupedSurface,
    surfaceContainer = AppleLightElevatedSurface,
    surfaceContainerHigh = AppleLightSurfaceVariant,
    surfaceContainerHighest = AppleLightSurfaceVariant,
    error = AppleLightDestructive,
    onError = AppleLightGroupedSurface,
    errorContainer = AppleLightDestructiveContainer,
    onErrorContainer = AppleLightOnDestructiveContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppleDarkAccent,
    onPrimary = AppleDarkCanvas,
    primaryContainer = AppleDarkAccentContainer,
    onPrimaryContainer = AppleDarkOnAccentContainer,
    inversePrimary = AppleLightAccent,
    secondary = AppleDarkSecondaryText,
    onSecondary = AppleDarkCanvas,
    secondaryContainer = AppleDarkSurfaceVariant,
    onSecondaryContainer = AppleDarkPrimaryText,
    tertiary = AppleDarkAccent,
    onTertiary = AppleDarkCanvas,
    tertiaryContainer = AppleDarkAccentContainer,
    onTertiaryContainer = AppleDarkOnAccentContainer,
    background = AppleDarkCanvas,
    surface = AppleDarkGroupedSurface,
    surfaceVariant = AppleDarkSurfaceVariant,
    surfaceTint = AppleDarkAccent,
    inverseSurface = AppleLightGroupedSurface,
    inverseOnSurface = AppleLightPrimaryText,
    onBackground = AppleDarkPrimaryText,
    onSurface = AppleDarkPrimaryText,
    onSurfaceVariant = AppleDarkSecondaryText,
    outline = AppleDarkSeparator,
    outlineVariant = AppleDarkSeparator,
    scrim = AppleDarkCanvas,
    surfaceBright = AppleDarkSurfaceVariant,
    surfaceDim = AppleDarkCanvas,
    surfaceContainerLowest = AppleDarkCanvas,
    surfaceContainerLow = AppleDarkGroupedSurface,
    surfaceContainer = AppleDarkGroupedSurface,
    surfaceContainerHigh = AppleDarkElevatedSurface,
    surfaceContainerHighest = AppleDarkSurfaceVariant,
    error = AppleDarkDestructive,
    onError = AppleDarkCanvas,
    errorContainer = AppleDarkDestructiveContainer,
    onErrorContainer = AppleDarkOnDestructiveContainer,
)

@Composable
fun WordFlipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalWordFlipExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WordFlipTypography,
            content = content,
        )
    }
}
