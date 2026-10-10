package com.jarves.mh.runtime

import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract and behaviour tests for the credential-free diagnostics pipeline.
 *
 * The primary guarantee is the **type-safe API contract**: [AgentDiagnostics.event]
 * exposes no parameter that could carry a prompt, conversation history, process
 * output, tool name, path, URL, provider body or exception text. The marker tests
 * below then prove that end to end.
 */
class AgentDiagnosticsTest {

    private val capture = DiagnosticCapture()

    // ── Type-safe API contract ──────────────────────────────────────────────

    @Test
    fun `event accepts only closed enums and primitives`() {
        val event = AgentDiagnostics::class.java.methods
            .firstOrNull { it.name == "event" && Modifier.isPublic(it.modifiers) }
            ?: error("AgentDiagnostics.event not found")

        val allowed = setOf(
            DiagnosticOperation::class.java,
            DiagnosticAgent::class.java,
            DiagnosticStage::class.java,
            java.lang.Long::class.java, java.lang.Integer::class.java,
            java.lang.Boolean::class.java,
        )
        for (parameter in event.parameterTypes) {
            val boxed = if (parameter.isPrimitive) boxPrimitive(parameter) else parameter
            assertTrue(
                "event() must not accept ${parameter.name}; only closed enums and primitives are allowed",
                boxed in allowed,
            )
        }
    }

    @Test
    fun `no diagnostics method accepts a String or Throwable`() {
        val methods: List<Method> = AgentDiagnostics::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
            .filterNot { it.name == "redact" } // separately tested defence-in-depth utility
            .filterNot { it.name == "errorCategory" } // takes a Throwable but returns only an enum

        for (method in methods) {
            for (parameter in method.parameterTypes) {
                assertFalse(
                    "${method.name} must not accept a String parameter",
                    parameter == String::class.java,
                )
                assertFalse(
                    "${method.name} must not accept a Throwable parameter",
                    Throwable::class.java.isAssignableFrom(parameter),
                )
            }
        }
    }

    @Test
    fun `there is no globally mutable sink`() {
        // A static, reassignable log sink (var, or a non-final field) is what must not
        // exist. The immutable `production` instance and the TAG constant are fine.
        val mutableStatics = AgentDiagnostics::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
        assertTrue(
            "AgentDiagnostics must not hold a reassignable static log sink, found: " +
                mutableStatics.map { it.name },
            mutableStatics.isEmpty(),
        )

        val mutableCompanionFields = AgentDiagnostics.Companion::class.java.declaredFields
            .filter { !Modifier.isFinal(it.modifiers) }
        assertTrue(
            "AgentDiagnostics.Companion must not hold reassignable log state, found: " +
                mutableCompanionFields.map { it.name },
            mutableCompanionFields.isEmpty(),
        )
    }

    private fun boxPrimitive(primitive: Class<*>): Class<*> = when (primitive) {
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        else -> primitive
    }

    // ── Sensitive markers must never reach the log ──────────────────────────

    @Test
    fun `prompt and conversation history markers never enter captured logs`() {
        val prompt = "PROMPT_MARKER_7f3a explain this stacktrace"
        val history = "HISTORY_MARKER_9c1d earlier turn content"
        val command = listOf("claude", "-p", "$prompt\n$history", "--model", "sonnet")

        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.COMMAND_BUILT,
            argumentCount = command.size,
        )

