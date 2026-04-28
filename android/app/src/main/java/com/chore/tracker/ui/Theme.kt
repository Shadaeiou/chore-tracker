package com.chore.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pre-defined color schemes the user can switch between in Settings.
 * Each palette has a tuned light/dark scheme — accents adjust together. */
enum class ThemePalette(val displayName: String, val swatch: Color) {
    GREEN("Green", Color(0xFF386A20)),
    OCEAN("Ocean", Color(0xFF0061A4)),
    PLUM("Plum", Color(0xFF6750A4)),
    AMBER("Amber", Color(0xFFB1530A)),
    ROSE("Rose", Color(0xFFB3265E)),
    SLATE("Slate", Color(0xFF3F4756)),
}

private val GreenLight = lightColorScheme(
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
private val GreenDark = darkColorScheme(
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

private val OceanLight = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFFFDFCFF),
    surface = Color(0xFFFDFCFF),
    surfaceVariant = Color(0xFFDFE2EB),
    error = Color(0xFFBA1A1A),
)
private val OceanDark = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFF42474E),
    error = Color(0xFFFFB4AB),
)

private val PlumLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    error = Color(0xFFBA1A1A),
)
private val PlumDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
)

private val AmberLight = lightColorScheme(
    primary = Color(0xFF8B4F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF735944),
    secondaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF1DFD0),
    error = Color(0xFFBA1A1A),
)
private val AmberDark = darkColorScheme(
    primary = Color(0xFFFFB779),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3B00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C0A2),
    secondaryContainer = Color(0xFF59422E),
    background = Color(0xFF201A17),
    surface = Color(0xFF201A17),
    surfaceVariant = Color(0xFF52443A),
    error = Color(0xFFFFB4AB),
)

private val RoseLight = lightColorScheme(
    primary = Color(0xFFB3265E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F001B),
    secondary = Color(0xFF74565F),
    secondaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF2DDE1),
    error = Color(0xFFBA1A1A),
)
private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF66002F),
    primaryContainer = Color(0xFF8E1147),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    secondaryContainer = Color(0xFF5A3F47),
    background = Color(0xFF201A1B),
    surface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFF524346),
    error = Color(0xFFFFB4AB),
)

private val SlateLight = lightColorScheme(
    primary = Color(0xFF3F4756),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7DFEE),
    onPrimaryContainer = Color(0xFF000A12),
    secondary = Color(0xFF555F71),
    secondaryContainer = Color(0xFFD9E3F9),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE0E1EC),
    error = Color(0xFFBA1A1A),
)
private val SlateDark = darkColorScheme(
    primary = Color(0xFFB6C5DB),
    onPrimary = Color(0xFF1F2D3F),
    primaryContainer = Color(0xFF334155),
    onPrimaryContainer = Color(0xFFD7DFEE),
    secondary = Color(0xFFBDC7DC),
    secondaryContainer = Color(0xFF3C4858),
    background = Color(0xFF1A1B1F),
    surface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFF44464F),
    error = Color(0xFFFFB4AB),
)

private fun palette(p: ThemePalette, dark: Boolean): ColorScheme = when (p) {
    ThemePalette.GREEN -> if (dark) GreenDark else GreenLight
    ThemePalette.OCEAN -> if (dark) OceanDark else OceanLight
    ThemePalette.PLUM -> if (dark) PlumDark else PlumLight
    ThemePalette.AMBER -> if (dark) AmberDark else AmberLight
    ThemePalette.ROSE -> if (dark) RoseDark else RoseLight
    ThemePalette.SLATE -> if (dark) SlateDark else SlateLight
}

@Composable
fun ChoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themePalette: ThemePalette = ThemePalette.GREEN,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = palette(themePalette, useDark),
        content = content,
    )
}
