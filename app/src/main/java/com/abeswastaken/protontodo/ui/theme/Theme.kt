package com.abeswastaken.protontodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette matches the approved mockup (Proton-style purple on soft neutrals).
private val Light = lightColorScheme(
    primary = Color(0xFF6D4AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEEAFF),
    onPrimaryContainer = Color(0xFF6D4AFF),
    background = Color(0xFFF4F3F8),
    onBackground = Color(0xFF0C0C14),
    surface = Color.White,
    onSurface = Color(0xFF0C0C14),
    onSurfaceVariant = Color(0xFF6A6780),
    outlineVariant = Color(0xFFE3E1EC),
    error = Color(0xFFD92A4B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8A6EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D2652),
    onPrimaryContainer = Color(0xFFB9A8FF),
    background = Color(0xFF16141C),
    onBackground = Color(0xFFEEEDF5),
    surface = Color(0xFF211F2A),
    onSurface = Color(0xFFEEEDF5),
    onSurfaceVariant = Color(0xFFA4A1B6),
    outlineVariant = Color(0xFF34313F),
    error = Color(0xFFF0567A),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ProtonTodoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
