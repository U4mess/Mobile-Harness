package com.jarves.mh.runtime

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bounded checks for [awaitProcessExit] — the shared wait/cleanup used when the
 * installer verifies a guest command — driven by a real long-running OS process.
 *
 * The Android native binding cannot run on a host JVM, so the `waitpid` flag
 * mapping (`no_hang ? WNOHANG : 0`) is verified by reading `pocket_spawn.c`.
 * These tests pin the behaviour that mapping produces: `isAlive` never blocks,
 * a timed-out verification owns and destroys its process, and cancelling the
 * coroutine does the same.
 */
class ProcessVerificationTest {

    /**
     * Starts a process that outlives every timeout used here. Both commands write
     * nothing, so the default stdio pipes never fill and need no redirection
     * (`ProcessBuilder.Redirect` is absent from the Android stub jar).
     */
    private fun startLongRunningProcess(seconds: Int): Process {
        val command = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("cmd", "/c", "ping -n ${seconds + 1} 127.0.0.1 > NUL")
        } else {
            listOf("sleep", seconds.toString())
        }
        return ProcessBuilder(command).start()
    }

    @Test
    fun `isAlive returns promptly while the process is running`() {
        val process = startLongRunningProcess(20)
        try {
            val startedAt = System.nanoTime()
            repeat(5) { assertTrue("process should still be running", process.isAlive) }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("five isAlive polls must not block (took ${elapsedMs}ms)", elapsedMs < 2_000)
        } finally {
            process.destroyForcibly()
        }
    }

    @Test
    fun `verification times out and cleans up its owned process`() = runBlocking {
        val process = startLongRunningProcess(30)
        val error = runCatching {
            awaitProcessExit(process, timeoutMs = 1_000L, timeoutMessage = "verification timed out")
        }.exceptionOrNull()

        assertEquals("verification timed out", error?.message)
        assertTrue("timed-out process must be destroyed", waitForExit(process))
    }

    @Test
    fun `cancellation destroys the owned process`() = runBlocking {
        val process = startLongRunningProcess(30)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val waiter = scope.async {
                awaitProcessExit(process, timeoutMs = 60_000L, timeoutMessage = "never reached")
            }
            delay(300)
            assertTrue("verification should still be waiting", waiter.isActive)
            waiter.cancel()
            runCatching { waiter.await() }
            assertTrue("cancelled verification must destroy its process", waitForExit(process))
        } finally {
            scope.cancel()
            process.destroyForcibly()
        }
    }

    private fun waitForExit(process: Process): Boolean =
        process.waitFor(5, TimeUnit.SECONDS)
}
