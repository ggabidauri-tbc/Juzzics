package com.example.juzzics.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = EmberDark,
    onPrimary = OnEmberDark,
    primaryContainer = EmberContainerDark,
    onPrimaryContainer = OnEmberContainerDark,
    secondary = SageDark,
    onSecondary = OnSageDark,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = OnSageContainerDark,
    tertiary = GoldDark,
    onTertiary = OnGoldDark,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = OnGoldContainerDark,
    background = CharcoalBackground,
    onBackground = OnCharcoal,
    surface = CharcoalBackground,
    onSurface = OnCharcoal,
    surfaceVariant = CharcoalHigh,
    onSurfaceVariant = OnCharcoalVariant,
    surfaceContainerLowest = CharcoalLowest,
    surfaceContainerLow = CharcoalLow,
    surfaceContainer = Charcoal,
    surfaceContainerHigh = CharcoalHigh,
    surfaceContainerHighest = CharcoalHighest,
    surfaceBright = CharcoalHighest,
    surfaceDim = CharcoalBackground,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = OnCharcoal,
    inverseOnSurface = Charcoal,
    inversePrimary = EmberLight,
)

private val LightColors = lightColorScheme(
    primary = EmberLight,
    onPrimary = OnEmberLight,
    primaryContainer = EmberContainerLight,
    onPrimaryContainer = OnEmberContainerLight,
    secondary = SageLight,
    onSecondary = OnSageLight,
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = OnSageContainerLight,
    tertiary = GoldLight,
    onTertiary = OnGoldLight,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = OnGoldContainerLight,
    background = PaperBackground,
    onBackground = OnPaper,
    surface = PaperBackground,
    onSurface = OnPaper,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = OnPaperVariant,
    surfaceContainerLowest = PaperLowest,
    surfaceContainerLow = PaperLow,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    surfaceBright = PaperLowest,
    surfaceDim = PaperHighest,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    inverseSurface = Charcoal,
    inverseOnSurface = OnCharcoal,
    inversePrimary = EmberDark,
)

/** soft, generous corners everywhere */
private val JuzzicsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Juzzics' own look (not the wallpaper colors): recognizable, and the album-art tinted full
 * player stands out on it. Follows the phone's dark / light setting.
 */
@Composable
fun JuzzicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = JuzzicsShapes,
        content = content
    )
}
