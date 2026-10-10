package com.jarves.mh.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PocketForge Midnight Studio — raw palette.
 *
 * Every literal colour in the design system lives here so the semantic layers
 * ([pocketForgeDarkScheme], [PocketForgeColors]) never contain a hex value.
 * Nothing in this file is consumed by screens directly.
 */

// ── Primary (PocketForge blue) ───────────────────────────────────────────────
internal val BlueDarkPrimary = Color(0xFF8AB4FF)
internal val BlueDarkOnPrimary = Color(0xFF102344)
internal val BlueDarkPrimaryContainer = Color(0xFF203657)
internal val BlueDarkOnPrimaryContainer = Color(0xFFE0EBFF)
internal val BlueDarkInversePrimary = Color(0xFF245BC7)

internal val BlueLightPrimary = Color(0xFF245BC7)
internal val BlueLightOnPrimary = Color(0xFFFFFFFF)
internal val BlueLightPrimaryContainer = Color(0xFFE0EBFF)
internal val BlueLightOnPrimaryContainer = Color(0xFF102344)
internal val BlueLightInversePrimary = Color(0xFF8AB4FF)

// ── Secondary (cyan) ────────────────────────────────────────────────────────
internal val CyanDark = Color(0xFF67DCEB)
internal val CyanDarkOn = Color(0xFF062D35)
internal val CyanDarkContainer = Color(0xFF123842)
internal val CyanDarkOnContainer = Color(0xFFBEEFF5)

internal val CyanLight = Color(0xFF006B78)
internal val CyanLightOn = Color(0xFFFFFFFF)
internal val CyanLightContainer = Color(0xFFBCECF2)
internal val CyanLightOnContainer = Color(0xFF00363D)

// ── Tertiary (success green) ────────────────────────────────────────────────
internal val GreenDark = Color(0xFF65D6A4)
internal val GreenDarkOn = Color(0xFF063A26)
internal val GreenDarkContainer = Color(0xFF123B2B)
internal val GreenDarkOnContainer = Color(0xFFCAFAE2)

internal val GreenLight = Color(0xFF176B48)
internal val GreenLightOn = Color(0xFFFFFFFF)
internal val GreenLightContainer = Color(0xFFC8F2DD)
internal val GreenLightOnContainer = Color(0xFF073A26)

// ── Foundation neutrals ─────────────────────────────────────────────────────
internal val NavyBackgroundDark = Color(0xFF0B1018)
internal val NavySurfaceDark = Color(0xFF131C29)
internal val NavySurfaceVariantDark = Color(0xFF1B2738)
internal val NavyOutlineDark = Color(0xFF2C3B50)
internal val NavyOutlineVariantDark = Color(0xFF223044)
internal val NavyOnDark = Color(0xFFF1F5FC)
internal val NavyOnVariantDark = Color(0xFFAAB8CC)

internal val NavyContainerLowestDark = Color(0xFF080C12)
internal val NavyContainerLowDark = Color(0xFF0F1621)
internal val NavyContainerHighDark = Color(0xFF1B2738)
internal val NavyContainerHighestDark = Color(0xFF233146)
internal val NavyDimDark = Color(0xFF090E15)
internal val NavyBrightDark = Color(0xFF27364A)

internal val SlateBackgroundLight = Color(0xFFF5F7FB)
internal val SlateSurfaceLight = Color(0xFFFFFFFF)
internal val SlateSurfaceVariantLight = Color(0xFFEAF0F8)
internal val SlateOutlineLight = Color(0xFFD5DEEA)
internal val SlateOutlineVariantLight = Color(0xFFE1E7EF)
internal val SlateOnLight = Color(0xFF142033)
internal val SlateOnVariantLight = Color(0xFF526176)

internal val SlateContainerLowestLight = Color(0xFFFFFFFF)
internal val SlateContainerLowLight = Color(0xFFF8FAFD)
internal val SlateContainerLight = Color(0xFFF1F5FA)
internal val SlateContainerHighLight = Color(0xFFEAF0F8)
internal val SlateContainerHighestLight = Color(0xFFE1E9F3)
internal val SlateDimLight = Color(0xFFD9E1EC)
internal val SlateBrightLight = Color(0xFFFFFFFF)

