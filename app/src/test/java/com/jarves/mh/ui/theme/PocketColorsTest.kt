package com.jarves.mh.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the PocketForge Midnight Studio palette down.
 *
 * These tests exist so a palette edit cannot silently change a role, leak a
 * Material baseline colour into an undefined role, or drop a contrast pair below
 * its accessibility target.
 */
class PocketColorsTest {

    private fun hex(value: Long): Color = Color(value)

    // ── Exact Material role values ──────────────────────────────────────────

    @Test
    fun `dark scheme roles are exactly the approved values`() {
        val s = PocketForgeDarkScheme
        assertEquals(hex(0xFF8AB4FF), s.primary)
        assertEquals(hex(0xFF102344), s.onPrimary)
        assertEquals(hex(0xFF203657), s.primaryContainer)
        assertEquals(hex(0xFFE0EBFF), s.onPrimaryContainer)
        assertEquals(hex(0xFF245BC7), s.inversePrimary)
        assertEquals(hex(0xFF8AB4FF), s.surfaceTint)

        assertEquals(hex(0xFF67DCEB), s.secondary)
        assertEquals(hex(0xFF062D35), s.onSecondary)
        assertEquals(hex(0xFF123842), s.secondaryContainer)
        assertEquals(hex(0xFFBEEFF5), s.onSecondaryContainer)

        assertEquals(hex(0xFF65D6A4), s.tertiary)
        assertEquals(hex(0xFF063A26), s.onTertiary)
        assertEquals(hex(0xFF123B2B), s.tertiaryContainer)
        assertEquals(hex(0xFFCAFAE2), s.onTertiaryContainer)

        assertEquals(hex(0xFF0B1018), s.background)
        assertEquals(hex(0xFFF1F5FC), s.onBackground)
        assertEquals(hex(0xFF131C29), s.surface)
        assertEquals(hex(0xFFF1F5FC), s.onSurface)
        assertEquals(hex(0xFF1B2738), s.surfaceVariant)
        assertEquals(hex(0xFFAAB8CC), s.onSurfaceVariant)
        assertEquals(hex(0xFF2C3B50), s.outline)
        assertEquals(hex(0xFF223044), s.outlineVariant)

        assertEquals(hex(0xFF080C12), s.surfaceContainerLowest)
        assertEquals(hex(0xFF0F1621), s.surfaceContainerLow)
        assertEquals(hex(0xFF131C29), s.surfaceContainer)
        assertEquals(hex(0xFF1B2738), s.surfaceContainerHigh)
        assertEquals(hex(0xFF233146), s.surfaceContainerHighest)
        assertEquals(hex(0xFF090E15), s.surfaceDim)
        assertEquals(hex(0xFF27364A), s.surfaceBright)

        assertEquals(hex(0xFFFF9B9B), s.error)
        assertEquals(hex(0xFF521018), s.onError)
        assertEquals(hex(0xFF5C2027), s.errorContainer)
        assertEquals(hex(0xFFFFD9DC), s.onErrorContainer)

        assertEquals(hex(0xFFF1F5FC), s.inverseSurface)
        assertEquals(hex(0xFF0F1621), s.inverseOnSurface)
        assertEquals(hex(0xFF000000), s.scrim)
    }

    @Test
    fun `light scheme roles are exactly the approved values`() {
        val s = PocketForgeLightScheme
        assertEquals(hex(0xFF245BC7), s.primary)
        assertEquals(hex(0xFFFFFFFF), s.onPrimary)
        assertEquals(hex(0xFFE0EBFF), s.primaryContainer)
        assertEquals(hex(0xFF102344), s.onPrimaryContainer)
        assertEquals(hex(0xFF8AB4FF), s.inversePrimary)
        assertEquals(hex(0xFF245BC7), s.surfaceTint)

        assertEquals(hex(0xFF006B78), s.secondary)
        assertEquals(hex(0xFFFFFFFF), s.onSecondary)
        assertEquals(hex(0xFFBCECF2), s.secondaryContainer)
        assertEquals(hex(0xFF00363D), s.onSecondaryContainer)

        assertEquals(hex(0xFF176B48), s.tertiary)
        assertEquals(hex(0xFFFFFFFF), s.onTertiary)
        assertEquals(hex(0xFFC8F2DD), s.tertiaryContainer)
        assertEquals(hex(0xFF073A26), s.onTertiaryContainer)

        assertEquals(hex(0xFFF5F7FB), s.background)
        assertEquals(hex(0xFF142033), s.onBackground)
        assertEquals(hex(0xFFFFFFFF), s.surface)
        assertEquals(hex(0xFF142033), s.onSurface)
        assertEquals(hex(0xFFEAF0F8), s.surfaceVariant)
        assertEquals(hex(0xFF526176), s.onSurfaceVariant)
        assertEquals(hex(0xFFD5DEEA), s.outline)
        assertEquals(hex(0xFFE1E7EF), s.outlineVariant)

        assertEquals(hex(0xFFFFFFFF), s.surfaceContainerLowest)
        assertEquals(hex(0xFFF8FAFD), s.surfaceContainerLow)
        assertEquals(hex(0xFFF1F5FA), s.surfaceContainer)
        assertEquals(hex(0xFFEAF0F8), s.surfaceContainerHigh)
        assertEquals(hex(0xFFE1E9F3), s.surfaceContainerHighest)
        assertEquals(hex(0xFFD9E1EC), s.surfaceDim)
        assertEquals(hex(0xFFFFFFFF), s.surfaceBright)

        assertEquals(hex(0xFFB42332), s.error)
        assertEquals(hex(0xFFFFFFFF), s.onError)
        assertEquals(hex(0xFFFFDADD), s.errorContainer)
        assertEquals(hex(0xFF540812), s.onErrorContainer)

        assertEquals(hex(0xFF142033), s.inverseSurface)
        assertEquals(hex(0xFFF5F7FB), s.inverseOnSurface)
        assertEquals(hex(0xFF000000), s.scrim)
    }

