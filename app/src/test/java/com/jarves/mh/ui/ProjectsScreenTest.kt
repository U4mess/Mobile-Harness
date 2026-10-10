package com.jarves.mh.ui

import androidx.compose.ui.unit.dp
import com.jarves.mh.ui.theme.PocketRadiusTokens
import com.jarves.mh.ui.theme.PocketSizeTokens
import com.jarves.mh.ui.theme.PocketSpacingTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for the Projects screen.
 *
 * These call the same presentation functions production uses — nothing is restated
 * here — and assert the token contracts the layout depends on. Layout itself is not
 * asserted here because that would require Compose UI test dependencies, which this
 * project deliberately does not carry (adding them would reintroduce the
 * `ui-test-manifest` exported activity removed in the security checkpoint).
 */
class ProjectsScreenTest {

    // ── Running-state precedence ────────────────────────────────────────────

    @Test
    fun `task running outranks terminal running`() {
        assertEquals("Task running", ProjectsPresentation.runningLabel(taskRunning = true, terminalRunning = true))
    }

    @Test
    fun `terminal running shows when no task is running`() {
        assertEquals("Terminal running", ProjectsPresentation.runningLabel(taskRunning = false, terminalRunning = true))
    }

    @Test
    fun `no indicator when nothing is running`() {
        assertNull(ProjectsPresentation.runningLabel(taskRunning = false, terminalRunning = false))
    }

    // ── Card presentation mapping ───────────────────────────────────────────

    @Test
    fun `quick projects get a fixed description and named projects keep theirs`() {
        assertEquals(
            "Quick project",
            ProjectsPresentation.cardDescription(isQuickProject = true, description = "ignored"),
        )
        assertEquals(
            "Restored from an archive",
            ProjectsPresentation.cardDescription(isQuickProject = false, description = "Restored from an archive"),
        )
    }

    @Test
    fun `name and description presentation policy allow two lines`() {
        assertEquals(2, ProjectsPresentation.NAME_MAX_LINES)
        assertEquals(2, ProjectsPresentation.DESCRIPTION_MAX_LINES)
    }

    // ── FAB bottom clearance ────────────────────────────────────────────────

    @Test
    fun `terminal FAB clearance is the comfortable control height plus one spacing`() {
        val clearance = ProjectsPresentation.terminalFabClearance(
            fabHeight = PocketSizeTokens.comfortableControlHeight,
            above = PocketSpacingTokens.space16,
        )
        assertEquals(56.dp, PocketSizeTokens.comfortableControlHeight)
        assertEquals(72.dp, clearance)
    }

    @Test
    fun `terminal FAB clearance exceeds the FAB height`() {
        val clearance = ProjectsPresentation.terminalFabClearance(
            fabHeight = PocketSizeTokens.comfortableControlHeight,
            above = PocketSpacingTokens.space16,
        )
        assertTrue(
            "clearance $clearance must exceed the FAB height ${PocketSizeTokens.comfortableControlHeight}",
            clearance > PocketSizeTokens.comfortableControlHeight,
        )
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

    @Test
    fun `compact visual height stays below the touch target and is documented as visual only`() {
        assertTrue(
            "compactControlHeight must be a visual height under the 48dp touch target",
            PocketSizeTokens.compactControlHeight < PocketSizeTokens.minimumTouchTarget,
        )
    }

    // ── Token contracts the screen relies on ────────────────────────────────

    @Test
    fun `spacing and radius tokens used by the screen are the approved scale`() {
        assertEquals(4.dp, PocketSpacingTokens.space4)
        assertEquals(8.dp, PocketSpacingTokens.space8)
        assertEquals(12.dp, PocketSpacingTokens.space12)
        assertEquals(16.dp, PocketSpacingTokens.space16)
        assertEquals(8.dp, PocketRadiusTokens.radius8)
        assertEquals(12.dp, PocketRadiusTokens.radius12)
        assertEquals(16.dp, PocketRadiusTokens.radius16)
    }
}
