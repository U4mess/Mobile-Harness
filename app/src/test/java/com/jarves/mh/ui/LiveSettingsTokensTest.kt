package com.jarves.mh.ui

import androidx.compose.ui.unit.dp
import com.jarves.mh.ui.theme.PocketSizeTokens
import com.jarves.mh.ui.theme.PocketSpacingTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the **live** Settings screen.
 *
 * This replaces the earlier `SettingsScreenTokensTest`, which asserted against
 * `SettingsScreen.kt` — a file whose only composable (`LegacySettingsScreen`) is
 * `private` and has no caller. That test stayed green while the screen users actually
 * see was untouched, so it could not have caught a regression.
 *
 * Assertions here target `SettingsScreenModern.kt`, which declares the `SettingsScreen`
 * composable that `PocketDevApp` calls.
 *
 * Dead composables inside that file (`AntigravityConnectionSettings`,
 * `ConnectionSettings`, `SelectionDot`) are approved to retain their legacy colour
 * references until they are deleted, so colour assertions exclude them.
 */
class LiveSettingsTokensTest {

    private val sourceText: String by lazy {
        val f = File("src/main/java/com/jarves/mh/ui/SettingsScreenModern.kt")
        assertTrue("SettingsScreenModern.kt not found at ${f.absolutePath}", f.isFile)
        f.readText()
    }

    private val lines: List<String> by lazy { sourceText.lines() }

    /** Ranges of the approved-unreachable composables, by declaration and brace matching. */
    private val deadRanges: List<IntRange> by lazy {
        listOf("AntigravityConnectionSettings", "ConnectionSettings", "SelectionDot").mapNotNull { name ->
            val start = lines.indexOfFirst { it.startsWith("private fun $name(") }
            if (start < 0) return@mapNotNull null
            var depth = 0
            var seen = false
            var end = lines.lastIndex
            for (i in start until lines.size) {
                for (ch in lines[i]) {
                    when (ch) {
                        '{' -> { depth++; seen = true }
                        '}' -> depth--
                    }
                }
                if (seen && depth == 0) { end = i; break }
            }
            (start + 1)..(end + 1)
        }
    }

    private fun isDead(lineNumber: Int): Boolean = deadRanges.any { lineNumber in it }

    /** Reachable (non-dead) code as line number to text. */
    private fun liveLines(): List<Pair<Int, String>> =
        lines.mapIndexedNotNull { i, l -> if (isDead(i + 1)) null else (i + 1) to l }

    private fun liveLineContaining(fragment: String): String =
        liveLines().firstOrNull { it.second.contains(fragment) }?.second
            ?: error("no reachable line containing: $fragment")

    // ── Live/dead resolution ────────────────────────────────────────────────

    @Test
    fun `the live SettingsScreen is declared in the modern file`() {
        assertTrue(sourceText.contains("fun SettingsScreen("))
    }

    @Test
    fun `the approved dead composables are still present and declaration-only`() {
        for (name in listOf("AntigravityConnectionSettings", "ConnectionSettings")) {
            val count = lines.count { it.startsWith("private fun $name(") }
            assertEquals("$name should still be declared exactly once", 1, count)
        }
    }

    // ── Reachable orange must be zero ───────────────────────────────────────

