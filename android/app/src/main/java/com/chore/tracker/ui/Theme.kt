package com.chore.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F397),
    onPrimaryContainer = Color(0xFF052100),
    secondary = Color(0xFF55624C),
    secondaryContainer = Color(0xFFD9E7CB),
    background = Color(0xFFFCFDF6),
    surface = Color(0xFFFCFDF6),
    surfaceVariant = Color(0xFFDFE4D7),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD67D),
    onPrimary = Color(0xFF0E3900),
    primaryContainer = Color(0xFF205107),
    onPrimaryContainer = Color(0xFFB7F397),
    secondary = Color(0xFFBDCBB0),
    secondaryContainer = Color(0xFF3D4A35),
    background = Color(0xFF1A1C18),
    surface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFF43483F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ChoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
