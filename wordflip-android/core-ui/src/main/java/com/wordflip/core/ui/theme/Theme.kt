package com.wordflip.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageOnPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    secondary = SageSecondary,
    background = SageBackground,
    surface = SageSurface,
    surfaceVariant = SageSurfaceVariant,
    onBackground = SageOnBackground,
    onSurface = SageOnSurface,
    onSurfaceVariant = SageOnSurfaceVariant,
    error = SageError,
)

private val DarkColorScheme = darkColorScheme(
    primary = SageDarkPrimary,
    onPrimary = SageDarkOnPrimary,
    primaryContainer = SageDarkPrimaryContainer,
    onPrimaryContainer = SageDarkOnPrimaryContainer,
    secondary = SageDarkSecondary,
    background = SageDarkBackground,
    surface = SageDarkSurface,
    surfaceVariant = SageDarkSurfaceVariant,
    onBackground = SageDarkOnBackground,
    onSurface = SageDarkOnSurface,
    onSurfaceVariant = SageDarkOnSurfaceVariant,
    error = SageDarkError,
)

@Composable
fun WordFlipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
