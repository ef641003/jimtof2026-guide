package jp.fairguide.jimtof2026.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 紺（スチールブルー）とオレンジを基調にした、主催者のデザインとは別のオリジナル配色
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3F5),
    onPrimaryContainer = Color(0xFF0B2742),
    secondary = Color(0xFFA85612),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDBF),
    onSecondaryContainer = Color(0xFF3A1B00),
    tertiary = Color(0xFF3D6650),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEBD7),
    onTertiaryContainer = Color(0xFF0D2618),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF181C21),
    surface = Color(0xFFF6F8FB),
    onSurface = Color(0xFF181C21),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF424852),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F7),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E9EF),
    surfaceContainerHighest = Color(0xFFDEE4EB),
    outline = Color(0xFF727883),
    outlineVariant = Color(0xFFC9CED6),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3C9F2),
    onPrimary = Color(0xFF00315A),
    primaryContainer = Color(0xFF1F4E79),
    onPrimaryContainer = Color(0xFFD4E3F5),
    secondary = Color(0xFFFFB878),
    onSecondary = Color(0xFF4F2600),
    secondaryContainer = Color(0xFF713A00),
    onSecondaryContainer = Color(0xFFFFDDBF),
    tertiary = Color(0xFFB1D0BC),
    onTertiary = Color(0xFF1D3828),
    tertiaryContainer = Color(0xFF254E3A),
    onTertiaryContainer = Color(0xFFCDEBD7),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E4E9),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E4E9),
    surfaceVariant = Color(0xFF424852),
    onSurfaceVariant = Color(0xFFC2C7D0),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2025),
    surfaceContainerHigh = Color(0xFF262A30),
    surfaceContainerHighest = Color(0xFF31353B),
    outline = Color(0xFF8C919B),
    outlineVariant = Color(0xFF3A4048),
    error = Color(0xFFF2B8B5),
)

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
