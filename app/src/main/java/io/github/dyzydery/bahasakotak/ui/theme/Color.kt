package io.github.dyzydery.bahasakotak.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette: laut (sea teal) as primary, kunyit (turmeric) as secondary, merah (flag red) as a sparing
// tertiary accent, on warm paper. Fixed rather than dynamic so the app looks the same on every phone.

val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E69),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8EDE5),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFFA93B32),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD5),
    onTertiaryContainer = Color(0xFF410001),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1D3),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E8),
    surfaceContainer = Color(0xFFF5ECE2),
    surfaceContainerHigh = Color(0xFFEFE6DC),
    surfaceContainerHighest = Color(0xFFEAE1D6),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5CC),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFA8F1E8),
    secondary = Color(0xFFFFB94E),
    onSecondary = Color(0xFF482A00),
    secondaryContainer = Color(0xFF674000),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFFFB4A9),
    onTertiary = Color(0xFF690002),
    tertiaryContainer = Color(0xFF8C1D16),
    onTertiaryContainer = Color(0xFFFFDAD5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEAE1D6),
    surface = Color(0xFF16130F),
    onSurface = Color(0xFFEAE1D6),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF998F80),
    outlineVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFF110E0A),
    surfaceContainerLow = Color(0xFF1E1B16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2D2924),
    surfaceContainerHighest = Color(0xFF38342E),
)

/** Hero gradient stops (teal, deepening). Readable with white text in both modes. */
val HeroGradientLight = listOf(Color(0xFF0B6E69), Color(0xFF07514E))
val HeroGradientDark = listOf(Color(0xFF0E5A56), Color(0xFF083B39))
