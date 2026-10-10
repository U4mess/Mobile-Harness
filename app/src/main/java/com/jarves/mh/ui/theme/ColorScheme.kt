package com.jarves.mh.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * PocketForge Midnight Studio Material colour schemes.
 *
 * Every Material 3 role is set explicitly in both schemes. Nothing is left to
 * the Material baseline defaults, so an undefined role can never leak a stray
 * purple/grey into the UI — `PocketColorsBaselineLeakageTest` enforces this.
 */

internal val PocketForgeDarkScheme = darkColorScheme(
    primary = BlueDarkPrimary,
    onPrimary = BlueDarkOnPrimary,
    primaryContainer = BlueDarkPrimaryContainer,
    onPrimaryContainer = BlueDarkOnPrimaryContainer,
    inversePrimary = BlueDarkInversePrimary,

    secondary = CyanDark,
    onSecondary = CyanDarkOn,
    secondaryContainer = CyanDarkContainer,
    onSecondaryContainer = CyanDarkOnContainer,

    tertiary = GreenDark,
    onTertiary = GreenDarkOn,
    tertiaryContainer = GreenDarkContainer,
    onTertiaryContainer = GreenDarkOnContainer,

    background = NavyBackgroundDark,
    onBackground = NavyOnDark,
    surface = NavySurfaceDark,
    onSurface = NavyOnDark,
    surfaceVariant = NavySurfaceVariantDark,
    onSurfaceVariant = NavyOnVariantDark,
    surfaceTint = BlueDarkPrimary,

    inverseSurface = NavyOnDark,
    inverseOnSurface = NavyContainerLowDark,

    surfaceBright = NavyBrightDark,
    surfaceDim = NavyDimDark,
    surfaceContainerLowest = NavyContainerLowestDark,
    surfaceContainerLow = NavyContainerLowDark,
    surfaceContainer = NavySurfaceDark,
    surfaceContainerHigh = NavyContainerHighDark,
    surfaceContainerHighest = NavyContainerHighestDark,

    outline = NavyOutlineDark,
    outlineVariant = NavyOutlineVariantDark,

    error = ErrorDark,
    onError = ErrorDarkOn,
    errorContainer = ErrorDarkContainer,
    onErrorContainer = ErrorDarkOnContainer,

    scrim = ScrimBlack,
)

internal val PocketForgeLightScheme = lightColorScheme(
    primary = BlueLightPrimary,
    onPrimary = BlueLightOnPrimary,
    primaryContainer = BlueLightPrimaryContainer,
    onPrimaryContainer = BlueLightOnPrimaryContainer,
    inversePrimary = BlueLightInversePrimary,

    secondary = CyanLight,
    onSecondary = CyanLightOn,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = CyanLightOnContainer,

    tertiary = GreenLight,
    onTertiary = GreenLightOn,
    tertiaryContainer = GreenLightContainer,
    onTertiaryContainer = GreenLightOnContainer,

    background = SlateBackgroundLight,
    onBackground = SlateOnLight,
    surface = SlateSurfaceLight,
    onSurface = SlateOnLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateOnVariantLight,
    surfaceTint = BlueLightPrimary,

    inverseSurface = SlateOnLight,
    inverseOnSurface = SlateBackgroundLight,

    surfaceBright = SlateBrightLight,
    surfaceDim = SlateDimLight,
    surfaceContainerLowest = SlateContainerLowestLight,
    surfaceContainerLow = SlateContainerLowLight,
    surfaceContainer = SlateContainerLight,
    surfaceContainerHigh = SlateContainerHighLight,
    surfaceContainerHighest = SlateContainerHighestLight,

    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineVariantLight,

    error = ErrorLight,
    onError = ErrorLightOn,
    errorContainer = ErrorLightContainer,
    onErrorContainer = ErrorLightOnContainer,

    scrim = ScrimBlack,
)
