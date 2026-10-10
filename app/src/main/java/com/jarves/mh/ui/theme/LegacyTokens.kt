package com.jarves.mh.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Legacy Pocket palette — migration-only compatibility symbols.
 *
 * These seven names predate the Midnight Studio token layer and are kept with
 * their **exact original values** so the ~186 existing direct references keep
 * rendering as they do today. They are deliberately NOT annotated with
 * `@Deprecated`: that would emit hundreds of warnings across the UI before the
 * screens have been migrated.
 *
 * Do not add new usages. Each symbol below names its replacement; screens migrate
 * one at a time to `MaterialTheme.colorScheme.*` or `PocketTheme.colors.*`, and
 * only once the last reference is gone should these be deleted.
 *
 * `PocketOrange` is the old *action* colour. It is NOT redefined to PocketForge
 * blue — the new action colour is `MaterialTheme.colorScheme.primary`, and the two
 * will coexist visually until each screen is migrated.
 */

/** Legacy action colour (orange). Migrate to `colorScheme.primary`. */
val PocketOrange = Color(0xFFF28C52)

/** Legacy success colour. Migrate to `PocketTheme.colors.success`. */
val PocketGreen = Color(0xFF69D69E)

/** Legacy secondary accent. Migrate to `colorScheme.secondary` / `colors.accentCyan`. */
val PocketBlue = Color(0xFF8EA8FF)

/** Legacy scaffold colour. Migrate to `colorScheme.background`. */
val PocketBackground = Color(0xFF0B0E14)

/** Legacy card colour. Migrate to `colorScheme.surface`. */
val PocketSurface = Color(0xFF131821)

/** Legacy raised-surface colour. Migrate to `colorScheme.surfaceVariant`. */
val PocketSurfaceVariant = Color(0xFF1B222D)

/** Legacy border colour. Migrate to `colorScheme.outline` / `colors.borderStrong`. */
val PocketOutline = Color(0xFF2A3240)
