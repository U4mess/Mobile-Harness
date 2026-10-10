package com.jarves.mh.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Narrow migration guard for the three Settings sites migrated away from the legacy
 * PocketOrange accent.
 *
 * Settings is not yet fully tokenised, so this asserts the specific elements that were
 * deliberately changed rather than the whole file: the residual PocketOrange references
 * are still allowed until that screen is migrated in its own pass.
 */
class SettingsScreenTokensTest {

    private val source: String by lazy {
        val f = File("src/main/java/com/jarves/mh/ui/SettingsScreen.kt")
        assertTrue("SettingsScreen.kt not found at ${f.absolutePath}", f.isFile)
        f.readText()
    }

    private fun lineContaining(fragment: String): String =
        source.lines().firstOrNull { it.contains(fragment) }
            ?: error("no line containing: $fragment")

    @Test
    fun `the PocketForge gear icon is no longer the legacy orange`() {
        val icon = source.lines().indexOfFirst { it.trim().startsWith("Icons.Default.Settings,") }
        assertTrue("gear icon block not found", icon >= 0)
        val block = source.lines().subList(icon, (icon + 4).coerceAtMost(source.lines().size)).joinToString("\n")
        assertTrue(block, block.contains("MaterialTheme.colorScheme.primary"))
        assertFalse(block, block.contains("PocketOrange"))
    }

    @Test
    fun `the selected theme tile border is primary`() {
        val line = lineContaining("if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline")
        assertTrue(line, line.contains("MaterialTheme.colorScheme.primary"))
        assertFalse(line, line.contains("PocketOrange"))
    }

    @Test
    fun `the selected theme tile icon uses onPrimaryContainer`() {
        val line = source.lines().firstOrNull {
            it.contains("onPrimaryContainer") && it.contains("if (selected)")
        } ?: error("selected theme tile icon tint not migrated")
        assertFalse(line, line.contains("PocketOrange"))
    }

    @Test
    fun `the selected theme tile container is still primaryContainer`() {
        // The border and icon choices only make sense against this container.
        assertTrue(
            source.contains("if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface"),
        )
    }

    @Test
    fun `the migrated sites did not remove unrelated orange references prematurely`() {
        // Settings is migrated in its own pass; this guards against an accidental
        // whole-file sweep during a three-line change.
        assertTrue(
            "Settings should still contain its remaining PocketOrange sites for a later pass",
            source.contains("PocketOrange"),
        )
    }
}