// ── Error (Midnight Studio, not Material baseline) ──────────────────────────
internal val ErrorDark = Color(0xFFFF9B9B)
internal val ErrorDarkOn = Color(0xFF521018)
internal val ErrorDarkContainer = Color(0xFF5C2027)
internal val ErrorDarkOnContainer = Color(0xFFFFD9DC)

internal val ErrorLight = Color(0xFFB42332)
internal val ErrorLightOn = Color(0xFFFFFFFF)
internal val ErrorLightContainer = Color(0xFFFFDADD)
internal val ErrorLightOnContainer = Color(0xFF540812)

// ── Custom semantic palette ─────────────────────────────────────────────────
internal val WarningDark = Color(0xFFF2C66D)
internal val WarningDarkContainer = Color(0xFF3A2E12)
internal val WarningDarkOnContainer = Color(0xFFFFEBC2)

internal val WarningLight = Color(0xFF805600)
internal val WarningLightContainer = Color(0xFFFFEFC7)
internal val WarningLightOnContainer = Color(0xFF4A3200)

internal val InfoDark = Color(0xFF8AB4FF)
internal val InfoDarkContainer = Color(0xFF203657)
internal val InfoDarkOnContainer = Color(0xFFE0EBFF)

internal val InfoLight = Color(0xFF245BC7)
internal val InfoLightContainer = Color(0xFFE0EBFF)
internal val InfoLightOnContainer = Color(0xFF102344)

/**
 * Meaningful interactive boundaries. Verified >= 3:1 against every surface and
 * surfaceVariant in its theme (see PocketColorsContrastTest); the decorative
 * [NavyOutlineDark]/[NavyOutlineVariantDark] pair is intentionally subtler.
 */
internal val BorderStrongDark = Color(0xFF73859F)
internal val BorderStrongLight = Color(0xFF848484)

/** Modal dimming layer. Applied with call-site alpha; never carries content. */
internal val ScrimBlack = Color(0xFF000000)

// ── Always-dark terminal (both themes) ──────────────────────────────────────
internal val TerminalSurface = Color(0xFF080C12)
internal val TerminalText = Color(0xFFF0F6FC)
internal val TerminalTextMuted = Color(0xFFAAB8CC)
internal val TerminalPrompt = Color(0xFF65D6A4)
internal val TerminalEmpty = Color(0xFF7D8A9B)

// ── Diff ────────────────────────────────────────────────────────────────────
internal val DiffAddedSurfaceDark = Color(0xFF123B2B)
internal val DiffRemovedSurfaceDark = Color(0xFF5C2027)
internal val DiffAddedTextDark = Color(0xFF8FE7BC)
internal val DiffRemovedTextDark = Color(0xFFFFB4B4)

internal val DiffAddedSurfaceLight = Color(0xFFE3F5EB)
internal val DiffRemovedSurfaceLight = Color(0xFFFFE4E6)
internal val DiffAddedTextLight = Color(0xFF0B4F30)
internal val DiffRemovedTextLight = Color(0xFF8C1220)

// ── Domain identity colours (identities, not theme roles) ───────────────────
/**
 * Vendor and technology identities. These are brands, so they deliberately do
 * not follow the theme and keep the same value in both schemes.
 */
internal object PocketForgeIdentities {
    val agentClaude = Color(0xFFD97757)
    val agentDeepSeek = Color(0xFF4D6BFE)
    val agentAntigravity = Color(0xFF4285F4)

    val providerClaude = Color(0xFFD97757)
    val providerAnthropic = Color(0xFFE7A26D)
    val providerRouter = Color(0xFF5B8DEF)
    val providerDeepSeek = Color(0xFF4D6BFE)
    val providerKimi = Color(0xFF8B7CF6)
    val providerOpenCode = Color(0xFF22C55E)
    val providerNvidia = Color(0xFF76B900)

    val stackAndroid = Color(0xFF4ADE80)
    val stackPython = Color(0xFFFBBF24)
    val stackCpp = Color(0xFFA78BFA)
    val stackPhp = Color(0xFF818CF8)

    val githubGreen = Color(0xFF34A853)
    val badgeFree = Color(0xFF58C99C)
    val selectionOnPrimary = Color(0xFF241107)
}
