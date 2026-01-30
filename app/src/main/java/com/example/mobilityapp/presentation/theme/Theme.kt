package com.example.mobilityapp.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = DeepMarineBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = DeepMarineBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = LightSlateGray,
    onBackground = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepMarineBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = DeepMarineBlue,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = SlateGray,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = SurfaceDark,
    onSurface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun MobilityTheme(content: @Composable () -> Unit) {
    val colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
