package com.garetto.jukebox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** The app's colour palette: midnight purple with neon violet and pink. */
object Jx {
    val Bg = Color(0xFF07040D)
    val Card = Color(0xFF150C24)
    val CardHi = Color(0xFF1F1233)
    val Violet = Color(0xFF8B3DFF)
    val Pink = Color(0xFFFF2E93)
    val Blue = Color(0xFF3A1CFF)
    val Cyan = Color(0xFF38E8FF)
    val Ink = Color(0xFFF4ECFF)
    val Muted = Color(0xFF9D8FBA)

    val Neon: Brush get() = Brush.horizontalGradient(listOf(Violet, Pink))
    val NeonDiagonal: Brush get() = Brush.linearGradient(listOf(Violet, Pink))
    val Mono = FontFamily.Monospace
}

@Composable
fun JukeboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Jx.Violet,
            secondary = Jx.Pink,
            tertiary = Jx.Cyan,
            background = Jx.Bg,
            surface = Jx.Card,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Jx.Ink,
            onSurface = Jx.Ink,
        ),
        content = content,
    )
}
