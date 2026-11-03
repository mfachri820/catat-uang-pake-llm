package com.catatuang.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBDD),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4F6358),
    secondaryContainer = Color(0xFFE3ECE6),
    tertiary = Color(0xFFB4541A),
    tertiaryContainer = Color(0xFFFFE2D0),
    background = Color(0xFFF7F8F6),
    surface = Color(0xFFF7F8F6),
    surfaceContainer = Color(0xFFEDF0EC),
    surfaceContainerHigh = Color(0xFFE6EAE5),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD6B4),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0B5A41),
    onPrimaryContainer = Color(0xFFC8EBDD),
    secondary = Color(0xFFB4CCBE),
    secondaryContainer = Color(0xFF34473D),
    tertiary = Color(0xFFFFB68C),
    tertiaryContainer = Color(0xFF6B2E05),
    background = Color(0xFF111412),
    surface = Color(0xFF111412),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
)

@Composable
fun CatatUangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
