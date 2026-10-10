package com.fefw.planner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Surface2,
    onPrimaryContainer = TextPrimary,
    secondary = Secondary,
    onSecondary = OnPrimary,
    tertiary = Tertiary,
    onTertiary = OnPrimary,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Bg2,
    surfaceContainer = Surface1,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Surface2,
    surfaceDim = Bg,
    surfaceBright = Surface2,
    error = ErrorRed,
    onError = Color(0xFF2A0E0E),
    errorContainer = DangerSoft,
    onErrorContainer = DangerText,
    outline = Border,
    outlineVariant = Border,
    scrim = Color(0xCC000000)
)

private val FefwShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun FefwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = FefwTypography,
        shapes = FefwShapes,
        content = content
    )
}
