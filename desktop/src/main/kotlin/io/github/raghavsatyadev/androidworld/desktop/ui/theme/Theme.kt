package io.github.raghavsatyadev.androidworld.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Android green seed, tuned for M3E's tonal contrast.
private val Light = lightColorScheme(
    primary = Color(0xFF1B6D2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F5A8),
    onPrimaryContainer = Color(0xFF002109),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF38656A),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    surface = Color(0xFFF7FBF2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBEFE6),
    surfaceContainerHigh = Color(0xFFE6E9E0),
    surfaceContainerHighest = Color(0xFFE0E4DB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF89D88E),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF005320),
    onPrimaryContainer = Color(0xFFA4F5A8),
    secondary = Color(0xFFB9CCB4),
    secondaryContainer = Color(0xFF3B4B39),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    surface = Color(0xFF10140F),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF272B25),
    surfaceContainerHighest = Color(0xFF323630),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

@Composable
fun AndroidWorldTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark ?: isSystemInDarkTheme()) Dark else Light,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
