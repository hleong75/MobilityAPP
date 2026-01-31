package com.example.mobilityapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = DeepMarine,
    onPrimary = OnDeepMarine,
    primaryContainer = DeepMarineLight,
    onPrimaryContainer = OnDeepMarine,
    secondary = SlateGrayLight,
    onSecondary = OnSlateDark,
    secondaryContainer = SlateGray,
    onSecondaryContainer = OnSlateDark,
    tertiary = DeepMarineLight,
    onTertiary = OnDeepMarine,
    background = SlateSurface,
    onBackground = OnSlate,
    surface = SlateSurface,
    onSurface = OnSlate,
    surfaceVariant = SlateGrayLight,
    onSurfaceVariant = OnSlateDark,
    outline = SlateGray
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepMarineLight,
    onPrimary = OnDeepMarine,
    primaryContainer = DeepMarine,
    onPrimaryContainer = OnDeepMarine,
    secondary = SlateGrayLight,
    onSecondary = OnSlateDark,
    secondaryContainer = SlateGrayDark,
    onSecondaryContainer = OnSlateDark,
    tertiary = DeepMarineLight,
    onTertiary = OnDeepMarine,
    background = SlateSurfaceDark,
    onBackground = OnSlateDark,
    surface = SlateSurfaceDark,
    onSurface = OnSlateDark,
    surfaceVariant = SlateGrayDark,
    onSurfaceVariant = OnSlateDark,
    outline = SlateGray
)

@Composable
fun MobilityAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