    @Test
    fun `no reachable line references the legacy PocketOrange token`() {
        val offenders = liveLines()
            .filter { it.second.contains("PocketOrange") }
            // The import is required by the approved dead code; it is not a rendering site.
            .filterNot { it.second.trimStart().startsWith("import ") }
        assertTrue(
            "reachable Settings code must not use PocketOrange, found at ${offenders.map { it.first }}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `legacy orange survives only inside the approved dead composables`() {
        val orange = lines.mapIndexedNotNull { i, l -> if (l.contains("PocketOrange")) i + 1 else null }
        val live = orange
            .filterNot { isDead(it) }
            .filterNot { lines[it - 1].trimStart().startsWith("import ") }
        assertTrue("unexpected reachable orange at $live", live.isEmpty())
        assertTrue("expected the dead composables to still hold orange", orange.any { isDead(it) })
    }

    // ── Semantic colour roles ───────────────────────────────────────────────

    @Test
    fun `the attribution gear uses the primary role`() {
        val line = liveLineContaining("Icons.Default.Settings, null, Modifier.size(20.dp)")
        assertTrue(line, line.contains("MaterialTheme.colorScheme.primary"))
        assertFalse(line, line.contains("PocketOrange"))
    }

    @Test
    fun `selected theme choice uses primaryContainer, primary and onPrimaryContainer`() {
        val container = liveLineContaining("if (selected) MaterialTheme.colorScheme.primaryContainer")
        assertTrue(container, container.contains("MaterialTheme.colorScheme.primaryContainer"))

        val border = liveLineContaining("outlineVariant") // sanity: the row exists in live code
        assertTrue(border.isNotEmpty())
        assertTrue(
            liveLines().any { it.second.contains("if (selected) MaterialTheme.colorScheme.primary else") },
        )
        assertTrue(
            liveLines().any { it.second.contains("tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer") },
        )
    }

    @Test
    fun `no selection styling in reachable code uses the legacy accent`() {
        val selectionLines = liveLines().filter {
            it.second.contains("selected") && (it.second.contains("colorScheme.") || it.second.contains("Pocket"))
        }
        for ((n, l) in selectionLines) {
            assertFalse("line $n must not use PocketOrange", l.contains("PocketOrange"))
        }
    }

    @Test
    fun `install progress uses primary and installed state uses the success token`() {
        assertTrue(
            liveLines().any { it.second.contains("LinearProgressIndicator(") },
        )
        assertTrue(
            "installed state should use the success token",
            liveLines().any { it.second.contains("PocketTheme.colors.success") },
        )
        assertTrue(
            "progress should be primary",
            liveLines().any { it.second.contains("color = MaterialTheme.colorScheme.primary,") },
        )
    }

    @Test
    fun `Remove stays an error-coloured, confirmation-gated action`() {
        val remove = liveLineContaining("Text(\"Remove\", color = MaterialTheme.colorScheme.error)")
        assertTrue(remove.contains("MaterialTheme.colorScheme.error"))
        assertTrue(
            "remove must be gated on the pending-removal confirmation",
            sourceText.contains("stackPendingRemoval = stack"),
        )
        assertTrue(
            "the confirmation dialog must survive",
            sourceText.contains("Remove \${stack.label}?"),
        )
    }

    // ── Update channel wording and gating ───────────────────────────────────

    @Test
    fun `the update channel never claims an available default release`() {
        assertFalse(
            "must not imply the upstream GitHub release provides PocketForge updates",
            sourceText.contains("Default GitHub release"),
        )
        assertTrue(sourceText.contains(SettingsPresentation.UPDATE_CHANNEL_DORMANT))
    }

    @Test
    fun `the update channel stays debug gated with its callbacks intact`() {
        assertTrue(sourceText.contains("if (BuildConfig.DEBUG)"))
        assertTrue(sourceText.contains("onSetDebugUpdateManifestUrl"))
        assertTrue(sourceText.contains("onClearDebugUpdateManifestUrl"))
        assertTrue(sourceText.contains("initialDebugUpdateManifestUrl"))
    }

    // ── Layout policy ───────────────────────────────────────────────────────

    @Test
    fun `list bottom clearance is the comfortable control height plus one spacing step`() {
        assertEquals(72.dp, SettingsPresentation.listBottomClearance(56.dp, 16.dp))
        assertEquals(
            72.dp,
            SettingsPresentation.listBottomClearance(
                PocketSizeTokens.comfortableControlHeight,
                PocketSpacingTokens.space16,
            ),
        )
    }

    @Test
    fun `toolchain descriptions allow two lines before ellipsising`() {
        assertEquals(2, SettingsPresentation.TOOLCHAIN_DESCRIPTION_MAX_LINES)
        // The description is a multi-line call, so assert on the enclosing statement.
        val start = lines.indexOfFirst { it.contains("stack.installsSummary") }
        assertTrue("stack.installsSummary not found", start >= 0)
        val statement = lines.subList(start, (start + 5).coerceAtMost(lines.size)).joinToString("\n")
        assertTrue(
            "the clipping description must not be limited to one line:\n$statement",
            statement.contains("SettingsPresentation.TOOLCHAIN_DESCRIPTION_MAX_LINES"),
        )
        assertTrue(statement.contains("TextOverflow.Ellipsis"))
        assertFalse(
            "one line is what caused the Android/PHP clipping",
            statement.contains("maxLines = 1"),
        )
    }

    @Test
    fun `reachable interactive rows meet the 48dp minimum`() {
        assertTrue(PocketSizeTokens.minimumTouchTarget >= 48.dp)
        val occurrences = liveLines()
            .count { it.second.contains("defaultMinSize(minHeight = PocketTheme.sizes.minimumTouchTarget)") }
        assertTrue("expected several 48dp-minimum rows, found $occurrences", occurrences >= 4)
    }

    @Test
    fun `the accordion header exposes an accurate state description`() {
        assertTrue(sourceText.contains("stateDescription = if (expanded) \"Expanded\" else \"Collapsed\""))
    }

    // ── Toolchain state policy ──────────────────────────────────────────────

    @Test
    fun `toolchain trailing state follows the approved precedence`() {
        fun state(installed: Boolean, installing: Boolean, removing: Boolean, core: Boolean) =
            SettingsPresentation.toolchainState(installed, installing, removing, core)

        assertEquals(SettingsPresentation.ToolchainState.REMOVING, state(true, true, true, false))
        assertEquals(SettingsPresentation.ToolchainState.INSTALLING, state(false, true, false, false))
        assertEquals(SettingsPresentation.ToolchainState.INCLUDED, state(true, false, false, true))
        assertEquals(SettingsPresentation.ToolchainState.INSTALLED, state(true, false, false, false))
        assertEquals(SettingsPresentation.ToolchainState.AVAILABLE, state(false, false, false, false))
    }

    @Test
    fun `removing outranks installing and installing outranks installed`() {
        assertEquals(
            SettingsPresentation.ToolchainState.REMOVING,
            SettingsPresentation.toolchainState(installed = true, installing = true, removing = true, isCore = false),
        )
        assertEquals(
            SettingsPresentation.ToolchainState.INSTALLING,
            SettingsPresentation.toolchainState(installed = true, installing = true, removing = false, isCore = true),
        )
    }

    // ── Typography and shape migration ──────────────────────────────────────

    @Test
    fun `reachable Settings code uses PocketTheme typography`() {
        for (role in listOf("rowTitle", "supporting", "metadata", "buttonLabel")) {
            assertTrue(
                "expected PocketTheme.typography.$role in reachable code",
                liveLines().any { it.second.contains("PocketTheme.typography.$role") },
            )
        }
    }

    @Test
    fun `reachable Settings code no longer uses raw fontSize literals`() {
        val raw = liveLines().filter { Regex("fontSize\\s*=\\s*[\\d.]+\\.sp").containsMatchIn(it.second) }
        assertTrue("raw font sizes remain at ${raw.map { it.first }}", raw.isEmpty())
    }

    @Test
    fun `reachable Settings code no longer uses raw corner radii`() {
        val raw = liveLines().filter { Regex("RoundedCornerShape\\([\\d.]+\\.dp\\)").containsMatchIn(it.second) }
        assertTrue("raw radii remain at ${raw.map { it.first }}", raw.isEmpty())
    }
}
