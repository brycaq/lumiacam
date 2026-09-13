package com.nokia1030cam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ViewfinderBlack = Color(0xFF0A0A0A)
val ChromeDark = Color(0xFF1C1C1E)
val LumiaCyan = Color(0xFF37C6D0)
val DialTrack = Color(0xFF3A3A3C)
val TextPrimary = Color(0xFFF2F2F2)
val TextSecondary = Color(0xFF9A9A9E)

private val Nokia1030ColorScheme = darkColorScheme(
    primary = LumiaCyan,
    onPrimary = ViewfinderBlack,
    background = ViewfinderBlack,
    onBackground = TextPrimary,
    surface = ChromeDark,
    onSurface = TextPrimary,
    secondary = TextSecondary
)

@Composable
fun Nokia1030CamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Nokia1030ColorScheme,
        content = content
    )
}
