package com.critterfarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SproutGreenDeep,
    onPrimary = Color.White,
    primaryContainer = PastureGreenLight,
    onPrimaryContainer = SproutGreenDeep,
    secondary = PondBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = Color.White,
    tertiary = BerryPink,
    onTertiary = Color.White,
    tertiaryContainer = SunshineYellow,
    onTertiaryContainer = InkBrown,
    background = CreamBackground,
    onBackground = InkBrown,
    surface = Color.White,
    onSurface = InkBrown,
    surfaceVariant = DormantGraySurface,
    onSurfaceVariant = DormantGray,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SproutGreenNight,
    onPrimary = SproutGreenDeep,
    primaryContainer = SproutGreenDeep,
    onPrimaryContainer = PastureGreenLight,
    secondary = SkyBlueNight,
    onSecondary = PondBlueDeep,
    secondaryContainer = PondBlueDeep,
    onSecondaryContainer = SkyBlueNight,
    tertiary = BerryPinkNight,
    onTertiary = InkBrown,
    tertiaryContainer = CoinGoldNight,
    onTertiaryContainer = InkBrown,
    background = NightBackground,
    onBackground = PastureGreenLight,
    surface = NightSurface,
    onSurface = PastureGreenLight,
    surfaceVariant = NightSurface,
    onSurfaceVariant = DormantGray,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun CritterFarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CritterFarmTypography,
        shapes = CritterFarmShapes,
        content = content,
    )
}