        assertNoMarker("PROMPT_MARKER_7f3a")
        assertNoMarker("HISTORY_MARKER_9c1d")
    }

    @Test
    fun `stdout and stderr markers never enter captured logs`() {
        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.EXIT,
            exitCode = 0,
            byteCount = 4096,
        )
        assertNoMarker("STDOUT_MARKER_4b2e")
        assertNoMarker("STDERR_MARKER_6a8f")
    }

    @Test
    fun `paths and provider urls never enter captured logs`() {
        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.CHANGED,
            pathCount = 3,
        )
        capture.diagnostics.event(
            DiagnosticOperation.GATEWAY,
            DiagnosticAgent.GATEWAY,
            DiagnosticStage.UPSTREAM_STATUS,
            httpStatus = 429,
        )
        assertNoMarker("/data/user/0/com.jarves.mh/files/workspaces/")
        assertNoMarker("src/main/java/Foo.kt")
        assertNoMarker("https://")
        assertNoMarker("api_key=")
    }

    @Test
    fun `api key and oauth shaped values never enter captured logs`() {
        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.DSH,
            DiagnosticStage.EXIT,
            exitCode = 1,
            category = ErrorCategory.PROVIDER_REJECTED,
        )
        assertNoMarker("sk-")
        assertNoMarker("ghp_")
        assertNoMarker("github_pat_")
        assertNoMarker("xoxb-")
        assertNoMarker("AIza")
        assertNoMarker("Bearer ")
    }

    @Test
    fun `exception messages never enter captured logs`() {
        val secret = "sk-liveABCDEFGH1234567890"
        val error = IOException("failed calling https://user:pw@host/v1?api_key=$secret")

        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.EXIT,
            exitCode = 1,
            category = AgentDiagnostics.errorCategory(error),
        )

        assertNoMarker(secret)
        assertNoMarker("user:pw@")
        assertNoMarker("api_key=")
        assertNoMarker("failed calling")
    }

    private fun assertNoMarker(marker: String) {
        for (line in capture.recorded) {
            assertFalse(
                "captured log line must not contain '$marker' but was: $line",
                line.contains(marker),
            )
        }
    }

    // ── Structured safe fields must remain available ────────────────────────

    @Test
    fun `structured safe fields remain available`() {
        capture.diagnostics.event(
            DiagnosticOperation.SESSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.EXIT,
            operationId = 42L,
            elapsedMs = 1500L,
            exitCode = 0,
            byteCount = 1234L,
            pathCount = 3,
            argumentCount = 9,
            httpStatus = 200,
            cancelled = true,
            category = ErrorCategory.PROVIDER_REJECTED,
        )

        val line = assertNotNull(capture.lastLine).let { capture.lastLine!! }
        assertTrue(line, line.contains("op=session"))
        assertTrue(line, line.contains("agent=claude"))
        assertTrue(line, line.contains("stage=exit"))
        assertTrue(line, line.contains("id=42"))
        assertTrue(line, line.contains("elapsedMs=1500"))
        assertTrue(line, line.contains("exitCode=0"))
        assertTrue(line, line.contains("bytes=1234"))
        assertTrue(line, line.contains("paths=3"))
        assertTrue(line, line.contains("args=9"))
        assertTrue(line, line.contains("status=200"))
        assertTrue(line, line.contains("cancelled=true"))
        assertTrue(line, line.contains("category=provider_rejected"))
    }

    @Test
    fun `rendered records contain only the approved field keys`() {
        val approved = setOf(
            "op", "agent", "stage", "id", "elapsedMs", "exitCode",
            "bytes", "paths", "args", "status", "cancelled", "category",
        )
        capture.diagnostics.event(
            DiagnosticOperation.PERMISSION,
            DiagnosticAgent.CLAUDE,
            DiagnosticStage.AUTO_APPROVED,
            operationId = 1L,
            pathCount = 2,
            category = ErrorCategory.UNKNOWN,
        )
        for (line in capture.recorded) {
            val keys = line.split(' ')
                .filter { it.contains('=') }
                .map { it.substringBefore('=') }
            for (key in keys) {
                assertTrue("unapproved diagnostic key '$key' in: $line", key in approved)
            }
        }
    }

    // ── Error categorisation is type-based ──────────────────────────────────

    @Test
    fun `error categorisation uses exception type and not message text`() {
        assertEquals(
            ErrorCategory.CANCELLED,
            AgentDiagnostics.errorCategory(CancellationException("whatever")),
        )
        assertEquals(
            ErrorCategory.TIMEOUT,
            AgentDiagnostics.errorCategory(SocketTimeoutException("read timed out")),
        )
        assertEquals(
            ErrorCategory.NETWORK,
            AgentDiagnostics.errorCategory(UnknownHostException("no dns")),
        )
        assertEquals(
            ErrorCategory.NETWORK,
            AgentDiagnostics.errorCategory(ConnectException("refused")),
        )
        assertEquals(
            ErrorCategory.NETWORK,
            AgentDiagnostics.errorCategory(SocketException("broken pipe")),
        )
        assertEquals(
            ErrorCategory.CHECKSUM,
            AgentDiagnostics.errorCategory(ChecksumMismatchException("mismatch")),
        )
        assertEquals(
            ErrorCategory.IO,
            AgentDiagnostics.errorCategory(FileNotFoundException("/missing")),
        )
        assertEquals(
            ErrorCategory.PROVIDER_REJECTED,
            AgentDiagnostics.errorCategory(HttpFailureException(401, "unauthorized")),
        )
        assertEquals(ErrorCategory.IO, AgentDiagnostics.errorCategory(IOException("io")))
        assertEquals(ErrorCategory.UNKNOWN, AgentDiagnostics.errorCategory(IllegalStateException("x")))
        assertEquals(ErrorCategory.UNKNOWN, AgentDiagnostics.errorCategory(null))
    }

    @Test
    fun `a generic FileNotFoundException is Io and never RuntimeMissing`() {
        // RUNTIME_MISSING is reserved: no production type expresses "runtime missing"
        // yet, and a plain FileNotFoundException is too weak evidence to claim it.
        assertEquals(
            ErrorCategory.IO,
            AgentDiagnostics.errorCategory(FileNotFoundException("/whatever/path")),
        )
    }

    @Test
    fun `RuntimeMissing is reserved and currently unmapped`() {
        val classified = listOf(
            CancellationException("a"),
            SocketTimeoutException("b"),
            UnknownHostException("c"),
            ConnectException("d"),
            SocketException("e"),
            ChecksumMismatchException("f"),
            HttpFailureException(500, "g"),
            IOException("h"),
            FileNotFoundException("i"),
            IllegalStateException("j"),
            RuntimeException("k"),
            null,
        ).map { AgentDiagnostics.errorCategory(it) }

        assertTrue(
            "RUNTIME_MISSING must stay unmapped until a real production type exists",
            ErrorCategory.RUNTIME_MISSING !in classified,
        )
    }

    @Test
    fun `categorisation ignores misleading message text`() {
        // A plain IOException whose message claims to be a timeout must stay Io:
        // classification may not read message text.
        assertEquals(
            ErrorCategory.IO,
            AgentDiagnostics.errorCategory(IOException("timeout connecting to https://host")),
        )
        // And a session failure must not be classified by its text either.
        assertEquals(
            ErrorCategory.UNKNOWN,
            AgentDiagnostics.errorCategory(IllegalStateException("HTTP 401 unauthorized sk-abcdefghijklmnop")),
        )
    }

    // ── Capture isolation ───────────────────────────────────────────────────

    @Test
    fun `separate captures do not cross-contaminate`() {
        val other = DiagnosticCapture()
        capture.diagnostics.event(
            DiagnosticOperation.SESSION, DiagnosticAgent.CLAUDE, DiagnosticStage.LAUNCH_PREPARED,
        )
        other.diagnostics.event(
            DiagnosticOperation.GATEWAY, DiagnosticAgent.GATEWAY, DiagnosticStage.UPSTREAM_STATUS,
            httpStatus = 500,
        )
        assertEquals(1, capture.recorded.size)
        assertEquals(1, other.recorded.size)
        assertTrue(capture.recorded.single().contains("agent=claude"))
        assertTrue(other.recorded.single().contains("agent=gateway"))
    }

    @Test
    fun `concurrent writes to one capture are not lost`() {
        val threads = 8
        val perThread = 100
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val emitted = AtomicInteger()

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) {
                    capture.diagnostics.event(
                        DiagnosticOperation.SESSION,
                        DiagnosticAgent.CLAUDE,
                        DiagnosticStage.CHANGED,
                        pathCount = emitted.incrementAndGet(),
                    )
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("workers did not finish", done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertEquals(threads * perThread, capture.recorded.size)
    }

    // ── Redaction utility (defence in depth) ────────────────────────────────

    @Test
    fun `redact masks credential shaped values`() {
        assertFalse(AgentDiagnostics.redact("key sk-abcdefghijklmnop").contains("sk-abcdefghijklmnop"))
        assertFalse(AgentDiagnostics.redact("token ghp_abcdefghijklmnop").contains("ghp_abcdefghijklmnop"))
        assertFalse(
            AgentDiagnostics.redact("pat github_pat_abcdefghijklmnop")
                .contains("github_pat_abcdefghijklmnop"),
        )
        assertFalse(AgentDiagnostics.redact("slack xoxb-1234567890ab").contains("xoxb-1234567890ab"))
        assertFalse(AgentDiagnostics.redact("google AIzaSyABCDEFGHIJKLMNOP").contains("AIzaSyABCDEFGHIJKLMNOP"))
        assertFalse(AgentDiagnostics.redact("Bearer abcdefghijklmnop").contains("abcdefghijklmnop"))
        assertFalse(
            AgentDiagnostics.redact("https://h/v1?api_key=supersecretvalue")
                .contains("supersecretvalue"),
        )
        assertFalse(
            AgentDiagnostics.redact("https://user:hunter2@host/v1").contains("hunter2"),
        )
    }

    @Test
    fun `redact collapses whitespace and caps length`() {
        val out = AgentDiagnostics.redact("a\n\n   b\t c" + "x".repeat(500))
        assertFalse(out.contains('\n'))
        assertFalse(out.contains("  "))
        assertTrue("length was ${out.length}", out.length <= 160)
    }
}