    // ── No baseline leakage ─────────────────────────────────────────────────

    /** Every role Material 3 can render must be the palette's, never a default. */
    private fun assertNoLeakage(scheme: ColorScheme, name: String) {
        val baseline = androidx.compose.material3.darkColorScheme()
        val roles = listOf<Pair<String, Color>>(
            "primary" to scheme.primary, "onPrimary" to scheme.onPrimary,
            "primaryContainer" to scheme.primaryContainer, "onPrimaryContainer" to scheme.onPrimaryContainer,
            "inversePrimary" to scheme.inversePrimary,
            "secondary" to scheme.secondary, "onSecondary" to scheme.onSecondary,
            "secondaryContainer" to scheme.secondaryContainer, "onSecondaryContainer" to scheme.onSecondaryContainer,
            "tertiary" to scheme.tertiary, "onTertiary" to scheme.onTertiary,
            "tertiaryContainer" to scheme.tertiaryContainer, "onTertiaryContainer" to scheme.onTertiaryContainer,
            "background" to scheme.background, "onBackground" to scheme.onBackground,
            "surface" to scheme.surface, "onSurface" to scheme.onSurface,
            "surfaceVariant" to scheme.surfaceVariant, "onSurfaceVariant" to scheme.onSurfaceVariant,
            "surfaceTint" to scheme.surfaceTint, "inverseSurface" to scheme.inverseSurface,
            "inverseOnSurface" to scheme.inverseOnSurface,
            "surfaceBright" to scheme.surfaceBright, "surfaceDim" to scheme.surfaceDim,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "outline" to scheme.outline, "outlineVariant" to scheme.outlineVariant,
            "error" to scheme.error, "onError" to scheme.onError,
            "errorContainer" to scheme.errorContainer, "onErrorContainer" to scheme.onErrorContainer,
            "scrim" to scheme.scrim,
        )
        // Material's baseline is the tell-tale: none of our roles may equal it.
        val baselineValues = setOf(
            baseline.primary, baseline.secondary, baseline.tertiary,
            baseline.primaryContainer, baseline.secondaryContainer, baseline.tertiaryContainer,
            baseline.surface, baseline.surfaceVariant, baseline.background,
            baseline.surfaceContainer, baseline.surfaceContainerHigh, baseline.surfaceContainerLow,
            baseline.surfaceContainerLowest, baseline.surfaceContainerHighest,
            baseline.surfaceBright, baseline.surfaceDim,
        )
        for ((role, value) in roles) {
            assertTrue(
                "$name.$role equals a Material baseline colour ($value) — an undefined role leaked",
                value !in baselineValues,
            )
        }
    }

    @Test
    fun `dark scheme defines every role with no Material baseline leakage`() {
        assertNoLeakage(PocketForgeDarkScheme, "dark")
    }

    @Test
    fun `light scheme defines every role with no Material baseline leakage`() {
        assertNoLeakage(PocketForgeLightScheme, "light")
    }

    // ── Semantic tokens ─────────────────────────────────────────────────────

