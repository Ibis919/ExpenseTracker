package com.ibis.expense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF6C3),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9D9),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDEE5DC),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67DDA2),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF83F8B8),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9D9),
    tertiary = Color(0xFFA2CEDC),
    onTertiary = Color(0xFF05303F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DD),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DD),
    surfaceVariant = Color(0xFF232B24),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C)
)

@Composable
fun ExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
