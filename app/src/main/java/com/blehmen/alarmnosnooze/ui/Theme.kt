package com.blehmen.alarmnosnooze.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val AccentPresets: List<Int> = listOf(
    0xFF00BCD4, 0xFF8C6BFF, 0xFF4CAF50, 0xFFFF9800, 0xFFFF4081, 0xFFF44336
).map { it.toInt() }

val AccentNames = listOf("Cyan", "Purple", "Green", "Orange", "Pink", "Red")

private fun onColor(c: Color) = if (c.luminance() > 0.5f) Color.Black else Color.White
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

fun buildScheme(accent: Color, dark: Boolean): ColorScheme {
    val on = onColor(accent)
    return if (dark) darkColorScheme(
        primary = accent, onPrimary = on,
        primaryContainer = lerp(Color(0xFF1A1A1A), accent, 0.35f), onPrimaryContainer = Color.White,
        secondary = accent, onSecondary = on,
        background = Color.Black, onBackground = Color.White,
        surface = Color(0xFF1A1A1A), onSurface = Color.White,
        surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFF9E9E9E),
        surfaceContainer = Color(0xFF1A1A1A), surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF2E2E2E),
        outline = Color(0xFF5A5A5A), outlineVariant = Color(0xFF3A3A3A),
    ) else lightColorScheme(
        primary = accent, onPrimary = on,
        primaryContainer = lerp(Color.White, accent, 0.25f), onPrimaryContainer = Color.Black,
        secondary = accent, onSecondary = on,
        background = Color(0xFFF2F2F7), onBackground = Color.Black,
        surface = Color.White, onSurface = Color.Black,
        surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6B6B70),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEDEDF2),
        surfaceContainerHighest = Color(0xFFE5E5EA),
        outline = Color(0xFFA0A0A5), outlineVariant = Color(0xFFD0D0D5),
    )
}

@Composable
fun AppTheme(mode: Int, accent: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = buildScheme(Color(accent), dark), typography = Typography(), content = content)
}
