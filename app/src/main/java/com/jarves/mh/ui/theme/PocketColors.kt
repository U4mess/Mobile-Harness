package com.jarves.mh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PocketForge semantic tokens that Material 3 has no role for.
 *
 * Read them through [PocketTheme.colors], never by constructing a scheme here.
 * The `*Container` pairs are for chips/banners; the plain colours are for icons
 * and inline text on ordinary surfaces.
 */
@Immutable
data class PocketColors(
    val accentCyan: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    /** Meaningful interactive boundaries; >= 3:1 on every surface. */
    val borderStrong: Color,
    val codeSurface: Color,
    val codeSurfaceAlt: Color,
    val codeBorder: Color,
    val codeText: Color,
    val codeTextMuted: Color,
    /** Always dark in both themes, like a real terminal. */
    val terminalSurface: Color,
    val terminalText: Color,
    val terminalTextMuted: Color,
    val terminalPrompt: Color,
    val terminalEmpty: Color,
    val diffAddedSurface: Color,
    val diffRemovedSurface: Color,
    val diffAddedText: Color,
    val diffRemovedText: Color,
)

/**
 * Decorative separator colour is the Material `outlineVariant` role — a single
 * source of truth, so no duplicate token exists here. Diff metadata and context
 * text likewise mirror `onSurfaceVariant` and `onSurface`.
 */

internal val PocketForgeDarkColors = PocketColors(
    accentCyan = CyanDark,
    success = GreenDark,
    successContainer = GreenDarkContainer,
    onSuccessContainer = GreenDarkOnContainer,
    warning = WarningDark,
    warningContainer = WarningDarkContainer,
    onWarningContainer = WarningDarkOnContainer,
    info = InfoDark,
    infoContainer = InfoDarkContainer,
    onInfoContainer = InfoDarkOnContainer,
    borderStrong = BorderStrongDark,
    codeSurface = NavyContainerLowDark,
    codeSurfaceAlt = NavySurfaceDark,
    codeBorder = NavyOutlineVariantDark,
    codeText = NavyOnDark,
    codeTextMuted = NavyOnVariantDark,
    terminalSurface = TerminalSurface,
    terminalText = TerminalText,
    terminalTextMuted = TerminalTextMuted,
    terminalPrompt = TerminalPrompt,
    terminalEmpty = TerminalEmpty,
    diffAddedSurface = DiffAddedSurfaceDark,
    diffRemovedSurface = DiffRemovedSurfaceDark,
    diffAddedText = DiffAddedTextDark,
    diffRemovedText = DiffRemovedTextDark,
)

internal val PocketForgeLightColors = PocketColors(
    accentCyan = CyanLight,
    success = GreenLight,
    successContainer = GreenLightContainer,
    onSuccessContainer = GreenLightOnContainer,
    warning = WarningLight,
    warningContainer = WarningLightContainer,
    onWarningContainer = WarningLightOnContainer,
    info = InfoLight,
    infoContainer = InfoLightContainer,
    onInfoContainer = InfoLightOnContainer,
    borderStrong = BorderStrongLight,
    codeSurface = SlateContainerLowLight,
    codeSurfaceAlt = SlateContainerLight,
    codeBorder = SlateOutlineVariantLight,
    codeText = SlateOnLight,
    codeTextMuted = SlateOnVariantLight,
    terminalSurface = TerminalSurface,
    terminalText = TerminalText,
    terminalTextMuted = TerminalTextMuted,
    terminalPrompt = TerminalPrompt,
    terminalEmpty = TerminalEmpty,
    diffAddedSurface = DiffAddedSurfaceLight,
    diffRemovedSurface = DiffRemovedSurfaceLight,
    diffAddedText = DiffAddedTextLight,
    diffRemovedText = DiffRemovedTextLight,
)

internal val LocalPocketColors = staticCompositionLocalOf { PocketForgeDarkColors }
