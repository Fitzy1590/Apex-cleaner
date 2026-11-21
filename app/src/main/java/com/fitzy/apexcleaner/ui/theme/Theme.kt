package com.fitzy.apexcleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00C2A8)
val TvViolet = Color(0xFF7C3AED)
val Warn = Color(0xFFFFB020)
val Bad = Color(0xFFFF5A5F)

private val Dark = darkColorScheme(primary = Accent, secondary = Color(0xFF7C9CFF), background = Color(0xFF0E1417), surface = Color(0xFF172126))
private val Light = lightColorScheme(primary = Color(0xFF00796B), secondary = Color(0xFF3F51B5))

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
