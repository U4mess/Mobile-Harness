package com.jarves.mh.ui

import androidx.compose.ui.unit.dp
import com.jarves.mh.ui.theme.PocketSizeTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for the Agent screen migration.
 *
 * The status/role rules are asserted through the same [AgentPresentation] functions the
 * screen calls, so nothing is restated here. A small number of source-scan guards cover
 * the "no legacy colour remains" invariants that are not reachable through a pure
 * function; those are secondary guards, not the behaviour tests.
 */
class AgentScreenTest {

    // ── Status semantics ────────────────────────────────────────────────────

    @Test
    fun `a healthy connection maps to success`() {
        val role = AgentPresentation.statusRole(testing = false, failed = false, healthy = true)
        assertEquals(AgentPresentation.StatusRole.SUCCESS, role)
        assertEquals("Online", AgentPresentation.statusLabel(role))
    }

    @Test
    fun `an in-flight test maps to primary`() {
        val role = AgentPresentation.statusRole(testing = true, failed = false, healthy = false)
        assertEquals(AgentPresentation.StatusRole.PRIMARY, role)
        assertEquals("Testing…", AgentPresentation.statusLabel(role))
    }

    @Test
    fun `a failure maps to error and outranks a healthy flag`() {
        val role = AgentPresentation.statusRole(testing = false, failed = true, healthy = true)
        assertEquals(AgentPresentation.StatusRole.ERROR, role)
        assertEquals("Attention", AgentPresentation.statusLabel(role))
    }

    @Test
    fun `an untested connection is neutral, never success`() {
        val role = AgentPresentation.statusRole(testing = false, failed = false, healthy = false)
        assertEquals(AgentPresentation.StatusRole.NEUTRAL, role)
        assertEquals("Not tested", AgentPresentation.statusLabel(role))
        assertFalse(
            "an untested connection must not claim success",
            role == AgentPresentation.StatusRole.SUCCESS,
        )
    }

    @Test
    fun `testing outranks failure so a retry does not flash an error`() {
        assertEquals(
            AgentPresentation.StatusRole.PRIMARY,
            AgentPresentation.statusRole(testing = true, failed = true, healthy = false),
        )
    }

    @Test
    fun `every status role has a distinct non-empty label`() {
        val labels = AgentPresentation.StatusRole.entries.map(AgentPresentation::statusLabel)
        assertEquals("labels must be distinct: $labels", labels.size, labels.distinct().size)
        assertTrue(labels.all { it.isNotBlank() })
    }

    // ── Row / action presentation ───────────────────────────────────────────

    @Test
    fun `connection rows are the approved compact set in order`() {
        assertEquals(
            listOf("AI provider", "AI model", "Credentials", "Advanced settings"),
            AgentPresentation.ConnectionRow.entries.map { it.title },
        )
    }

    @Test
    fun `there is exactly one primary connection action label`() {
        assertEquals("Test connection", AgentPresentation.TEST_CONNECTION_LABEL)
    }

    // ── Touch targets ───────────────────────────────────────────────────────

    @Test
    fun `minimum touch target is at least 48dp`() {
        assertTrue(
            "minimumTouchTarget was ${PocketSizeTokens.minimumTouchTarget}",
            PocketSizeTokens.minimumTouchTarget >= 48.dp,
        )
    }

    @Test
    fun `standard control height is at least 48dp`() {
        assertTrue(PocketSizeTokens.standardControlHeight >= 48.dp)
    }

    // ── Source guards (secondary migration guards, not behaviour tests) ─────

    private val source: String by lazy {
        val f = File("src/main/java/com/jarves/mh/ui/AgentScreen.kt")
        assertTrue("AgentScreen.kt not found at ${f.absolutePath}", f.isFile)
        f.readText()
    }

    @Test
    fun `no PocketOrange remains in the Agent screen`() {
        assertFalse(
            "AgentScreen must not reference the legacy PocketOrange token",
            source.contains("PocketOrange"),
        )
    }

    @Test
    fun `no hardcoded Agent success greens remain`() {
        for (literal in listOf("58C9A3", "2E9D72")) {
            assertFalse(
                "hardcoded success green #$literal must be replaced by PocketTheme.colors.success",
                source.contains(literal),
            )
        }
    }

    @Test
    fun `no hardcoded 46dp control heights remain`() {
        assertFalse(
            "fixed 46dp controls clip at large font scales; use defaultMinSize",
            source.contains("height(46.dp)"),
        )
    }

    @Test
    fun `action buttons use a minimum height rather than a fixed one`() {
        assertTrue(
            "Test connection / Validate & save must use defaultMinSize(minHeight = standardControlHeight)",
            source.contains("defaultMinSize(minHeight = PocketTheme.sizes.standardControlHeight)"),
        )
        assertTrue(
            "small text actions must use the 48dp minimum",
            source.contains("defaultMinSize(minHeight = PocketTheme.sizes.minimumTouchTarget)"),
        )
    }

    @Test
    fun `the security footer uses full-alpha supporting typography`() {
        assertTrue(
            "footer should use the supporting role",
            source.contains("style = PocketTheme.typography.supporting"),
        )
        assertFalse(
            "footer must not fade to 70% alpha",
            source.contains("onSurfaceVariant.copy(alpha = 0.7f)"),
        )
    }

    @Test
    fun `oversized Agent section headings were removed`() {
        assertFalse(
            "20sp Bold headings were replaced by sectionTitle",
            source.contains("fontSize = 20.sp"),
        )
    }
}
