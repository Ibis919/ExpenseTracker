package com.ibis.expense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1EBF8),
    onPrimaryContainer = Color(0xFF182638),
    secondary = Color(0xFF596779),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EEF5),
    onSecondaryContainer = Color(0xFF182638),
    tertiary = Color(0xFF47637B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4EDF5),
    onTertiaryContainer = Color(0xFF173043),
    error = Color(0xFFA74620),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE6DD),
    onErrorContainer = Color(0xFF64280F),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF182638),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF182638),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF596779),
    outline = Color(0xFF6A7787),
    outlineVariant = Color(0xFFCBD4DF),
    inverseSurface = Color(0xFF1D2937),
    inverseOnSurface = Color(0xFFEBF0F7),
    inversePrimary = Color(0xFF93C6FF),
    surfaceTint = Color(0xFF1D5FA8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE1E7EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FBFE),
    surfaceContainer = Color(0xFFF3F6FA),
    surfaceContainerHigh = Color(0xFFEEF2F7),
    surfaceContainerHighest = Color(0xFFE9EEF5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C6FF),
    onPrimary = Color(0xFF0C3056),
    primaryContainer = Color(0xFF26394D),
    onPrimaryContainer = Color(0xFFD8EAFE),
    secondary = Color(0xFFB1BDCD),
    onSecondary = Color(0xFF263342),
    secondaryContainer = Color(0xFF344250),
    onSecondaryContainer = Color(0xFFEBF0F7),
    tertiary = Color(0xFFB4CCDF),
    onTertiary = Color(0xFF15374A),
    tertiaryContainer = Color(0xFF2A4354),
    onTertiaryContainer = Color(0xFFD7EAF7),
    error = Color(0xFFFFB492),
    onError = Color(0xFF5A240C),
    errorContainer = Color(0xFF643823),
    onErrorContainer = Color(0xFFFFDCCB),
    background = Color(0xFF111923),
    onBackground = Color(0xFFEBF0F7),
    surface = Color(0xFF1D2937),
    onSurface = Color(0xFFEBF0F7),
    surfaceVariant = Color(0xFF293646),
    onSurfaceVariant = Color(0xFFB1BDCD),
    outline = Color(0xFF8595A8),
    outlineVariant = Color(0xFF44566A),
    inverseSurface = Color(0xFFEBF0F7),
    inverseOnSurface = Color(0xFF182638),
    inversePrimary = Color(0xFF1D5FA8),
    surfaceTint = Color(0xFF93C6FF),
    surfaceBright = Color(0xFF344250),
    surfaceDim = Color(0xFF111923),
    surfaceContainerLowest = Color(0xFF0C131C),
    surfaceContainerLow = Color(0xFF16212D),
    surfaceContainer = Color(0xFF1D2937),
    surfaceContainerHigh = Color(0xFF293646),
    surfaceContainerHighest = Color(0xFF344250)
)

private val SystemTypography = Typography()
private val LedgerTypography = SystemTypography.copy(
    displayLarge = SystemTypography.displayLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.sp
    ),
    displayMedium = SystemTypography.displayMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.sp
    ),
    displaySmall = SystemTypography.displaySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.sp
    ),
    headlineMedium = SystemTypography.headlineMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        letterSpacing = 0.sp
    )
)

@Composable
fun ExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LedgerTypography,
        content = content
    )
}
