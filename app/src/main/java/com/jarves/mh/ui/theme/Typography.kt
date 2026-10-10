package com.jarves.mh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PocketForge type scale.
 *
 * System fonts only — `FontFamily.Default` (sans-serif) and [FontFamily.Monospace]
 * for code. Sizes are `sp` so user font scaling keeps working; no downloadable
 * fonts and no fixed pixel sizes.
 *
 * [PocketTypography.material] is what goes into `MaterialTheme.typography`, so
 * existing `MaterialTheme.typography.*` call sites pick the new scale up. The
 * extra named roles are brand-specific and are exposed through
 * [PocketTheme.typography] instead.
 */
val PocketTypography = PocketTypeScale(
    welcomeHeadline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    screenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    rowTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    supporting = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    buttonLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    navigationLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    metadata = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

/**
 * Named brand roles plus the Material [Typography] assembled from them.
 *
 * `material` is derived, never hand-maintained, so a size change here cannot
 * drift away from what components render.
 */
data class PocketTypeScale(
    val welcomeHeadline: TextStyle,
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val supporting: TextStyle,
    val buttonLabel: TextStyle,
    val navigationLabel: TextStyle,
    val metadata: TextStyle,
    val code: TextStyle,
) {
    val material: Typography = Typography(
        displaySmall = welcomeHeadline,
        headlineLarge = welcomeHeadline,
        headlineMedium = screenTitle,
        headlineSmall = screenTitle,
        titleLarge = sectionTitle,
        titleMedium = sectionTitle,
        titleSmall = rowTitle,
        bodyLarge = body,
        bodyMedium = supporting,
        bodySmall = metadata,
        labelLarge = buttonLabel,
        labelMedium = navigationLabel,
        labelSmall = metadata,
    )
}
