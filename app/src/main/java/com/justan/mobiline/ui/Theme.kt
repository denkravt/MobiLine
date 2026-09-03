package com.justan.mobiline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

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

/**
 * Standard back arrow pinned to the top-left of a screen. To keep any
 * screen title centered, wrap this in a `Box(fillMaxWidth())` and align it
 * start, with no PaddingValues -- the parent column's vertical padding
 * scopes it correctly.
 */
@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxWidth().then(modifier)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}