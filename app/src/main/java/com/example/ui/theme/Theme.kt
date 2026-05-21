package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    secondary = TerminalGreen,
    tertiary = TerminalDarkGreen,
    background = SolidBlack,
    surface = TerminalBlack,
    onPrimary = SolidBlack,
    onSecondary = SolidBlack,
    onBackground = TerminalGreen,
    onSurface = TerminalGreen
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for terminal style
    dynamicColor: Boolean = false, // Disable dynamic system wallpaper colors
    content: @Composable () -> Unit,
) {
    // We always use the Terminal Green ColorScheme for our retro terminal interface
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