    @Test
    fun `dark semantic tokens are exactly the approved values`() {
        val c = PocketForgeDarkColors
        assertEquals(hex(0xFF67DCEB), c.accentCyan)
        assertEquals(hex(0xFF65D6A4), c.success)
        assertEquals(hex(0xFF123B2B), c.successContainer)
        assertEquals(hex(0xFFCAFAE2), c.onSuccessContainer)
        assertEquals(hex(0xFFF2C66D), c.warning)
        assertEquals(hex(0xFF3A2E12), c.warningContainer)
        assertEquals(hex(0xFFFFEBC2), c.onWarningContainer)
        assertEquals(hex(0xFF8AB4FF), c.info)
        assertEquals(hex(0xFF203657), c.infoContainer)
        assertEquals(hex(0xFFE0EBFF), c.onInfoContainer)
        assertEquals(hex(0xFF73859F), c.borderStrong)
        assertEquals(hex(0xFF0F1621), c.codeSurface)
        assertEquals(hex(0xFF131C29), c.codeSurfaceAlt)
        assertEquals(hex(0xFF223044), c.codeBorder)
        assertEquals(hex(0xFFF1F5FC), c.codeText)
        assertEquals(hex(0xFFAAB8CC), c.codeTextMuted)
        assertEquals(hex(0xFF080C12), c.terminalSurface)
        assertEquals(hex(0xFFF0F6FC), c.terminalText)
        assertEquals(hex(0xFFAAB8CC), c.terminalTextMuted)
        assertEquals(hex(0xFF65D6A4), c.terminalPrompt)
        assertEquals(hex(0xFF7D8A9B), c.terminalEmpty)
        assertEquals(hex(0xFF123B2B), c.diffAddedSurface)
        assertEquals(hex(0xFF5C2027), c.diffRemovedSurface)
        assertEquals(hex(0xFF8FE7BC), c.diffAddedText)
        assertEquals(hex(0xFFFFB4B4), c.diffRemovedText)
    }

    @Test
    fun `light semantic tokens are exactly the approved values`() {
        val c = PocketForgeLightColors
        assertEquals(hex(0xFF006B78), c.accentCyan)
        assertEquals(hex(0xFF176B48), c.success)
        assertEquals(hex(0xFFC8F2DD), c.successContainer)
        assertEquals(hex(0xFF073A26), c.onSuccessContainer)
        assertEquals(hex(0xFF805600), c.warning)
        assertEquals(hex(0xFFFFEFC7), c.warningContainer)
        assertEquals(hex(0xFF4A3200), c.onWarningContainer)
        assertEquals(hex(0xFF245BC7), c.info)
        assertEquals(hex(0xFFE0EBFF), c.infoContainer)
        assertEquals(hex(0xFF102344), c.onInfoContainer)
        assertEquals(hex(0xFF848484), c.borderStrong)
        assertEquals(hex(0xFFF8FAFD), c.codeSurface)
        assertEquals(hex(0xFFF1F5FA), c.codeSurfaceAlt)
        assertEquals(hex(0xFFE1E7EF), c.codeBorder)
        assertEquals(hex(0xFF142033), c.codeText)
        assertEquals(hex(0xFF526176), c.codeTextMuted)
        assertEquals(hex(0xFFE3F5EB), c.diffAddedSurface)
        assertEquals(hex(0xFFFFE4E6), c.diffRemovedSurface)
        assertEquals(hex(0xFF0B4F30), c.diffAddedText)
        assertEquals(hex(0xFF8C1220), c.diffRemovedText)
    }

    @Test
    fun `terminal is identical and dark in both themes`() {
        val dark = PocketForgeDarkColors
        val light = PocketForgeLightColors
        listOf(
            dark.terminalSurface to light.terminalSurface,
            dark.terminalText to light.terminalText,
            dark.terminalTextMuted to light.terminalTextMuted,
            dark.terminalPrompt to light.terminalPrompt,
            dark.terminalEmpty to light.terminalEmpty,
        ).forEach { (d, l) -> assertEquals("terminal tokens must not vary by theme", d, l) }
        assertEquals(hex(0xFF080C12), dark.terminalSurface)
    }

    @Test
    fun `diff informational text mirrors the Material roles`() {
        assertEquals(PocketForgeDarkScheme.onSurfaceVariant, PocketForgeDarkColors.codeTextMuted)
        assertEquals(PocketForgeDarkScheme.onSurface, PocketForgeDarkColors.codeText)
        assertEquals(PocketForgeLightScheme.onSurfaceVariant, PocketForgeLightColors.codeTextMuted)
        assertEquals(PocketForgeLightScheme.onSurface, PocketForgeLightColors.codeText)
    }

    // ── Legacy compatibility symbols ────────────────────────────────────────

    @Test
    fun `legacy tokens keep their exact original values`() {
        assertEquals(hex(0xFFF28C52), PocketOrange)
        assertEquals(hex(0xFF69D69E), PocketGreen)
        assertEquals(hex(0xFF8EA8FF), PocketBlue)
        assertEquals(hex(0xFF0B0E14), PocketBackground)
        assertEquals(hex(0xFF131821), PocketSurface)
        assertEquals(hex(0xFF1B222D), PocketSurfaceVariant)
        assertEquals(hex(0xFF2A3240), PocketOutline)
    }

    @Test
    fun `PocketOrange is not redefined as the new primary blue`() {
        assertTrue(
            "PocketOrange must stay the legacy action colour",
            PocketOrange != PocketForgeDarkScheme.primary && PocketOrange != PocketForgeLightScheme.primary,
        )
    }
}
