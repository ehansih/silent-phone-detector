package com.ehansih.silentphonedetector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0A2E10),
    secondary = Color(0xFF004D40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00352C),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E3DC),
    onSurfaceVariant = Color(0xFF47433F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD9A0),
    onPrimary = Color(0xFF0C2E12),
    primaryContainer = Color(0xFF1E4B25),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF7ACEC2),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF004E48),
    onSecondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE7E3DC),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE7E3DC),
    surfaceVariant = Color(0xFF47433F),
    onSurfaceVariant = Color(0xFFCAC5C0)
)

@Composable
fun SilentPhoneDetectorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
