package jp.fairguide.jimtof2026.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 配色の方針
 *  - 本文の文字と地の色は WCAG AA（4.5:1 以上）を満たす組み合わせだけを使う
 *  - 「場所」を表す色は、画面をまたいで同じにする（東＝青、西＝緑、南＝橙、会議棟＝紫）
 *    色だけに頼らず、必ず E/W/S の文字や「東1」などの名前を添える
 *  - 強調色（オレンジ）は、ユーザーの操作対象（選択中・通知オン・未訪問）にだけ使う
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C4A80),            // 白地で 8.4:1
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E5F7),
    onPrimaryContainer = Color(0xFF0C2744),
    secondary = Color(0xFFA04A0B),          // 白地で 5.6:1
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF3B1A00),
    tertiary = Color(0xFF2F6A4A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFEBD9),
    onTertiaryContainer = Color(0xFF0B2A19),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF15191F),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF15191F),
    surfaceVariant = Color(0xFFE1E5EB),
    onSurfaceVariant = Color(0xFF3F4650),   // 白地で 9.5:1、薄い地でも 7:1 以上
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF2F6),
    surfaceContainer = Color(0xFFE9EDF2),
    surfaceContainerHigh = Color(0xFFE3E8EE),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFF6C7380),
    outlineVariant = Color(0xFFCBD1D9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7CBF4),
    onPrimary = Color(0xFF07305A),
    primaryContainer = Color(0xFF244B78),
    onPrimaryContainer = Color(0xFFD7E5F7),
    secondary = Color(0xFFFFB783),
    onSecondary = Color(0xFF4E2500),
    secondaryContainer = Color(0xFF6D3706),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFFA9D5BB),
    onTertiary = Color(0xFF123A25),
    tertiaryContainer = Color(0xFF2B5340),
    onTertiaryContainer = Color(0xFFCFEBD9),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF3F4650),
    onSurfaceVariant = Color(0xFFC4CAD3),
    surfaceContainerLowest = Color(0xFF0C0F12),
    surfaceContainerLow = Color(0xFF191D22),
    surfaceContainer = Color(0xFF1E2227),
    surfaceContainerHigh = Color(0xFF282C32),
    surfaceContainerHighest = Color(0xFF33373D),
    outline = Color(0xFF8E949E),
    outlineVariant = Color(0xFF3E444C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 場所（展示棟）ごとの色。container は面、onContainer は文字、strong は線・アイコン */
@Immutable
data class PlaceColor(val container: Color, val onContainer: Color, val strong: Color)

@Immutable
data class GuideColors(
    val east: PlaceColor,
    val west: PlaceColor,
    val south: PlaceColor,
    val conference: PlaceColor,
    val neutral: PlaceColor,
    /** 開催中などの良い状態 */
    val good: PlaceColor,
    /** 注意喚起 */
    val warn: PlaceColor,
    /** カードの地 */
    val card: Color,
    /** カードの線 */
    val cardBorder: Color,
)

private val LightGuideColors = GuideColors(
    east = PlaceColor(Color(0xFFD7E5F7), Color(0xFF0C2744), Color(0xFF2A62A8)),
    west = PlaceColor(Color(0xFFD2ECD9), Color(0xFF0B2A19), Color(0xFF2B7A4B)),
    south = PlaceColor(Color(0xFFFFE0C2), Color(0xFF3B1A00), Color(0xFFBF5E12)),
    conference = PlaceColor(Color(0xFFE7DEF7), Color(0xFF24133F), Color(0xFF6C4BAE)),
    neutral = PlaceColor(Color(0xFFE1E5EB), Color(0xFF2B313A), Color(0xFF6C7380)),
    good = PlaceColor(Color(0xFFD2ECD9), Color(0xFF0B2A19), Color(0xFF1F7A3F)),
    warn = PlaceColor(Color(0xFFFFEBC2), Color(0xFF3A2500), Color(0xFF9A6A00)),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFD6DBE2),
)

private val DarkGuideColors = GuideColors(
    east = PlaceColor(Color(0xFF203F63), Color(0xFFD7E5F7), Color(0xFF8DB8EC)),
    west = PlaceColor(Color(0xFF1F4A31), Color(0xFFCFEBD9), Color(0xFF8CCFA5)),
    south = PlaceColor(Color(0xFF5E3410), Color(0xFFFFDCC4), Color(0xFFF2A86A)),
    conference = PlaceColor(Color(0xFF3F2E66), Color(0xFFE7DEF7), Color(0xFFBCA6EC)),
    neutral = PlaceColor(Color(0xFF3F4650), Color(0xFFE3E6EB), Color(0xFF9AA1AB)),
    good = PlaceColor(Color(0xFF1F4A31), Color(0xFFCFEBD9), Color(0xFF7FCF9B)),
    warn = PlaceColor(Color(0xFF5A4300), Color(0xFFFFEBC2), Color(0xFFF2C14E)),
    card = Color(0xFF1E2227),
    cardBorder = Color(0xFF2E333A),
)

val LocalGuideColors = staticCompositionLocalOf { LightGuideColors }

object GuideTheme {
    val colors: GuideColors
        @Composable @ReadOnlyComposable get() = LocalGuideColors.current
}

/** 日本語の本文が読みやすいように、既定よりやや大きめ・行間広め */
private val GuideTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val GuideShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalGuideColors provides if (dark) DarkGuideColors else LightGuideColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = GuideTypography,
            shapes = GuideShapes,
            content = content,
        )
    }
}
