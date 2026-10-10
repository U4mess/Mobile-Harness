package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup state file used to be rewritten for every 128 KiB chunk, which is harmless at
 * one connection's throughput but becomes the ceiling once downloads run in parallel.
 * Milestones must still be durable immediately.
 */
class SetupPersistencePolicyTest {

    @Test
    fun `routine progress is throttled to the interval`() {
        val throttle = PersistThrottle(1_000L)

        assertTrue("the first write always happens", throttle.shouldPersist(0L, immediate = false))
        assertFalse(throttle.shouldPersist(200L, immediate = false))
        assertFalse(throttle.shouldPersist(999L, immediate = false))
        assertTrue(throttle.shouldPersist(1_000L, immediate = false))
        assertFalse(throttle.shouldPersist(1_500L, immediate = false))
        assertTrue(throttle.shouldPersist(2_000L, immediate = false))
    }

    @Test
    fun `milestones bypass the interval`() {
        val throttle = PersistThrottle(1_000L)

        assertTrue(throttle.shouldPersist(0L, immediate = false))
        assertFalse(throttle.shouldPersist(10L, immediate = false))
        // A stage change or a terminal event must never be delayed by the throttle.
        assertTrue(throttle.shouldPersist(11L, immediate = true))
        assertTrue(throttle.shouldPersist(12L, immediate = true))
    }

    @Test
    fun `stage changes and completions persist immediately while routine output does not`() {
        assertTrue(persistsImmediately(RuntimeInstallEvent.STAGE))
        assertTrue(persistsImmediately(RuntimeInstallEvent.COMMAND_COMPLETED))
        assertTrue(persistsImmediately(RuntimeInstallEvent.COMPLETED))

        assertFalse(persistsImmediately(RuntimeInstallEvent.DOWNLOAD))
        assertFalse(persistsImmediately(RuntimeInstallEvent.OUTPUT))
        assertFalse(persistsImmediately(RuntimeInstallEvent.COMMAND))
    }

    /** Every event kind is classified, so a new one cannot silently default to "routine". */
    @Test
    fun `every install event is classified`() {
        val classified = RuntimeInstallEvent.entries.filter { event ->
            persistsImmediately(event) || !persistsImmediately(event)
        }
        assertEquals(RuntimeInstallEvent.entries.size, classified.size)
    }
}
