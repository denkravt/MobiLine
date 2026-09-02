package com.justan.mobiline.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — matches the MobiLine logo (#2B9B51).
val MobGreen = Color(0xFF2B9B51)        // primary brand green
val MobGreenDark = Color(0xFF1D6B36)    // darker green for secondary / on-light accents
val MobGreenContainer = Color(0xFFA9E8C0)
val MobGreenOnContainer = Color(0xFF0A3D1D)
val MobRed = Color(0xFFC62828)          // error / out-of-level feedback
val MobGray = Color.Gray                // muted / disabled

private val MobiLineColorScheme = lightColorScheme(
    primary = MobGreen,
    onPrimary = Color.White,
    primaryContainer = MobGreenContainer,
    onPrimaryContainer = MobGreenOnContainer,
    secondary = MobGreenDark,
    onSecondary = Color.White,
    error = MobRed,
    onError = Color.White,
)

@Composable
fun MobiLineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobiLineColorScheme, content = content)
}