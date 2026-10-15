package com.jmusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenAccent = Color(0xFF1ED760)
val CyanAccent = Color(0xFF00E5FF)
val PurpleAccent = Color(0xFFBB86FC)

val BackgroundDark = Color(0xFF0D0F14)
val SurfaceDark = Color(0xFF161A22)
val CardBackground = Color(0xFF1F2430)
val TextPrimary = Color(0xFFEEEEEE)
val TextMuted = Color(0xFFA0A5B5)

private val DarkColorScheme = darkColorScheme(
    primary = GreenAccent,
    secondary = CyanAccent,
    tertiary = PurpleAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = CardBackground,
    onPrimary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted
)

@Composable
fun JMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
