package com.embyfusion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FusionGreen = Color(0xFF55E6A5)
val FusionBackground = Color(0xFF090B10)
val FusionSurface = Color(0xFF121620)
val FusionSurfaceHigh = Color(0xFF1A2130)

private val colors = darkColorScheme(
    primary = FusionGreen,
    onPrimary = Color(0xFF002116),
    secondary = Color(0xFF8BB9FF),
    background = FusionBackground,
    onBackground = Color(0xFFE7EAF0),
    surface = FusionSurface,
    surfaceVariant = FusionSurfaceHigh,
    onSurface = Color(0xFFE7EAF0),
    onSurfaceVariant = Color(0xFFABB4C4),
    error = Color(0xFFFFB4AB)
)

@Composable fun FusionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

