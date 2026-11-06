package com.wowforever.addonmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CurseForge-inspired colors
val CurseOrange = Color(0xFFFF6B00)
val CurseDark = Color(0xFF1A1A1A)
val CurseDarker = Color(0xFF121212)
val CurseCard = Color(0xFF2A2A2A)
val CurseText = Color(0xFFE0E0E0)
val CurseMuted = Color(0xFFAAAAAA)

private val CurseColorScheme = darkColorScheme(
    primary = CurseOrange,
    onPrimary = Color.White,
    secondary = CurseOrange,
    background = CurseDarker,
    surface = CurseDark,
    onBackground = CurseText,
    onSurface = CurseText,
    surfaceVariant = CurseCard,
    onSurfaceVariant = CurseMuted
)

@Composable
fun CurseForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CurseColorScheme,
        content = content
    )
}
