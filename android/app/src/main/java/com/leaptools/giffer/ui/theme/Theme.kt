package com.leaptools.giffer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GifferYellow = Color(0xFFFFD60A)
val GifferBlue = Color(0xFF0A84FF)

private val DarkColors = darkColorScheme(
    primary = GifferYellow,
    onPrimary = Color.Black,
    secondary = GifferBlue,
    background = Color.Black,
    surface = Color(0xFF1A1A1A),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun GifferTheme(content: @Composable () -> Unit) {
    // The editor is dark by design; use the dark scheme regardless of system setting.
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
