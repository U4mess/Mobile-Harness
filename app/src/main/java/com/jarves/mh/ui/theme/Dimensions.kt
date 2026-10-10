package com.jarves.mh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vertical and horizontal rhythm. Use these instead of bare `.dp` spacing. */
@Immutable
data class PocketSpacing(
    val space4: Dp,
    val space8: Dp,
    val space12: Dp,
    val space16: Dp,
    val space20: Dp,
    val space24: Dp,
    val space32: Dp,
)

/** Corner radii as raw values, for call sites that take a `Dp` rather than a Shape. */
@Immutable
data class PocketRadii(
    val radius8: Dp,
    val radius12: Dp,
    val radius16: Dp,
    val radius20: Dp,
)

/**
 * Control metrics.
 *
 * [compactControlHeight] is a **visual** height only: a 40dp control must still be
 * placed inside at least [minimumTouchTarget] (48dp) of interactive area, e.g. with
 * `Modifier.height(compactControlHeight)` inside a 48dp clickable row or by adding
 * vertical padding to the touch target. Never shrink the touch area to 40dp.
 */
@Immutable
data class PocketSizes(
    val minimumTouchTarget: Dp,
    val compactControlHeight: Dp,
    val standardControlHeight: Dp,
    val comfortableControlHeight: Dp,
)

val PocketSpacingTokens = PocketSpacing(
    space4 = 4.dp,
    space8 = 8.dp,
    space12 = 12.dp,
    space16 = 16.dp,
    space20 = 20.dp,
    space24 = 24.dp,
    space32 = 32.dp,
)

val PocketRadiusTokens = PocketRadii(
    radius8 = 8.dp,
    radius12 = 12.dp,
    radius16 = 16.dp,
    radius20 = 20.dp,
)

val PocketSizeTokens = PocketSizes(
    minimumTouchTarget = 48.dp,
    compactControlHeight = 40.dp,
    standardControlHeight = 48.dp,
    comfortableControlHeight = 56.dp,
)

internal val LocalPocketSpacing = staticCompositionLocalOf { PocketSpacingTokens }
internal val LocalPocketRadii = staticCompositionLocalOf { PocketRadiusTokens }
internal val LocalPocketSizes = staticCompositionLocalOf { PocketSizeTokens }
