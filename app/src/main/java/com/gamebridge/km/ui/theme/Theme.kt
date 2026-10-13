package com.gamebridge.km.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFF59E0B)
private val AmberDeep = Color(0xFFB45309)
private val Orange = Color(0xFFFB923C)
private val Mint = Color(0xFF34D399)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1206),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Color(0xFFFFF7ED),
    secondary = Orange,
    onSecondary = Color(0xFF1A1206),
    tertiary = Mint,
    onTertiary = Color(0xFF052E1C),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF1F1F27),
    onSurfaceVariant = Color(0xFFA1A1AA),
    error = Color(0xFFEF4444),
    onError = Color(0xFF450A0A),
    outline = Color(0xFF3F3F46)
)

@Composable
fun GameBridgeTheme(content: @Composable () -> Unit) {
    // Always dark — it's a gaming tool.
    MaterialTheme(colorScheme = DarkColors, content = content)
}
