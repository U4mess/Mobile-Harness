package com.jarves.mh.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast plus the typography and dimension scales.
 *
 * Every audited text/container pair is asserted here, so lowering a contrast
 * ratio requires deliberately changing a test rather than drifting unnoticed.
 */
class PocketColorsContrastTest {

    // WCAG 2.1 relative luminance + contrast ratio.
    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(what: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$what contrast was %.2f but must be >= %.1f".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    /** Ordinary text needs 4.5:1; large text and non-text boundaries 3:1. */
    private fun assertTextPairs(scheme: ColorScheme, colors: PocketColors, name: String) {
        val t = 4.5
        assertContrast("$name onBackground/background", scheme.onBackground, scheme.background, t)
        assertContrast("$name onSurface/surface", scheme.onSurface, scheme.surface, t)
        assertContrast("$name onSurface/surfaceContainerLowest", scheme.onSurface, scheme.surfaceContainerLowest, t)
        assertContrast("$name onSurface/surfaceContainerLow", scheme.onSurface, scheme.surfaceContainerLow, t)
        assertContrast("$name onSurface/surfaceContainer", scheme.onSurface, scheme.surfaceContainer, t)
        assertContrast("$name onSurface/surfaceContainerHigh", scheme.onSurface, scheme.surfaceContainerHigh, t)
        assertContrast("$name onSurface/surfaceContainerHighest", scheme.onSurface, scheme.surfaceContainerHighest, t)
        assertContrast("$name onSurface/surfaceDim", scheme.onSurface, scheme.surfaceDim, t)
        assertContrast("$name onSurface/surfaceBright", scheme.onSurface, scheme.surfaceBright, t)

        assertContrast("$name onSurfaceVariant/surface", scheme.onSurfaceVariant, scheme.surface, t)
        assertContrast("$name onSurfaceVariant/surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, t)
        assertContrast("$name onSurfaceVariant/background", scheme.onSurfaceVariant, scheme.background, t)

        assertContrast("$name onPrimary/primary", scheme.onPrimary, scheme.primary, t)
        assertContrast("$name onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, t)
        assertContrast("$name primary/surface", scheme.primary, scheme.surface, t)
        assertContrast("$name primary/background", scheme.primary, scheme.background, t)

        assertContrast("$name onSecondary/secondary", scheme.onSecondary, scheme.secondary, t)
        assertContrast("$name onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, t)
        assertContrast("$name secondary/surface", scheme.secondary, scheme.surface, t)

        assertContrast("$name onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, t)
        assertContrast("$name onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, t)
        assertContrast("$name tertiary/surface", scheme.tertiary, scheme.surface, t)

        assertContrast("$name onError/error", scheme.onError, scheme.error, t)
        assertContrast("$name onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer, t)
        assertContrast("$name error/surface", scheme.error, scheme.surface, t)
        assertContrast("$name error/background", scheme.error, scheme.background, t)

        // inversePrimary is only valid on inverseSurface.
        assertContrast("$name inversePrimary/inverseSurface", scheme.inversePrimary, scheme.inverseSurface, t)
        assertContrast("$name inverseOnSurface/inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, t)

        assertContrast("$name accentCyan/surface", colors.accentCyan, scheme.surface, t)
        assertContrast("$name success/surface", colors.success, scheme.surface, t)
        assertContrast("$name success/background", colors.success, scheme.background, t)
        assertContrast("$name onSuccessContainer/successContainer", colors.onSuccessContainer, colors.successContainer, t)
        assertContrast("$name warning/surface", colors.warning, scheme.surface, t)
        assertContrast("$name onWarningContainer/warningContainer", colors.onWarningContainer, colors.warningContainer, t)
        assertContrast("$name info/surface", colors.info, scheme.surface, t)
        assertContrast("$name onInfoContainer/infoContainer", colors.onInfoContainer, colors.infoContainer, t)

        assertContrast("$name codeText/codeSurface", colors.codeText, colors.codeSurface, t)
        assertContrast("$name codeText/codeSurfaceAlt", colors.codeText, colors.codeSurfaceAlt, t)
        assertContrast("$name codeTextMuted/codeSurface", colors.codeTextMuted, colors.codeSurface, t)

        assertContrast("$name terminalText/terminalSurface", colors.terminalText, colors.terminalSurface, t)
        assertContrast("$name terminalTextMuted/terminalSurface", colors.terminalTextMuted, colors.terminalSurface, t)
        assertContrast("$name terminalPrompt/terminalSurface", colors.terminalPrompt, colors.terminalSurface, t)
        assertContrast("$name terminalEmpty/terminalSurface", colors.terminalEmpty, colors.terminalSurface, t)

        assertContrast("$name diffAddedText/diffAddedSurface", colors.diffAddedText, colors.diffAddedSurface, t)
        assertContrast("$name diffRemovedText/diffRemovedSurface", colors.diffRemovedText, colors.diffRemovedSurface, t)
        assertContrast("$name diffInfoText/diffAddedSurface", colors.codeTextMuted, colors.diffAddedSurface, t)
        assertContrast("$name diffContextText/diffAddedSurface", colors.codeText, colors.diffAddedSurface, t)
    }

    @Test
    fun `every audited dark text and container pair meets 4_5 to 1`() {
        assertTextPairs(PocketForgeDarkScheme, PocketForgeDarkColors, "dark")
    }

    @Test
    fun `every audited light text and container pair meets 4_5 to 1`() {
        assertTextPairs(PocketForgeLightScheme, PocketForgeLightColors, "light")
    }

    /** Non-text boundary: WCAG 1.4.11 wants >= 3:1 for meaningful boundaries. */
    private fun assertBorderStrong(scheme: ColorScheme, colors: PocketColors, name: String) {
        val surfaces = mapOf(
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "background" to scheme.background,
        )
        for ((role, bg) in surfaces) {
            assertContrast("$name borderStrong/$role", colors.borderStrong, bg, 3.0)
        }
    }

    @Test
    fun `borderStrong clears 3 to 1 on every surface in both themes`() {
        assertBorderStrong(PocketForgeDarkScheme, PocketForgeDarkColors, "dark")
        assertBorderStrong(PocketForgeLightScheme, PocketForgeLightColors, "light")
    }

    /** Decorative separators are intentionally subtle and must stay that way. */
    @Test
    fun `outline and outlineVariant remain subtle decorative separators`() {
        val dark = contrast(PocketForgeDarkScheme.outlineVariant, PocketForgeDarkScheme.surface)
        val light = contrast(PocketForgeLightScheme.outlineVariant, PocketForgeLightScheme.surface)
        assertTrue("dark outlineVariant should stay subtle, was %.2f".format(dark), dark < 2.0)
        assertTrue("light outlineVariant should stay subtle, was %.2f".format(light), light < 2.0)
    }

    // ── Typography ──────────────────────────────────────────────────────────

    @Test
    fun `brand type scale matches the approved sizes, line heights and weights`() {
        val t = PocketTypography
        assertEquals(28.sp, t.welcomeHeadline.fontSize)
        assertEquals(34.sp, t.welcomeHeadline.lineHeight)
        assertEquals(FontWeight.SemiBold, t.welcomeHeadline.fontWeight)

        assertEquals(24.sp, t.screenTitle.fontSize)
        assertEquals(30.sp, t.screenTitle.lineHeight)
        assertEquals(FontWeight.SemiBold, t.screenTitle.fontWeight)

        assertEquals(18.sp, t.sectionTitle.fontSize)
        assertEquals(24.sp, t.sectionTitle.lineHeight)
        assertEquals(FontWeight.SemiBold, t.sectionTitle.fontWeight)

        assertEquals(16.sp, t.rowTitle.fontSize)
        assertEquals(22.sp, t.rowTitle.lineHeight)
        assertEquals(FontWeight.Medium, t.rowTitle.fontWeight)

        assertEquals(16.sp, t.body.fontSize)
        assertEquals(24.sp, t.body.lineHeight)
        assertEquals(FontWeight.Normal, t.body.fontWeight)

        assertEquals(14.sp, t.supporting.fontSize)
        assertEquals(20.sp, t.supporting.lineHeight)
        assertEquals(FontWeight.Normal, t.supporting.fontWeight)

        assertEquals(14.sp, t.buttonLabel.fontSize)
        assertEquals(20.sp, t.buttonLabel.lineHeight)
        assertEquals(FontWeight.SemiBold, t.buttonLabel.fontWeight)

        assertEquals(12.sp, t.navigationLabel.fontSize)
        assertEquals(16.sp, t.navigationLabel.lineHeight)
        assertEquals(FontWeight.Medium, t.navigationLabel.fontWeight)

        assertEquals(12.sp, t.metadata.fontSize)
        assertEquals(18.sp, t.metadata.lineHeight)
        assertEquals(FontWeight.Normal, t.metadata.fontWeight)
    }

    @Test
    fun `code role is monospace at 14 on 20`() {
        assertEquals(FontFamily.Monospace, PocketTypography.code.fontFamily)
        assertEquals(14.sp, PocketTypography.code.fontSize)
        assertEquals(20.sp, PocketTypography.code.lineHeight)
        assertEquals(FontWeight.Normal, PocketTypography.code.fontWeight)
    }

    @Test
    fun `Material typography carries the mapped roles`() {
        val m = PocketTypography.material
        assertEquals(PocketTypography.welcomeHeadline, m.displaySmall)
        assertEquals(PocketTypography.screenTitle, m.headlineMedium)
        assertEquals(PocketTypography.screenTitle, m.headlineSmall)
        assertEquals(PocketTypography.sectionTitle, m.titleLarge)
        assertEquals(PocketTypography.sectionTitle, m.titleMedium)
        assertEquals(PocketTypography.rowTitle, m.titleSmall)
        assertEquals(PocketTypography.body, m.bodyLarge)
        assertEquals(PocketTypography.supporting, m.bodyMedium)
        assertEquals(PocketTypography.metadata, m.bodySmall)
        assertEquals(PocketTypography.buttonLabel, m.labelLarge)
        assertEquals(PocketTypography.navigationLabel, m.labelMedium)
        assertEquals(PocketTypography.metadata, m.labelSmall)
    }

    @Test
    fun `every type role uses the system sans family except code`() {
        val t = PocketTypography
        listOf(
            t.welcomeHeadline, t.screenTitle, t.sectionTitle, t.rowTitle, t.body,
            t.supporting, t.buttonLabel, t.navigationLabel, t.metadata,
        ).forEach { assertEquals(FontFamily.Default, it.fontFamily) }
    }

    // ── Shapes and dimensions ───────────────────────────────────────────────

    @Test
    fun `shapes are the approved radii`() {
        // Density 1f + a unit shape size makes CornerSize.toPx() return the dp value.
        val density = object : androidx.compose.ui.unit.Density {
            override val density: Float = 1f
            override val fontScale: Float = 1f
        }
        val unit = androidx.compose.ui.geometry.Size(1f, 1f)
        fun radiusOf(shape: androidx.compose.ui.graphics.Shape): androidx.compose.ui.unit.Dp {
            val rounded = shape as androidx.compose.foundation.shape.RoundedCornerShape
            return androidx.compose.ui.unit.Dp(rounded.topStart.toPx(unit, density))
        }

        assertEquals(8.dp, radiusOf(PocketShapes.extraSmall))
        assertEquals(12.dp, radiusOf(PocketShapes.small))
        assertEquals(16.dp, radiusOf(PocketShapes.medium))
        assertEquals(20.dp, radiusOf(PocketShapes.large))
        assertEquals(28.dp, radiusOf(PocketShapes.extraLarge))
    }

    @Test
    fun `spacing tokens are the approved scale`() {
        val s = PocketSpacingTokens
        assertEquals(4.dp, s.space4)
        assertEquals(8.dp, s.space8)
        assertEquals(12.dp, s.space12)
        assertEquals(16.dp, s.space16)
        assertEquals(20.dp, s.space20)
        assertEquals(24.dp, s.space24)
        assertEquals(32.dp, s.space32)
    }

    @Test
    fun `radius tokens are the approved scale`() {
        val r = PocketRadiusTokens
        assertEquals(8.dp, r.radius8)
        assertEquals(12.dp, r.radius12)
        assertEquals(16.dp, r.radius16)
        assertEquals(20.dp, r.radius20)
    }

    @Test
    fun `size tokens match the approved control metrics`() {
        val s = PocketSizeTokens
        assertEquals(48.dp, s.minimumTouchTarget)
        assertEquals(40.dp, s.compactControlHeight)
        assertEquals(48.dp, s.standardControlHeight)
        assertEquals(56.dp, s.comfortableControlHeight)
        assertTrue(
            "compact visual height must sit inside the minimum touch target",
            s.compactControlHeight < s.minimumTouchTarget,
        )
    }
}
