package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class JarvisExtendedColors(
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val subtleBorder: Color,
    val userBubbleContainer: Color,
    val userBubbleText: Color,
    val assistantCardContainer: Color,
    val accentCyan: Color,
    val accentAmber: Color,
    val isDark: Boolean
)

val LocalJarvisColors = staticCompositionLocalOf {
    JarvisExtendedColors(
        codeBackground = Color(0xFF0D1320),
        codeHeaderBackground = Color(0xFF162033),
        subtleBorder = ObsidianOutline,
        userBubbleContainer = CyanContainerDark,
        userBubbleText = TextPrimaryDark,
        assistantCardContainer = ObsidianCard,
        accentCyan = CyanPulse,
        accentAmber = AmberCore,
        isDark = true
    )
}

private val JarvisDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF04212B),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = AmberCore,
    onSecondary = Color(0xFF2B1800),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = Color.White,
    primaryContainer = SkyContainerLight,
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF78350F),
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = AlabasterSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun JarvisTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) JarvisDarkColorScheme else JarvisLightColorScheme
    val extendedColors = if (useDark) {
        JarvisExtendedColors(
            codeBackground = Color(0xFF0B111D),
            codeHeaderBackground = Color(0xFF162135),
            subtleBorder = ObsidianOutline,
            userBubbleContainer = Color(0xFF0E3446),
            userBubbleText = TextPrimaryDark,
            assistantCardContainer = ObsidianCard,
            accentCyan = CyanPulse,
            accentAmber = AmberCore,
            isDark = true
        )
    } else {
        JarvisExtendedColors(
            codeBackground = Color(0xFF0F172A),
            codeHeaderBackground = Color(0xFF1E293B),
            subtleBorder = AlabasterOutline,
            userBubbleContainer = Color(0xFFDDF2FD),
            userBubbleText = TextPrimaryLight,
            assistantCardContainer = Color(0xFFFFFFFF),
            accentCyan = SkyPrimary,
            accentAmber = AmberLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalJarvisColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JarvisTypography,
            content = content
        )
    }
}
