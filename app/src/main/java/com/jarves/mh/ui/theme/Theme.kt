package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

/**
 * PocketForge Midnight Studio theme.
 *
 * Installs the explicit Material colour scheme (every role defined), the
 * PocketForge type scale, real shapes, and the semantic/dimension token
 * providers. The public entry point is unchanged:
 *
 * ```
 * PocketTheme(themeMode = AppThemeMode.SYSTEM) { App() }
 * ```
 */
@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val pocketColors = if (isDark) PocketForgeDarkColors else PocketForgeLightColors

    CompositionLocalProvider(
        LocalPocketColors provides pocketColors,
        LocalPocketSpacing provides PocketSpacingTokens,
        LocalPocketRadii provides PocketRadiusTokens,
        LocalPocketSizes provides PocketSizeTokens,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) PocketForgeDarkScheme else PocketForgeLightScheme,
            // Typography and shapes are deliberately NOT installed globally.
            //
            // Unmigrated screens (Agent, Settings, Chat, Terminal, onboarding,
            // dialogs) still use bare `MaterialTheme.typography.*` and
            // `MaterialTheme.shapes.*`, and Material components read the whole
            // type/shape set internally. Installing the Pocket scale globally
            // resized and re-rounded every one of those screens at once, before
            // any of them had been visually validated for it.
            //
            // Migrated screens opt in explicitly through `PocketTheme.typography`
            // and `PocketTheme.radii`. Install the Pocket scales globally only
            // once every screen has been migrated and visually validated on a
            // device; until then Material 3 defaults are the compatibility
            // baseline.
            content = content,
        )
    }
}

/**
 * Token accessors. `PocketTheme` is both this object and the composable above —
 * Kotlin resolves the composable by call syntax and the object by member access,
 * so both can share the name without conflict.
 */
object PocketTheme {
    /** Semantic tokens Material has no role for. */
    val colors: PocketColors
        @Composable @ReadOnlyComposable get() = LocalPocketColors.current

    /** Brand type roles; `MaterialTheme.typography` carries the mapped subset. */
    val typography: PocketTypeScale
        @Composable @ReadOnlyComposable get() = PocketTypography

    val spacing: PocketSpacing
        @Composable @ReadOnlyComposable get() = LocalPocketSpacing.current

    val radii: PocketRadii
        @Composable @ReadOnlyComposable get() = LocalPocketRadii.current

    val sizes: PocketSizes
        @Composable @ReadOnlyComposable get() = LocalPocketSizes.current
}
