package com.jarves.mh.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the resumable/parallel downloader, driven through a fake HTTP transport so
 * every range, validator and failure path is exercised without a network.
 *
 * The payload is deliberately larger than [RuntimeDownloader.PARALLEL_MIN_BYTES] so the
 * ranged plan is actually exercised; a smaller one would silently fall back to one
 * connection and the parallel assertions would pass for the wrong reason.
 */
class RuntimeDownloaderTest {

    private val payload = ByteArray(12 * 1024 * 1024) { (it % 251).toByte() }

    /** Serves [payload] with real range semantics unless configured to misbehave. */
    private inner class FakeServer(
        var ignoreRange: Boolean = false,
        var entityTag: String? = "\"v1\"",
        var lastModified: String? = "Mon, 01 Jan 2026 00:00:00 GMT",
        /** Lies about the total in `Content-Range` while still serving correct bytes. */
        var contentRangeTotalOverride: Long? = null,
        /** Truncates the body of the first response for each requested end offset, once. */
        var truncateOneResponseAt: Int? = null,
        val failFirstRequests: MutableSet<Int> = mutableSetOf(),
        val requests: MutableList<LongRange?> = mutableListOf(),
    ) : HttpGet {
        private val seen = AtomicInteger()
        private val truncatedEnds = mutableSetOf<Long>()

        override fun open(url: String, range: LongRange?, ifRange: String?): HttpSegment {
            requests += range
            val index = seen.incrementAndGet()
            if (index in failFirstRequests) throw IOException("simulated transient failure")
            val total = payload.size.toLong()
            if (range == null || ignoreRange) return segment(200, null, payload, total)

            val start = range.first.coerceIn(0L, total).toInt()
            val requestedEnd = if (range.last < 0L || range.last >= payload.size - 1L) {
                payload.size - 1
            } else {
                range.last.toInt()
            }
            if (start > requestedEnd) return segment(206, "bytes */$total", ByteArray(0), total)
            // A truncated transfer advertises the full range but delivers fewer bytes, so the
            // client sees a short read rather than a contradictory Content-Range.
            val bodyEnd = truncateOneResponseAt
                ?.takeIf { truncatedEnds.add(range.last) }
                ?.let { (start + it - 1).coerceAtMost(requestedEnd) }
                ?: requestedEnd
            return segment(206, "bytes $start-$requestedEnd/${contentRangeTotalOverride ?: total}", slice(start, bodyEnd), total)
        }

        private fun segment(status: Int, contentRange: String?, body: ByteArray, advertised: Long) =
            HttpSegment(
                status = status,
                contentLength = if (status == 200) advertised else body.size.toLong(),
                contentRange = contentRange,
                entityTag = entityTag,
                lastModified = lastModified,
                body = ByteArrayInputStream(body) as InputStream,
                onClose = {},
            )

        private fun slice(start: Int, endInclusive: Int): ByteArray = payload.copyOfRange(start, endInclusive + 1)
    }

    private fun tempDir(): File = Files.createTempDirectory("mh-dl").toFile().apply { deleteOnExit() }

    private fun downloader(server: HttpGet, sha: String = "unused", sleep: suspend (Long) -> Unit = {}) =
        RuntimeDownloader(
            http = server,
            digestOf = { file, _ -> if (file.isFile) sha else "" },
            sleeper = sleep,
        )

    private fun run(
        server: HttpGet,
        dir: File,
        expectedSize: Long?,
        expectedSha: String = "unused",
        sha: String = "unused",
        sleep: suspend (Long) -> Unit = {},
    ): File = runBlocking {
        val destination = File(dir, "artifact.bin")
        downloader(server, sha, sleep).download(
            url = "https://example.test/artifact",
            destination = destination,
            expectedChecksum = expectedSha,
            algorithm = "SHA-256",
            expectedSize = expectedSize,
            onProgress = { _, _ -> },
        )
        destination
    }

    /** Writes a plan plus a preallocated part file, as an interrupted parallel run would leave. */
    private fun seedPlan(dir: File, url: String = "https://example.test/artifact", entityTag: String? = "\"v1\"") {
        val part = File(dir, "artifact.bin.part.segments")
        part.parentFile?.mkdirs()
        java.io.RandomAccessFile(part, "rw").use { it.setLength(payload.size.toLong()) }
        val plan = PartialState(
            url = url,
            totalSize = payload.size.toLong(),
            checksum = "unused",
            entityTag = entityTag,
            lastModified = null,
            segments = RuntimeDownloader.segmentPlan(payload.size.toLong())!!,
        )
        File(dir, "artifact.bin.part.segments.json").writeText(plan.toJson())
    }

    // -- Valid segments and successful assembly -------------------------------

    @Test
    fun `parallel segments assemble into the exact artifact`() {
        val dir = tempDir()
        val server = FakeServer()

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertFalse(File(dir, "artifact.bin.part").exists())
        assertFalse(File(dir, "artifact.bin.part.segments").exists())
        assertFalse(File(dir, "artifact.bin.part.segments.json").exists())
        // One probe plus one request per segment, all carrying a range.
        assertTrue("expected ranged requests", server.requests.count { it != null } >= 3)
    }

    @Test
    fun `a single connection is used when the size is unknown`() {
        val dir = tempDir()
        val server = FakeServer()

        val artifact = run(server, dir, expectedSize = null)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertEquals(1, server.requests.size)
        assertEquals(null, server.requests.single())
    }

    @Test
    fun `an already valid destination is not downloaded again`() {
        val dir = tempDir()
        val destination = File(dir, "artifact.bin").apply { writeBytes(payload) }
        val server = FakeServer()

        runBlocking {
            downloader(server, sha = "match").download(
                "https://example.test/artifact", destination, "match", "SHA-256", payload.size.toLong(),
            ) { _, _ -> }
        }

        assertTrue(server.requests.isEmpty())
    }

    // -- Ignored Range / HTTP 200 ---------------------------------------------

    @Test
    fun `an ignored Range falls back to one connection instead of splicing`() {
        val dir = tempDir()
        val server = FakeServer(ignoreRange = true)

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
        // The probe saw 200, so the plan was declined and the transfer was a plain GET.
        assertTrue(server.requests.any { it == null })
    }

    @Test
    fun `a 200 answering a range request never appends to a partial segment`() {
        val dir = tempDir()
        seedPlan(dir)
        val server = FakeServer(ignoreRange = true)

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        // The unusable plan was discarded and the plain path produced the correct bytes.
        assertTrue(artifact.readBytes().contentEquals(payload))
        assertFalse(File(dir, "artifact.bin.part.segments.json").exists())
    }

    // -- Wrong Content-Range and truncated segments ---------------------------

    @Test
    fun `a wrong Content-Range total is rejected and the plain path is used`() {
        val dir = tempDir()
        val server = FakeServer(contentRangeTotalOverride = payload.size.toLong() + 4096L)

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue("expected the single-connection fallback", server.requests.any { it == null })
    }

    @Test
    fun `a truncated segment is retried and never accepted as complete`() {
        val dir = tempDir()
        val server = FakeServer(truncateOneResponseAt = 4096)

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
        // Each segment needed at least one retry after its truncated first response.
        assertTrue("expected extra attempts", server.requests.count { it != null } > RuntimeDownloader.MAX_SEGMENTS)
    }

    // -- Transient failure, resume and cancellation ---------------------------

    @Test
    fun `transient connection failures are retried with backoff`() {
        val dir = tempDir()
        val server = FakeServer(failFirstRequests = mutableSetOf(1, 2))
        val sleeps = mutableListOf<Long>()

        val artifact = run(server, dir, expectedSize = payload.size.toLong(), sleep = { sleeps += it })

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue("expected backoff between attempts", sleeps.isNotEmpty())
        assertEquals("backoff must grow", sleeps.sorted(), sleeps)
    }

    @Test
    fun `an existing legacy partial download is resumed, not restarted`() {
        val dir = tempDir()
        val half = payload.size / 2
        File(dir, "artifact.bin.part").writeBytes(payload.copyOfRange(0, half))
        val server = FakeServer()

        val artifact = run(server, dir, expectedSize = null)

        assertTrue(artifact.readBytes().contentEquals(payload))
        // The resume request starts exactly at the existing prefix.
        assertEquals(half.toLong(), server.requests.first()!!.first)
    }

    @Test
    fun `a stale plan with a mismatched url is discarded, not trusted`() {
        val dir = tempDir()
        seedPlan(dir, url = "https://example.test/other-artifact")
        val server = FakeServer()

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
    }

    @Test
    fun `a stale plan whose part file is the wrong size is discarded`() {
        val dir = tempDir()
        seedPlan(dir)
        // Truncate the part so it no longer matches the recorded total.
        java.io.RandomAccessFile(File(dir, "artifact.bin.part.segments"), "rw").use { it.setLength(1024L) }
        val server = FakeServer()

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
    }

    /** Cancellation must never be retried. */
    @Test
    fun `cancellation propagates instead of being retried`() {
        val dir = tempDir()
        var attempts = 0
        val cancelling = HttpGet { _, _, _ ->
            attempts++
            throw CancellationException("user cancelled")
        }
        val thrown = runCatching {
            runBlocking {
                downloader(cancelling).download(
                    "https://example.test/artifact", File(dir, "artifact.bin"), "unused", "SHA-256", null,
                ) { _, _ -> }
            }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(1, attempts)
    }

    @Test
    fun `cancelling the caller stops the download`() {
        val dir = tempDir()
        val server = FakeServer()

        runBlocking {
            val job = launch {
                downloader(server).download(
                    "https://example.test/artifact", File(dir, "artifact.bin"), "unused", "SHA-256",
                    payload.size.toLong(),
                ) { _, _ -> yield() }
            }
            yield()
            job.cancel()
            job.join()
        }

        assertTrue(server.requests.isNotEmpty())
        assertFalse("a cancelled download must not publish", File(dir, "artifact.bin").exists())
    }

    // -- Changed artifact validators ------------------------------------------

    @Test
    fun `a changed validator mid-transfer abandons the ranged plan`() {
        val dir = tempDir()
        seedPlan(dir, entityTag = "\"old\"")
        // The server now advertises a different validator and ignores If-Range.
        val server = FakeServer(ignoreRange = true, entityTag = "\"new\"")

        val artifact = run(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
    }

    // -- Final checksum mismatch ----------------------------------------------

    @Test
    fun `a final checksum mismatch fails without retrying and leaves no partial behind`() {
        val dir = tempDir()
        val server = FakeServer()
        val destination = File(dir, "artifact.bin")

        val thrown = runCatching {
            runBlocking {
                RuntimeDownloader(http = server, digestOf = { _, _ -> "actual-digest" }, sleeper = {}).download(
                    "https://example.test/artifact", destination, "expected-digest", "SHA-256",
                    payload.size.toLong(),
                ) { _, _ -> }
            }
        }.exceptionOrNull()

        assertTrue("expected a checksum failure, got $thrown", thrown is ChecksumMismatchException)
        assertFalse("a rejected artifact must not be published", destination.exists())
        assertFalse(File(dir, "artifact.bin.part").exists())
        assertFalse(File(dir, "artifact.bin.part.segments").exists())
        assertFalse(File(dir, "artifact.bin.part.segments.json").exists())
    }

    @Test
    fun `a size mismatch is rejected before publication`() {
        val dir = tempDir()
        val server = FakeServer()
        val destination = File(dir, "artifact.bin")

        val thrown = runCatching {
            runBlocking {
                downloader(server).download(
                    "https://example.test/artifact", destination, "unused", "SHA-256",
                    expectedSize = payload.size.toLong() + 999L,
                ) { _, _ -> }
            }
        }.exceptionOrNull()

        assertNotNull(thrown)
        assertFalse(destination.exists())
    }

    // -- Segment planning -----------------------------------------------------

    @Test
    fun `segment plans stay within the connection cap and cover the whole artifact`() {
        listOf(9L * 1024 * 1024, 64L * 1024 * 1024, 569_652_007L).forEach { size ->
            val plan = RuntimeDownloader.segmentPlan(size)
            assertNotNull("expected a parallel plan for $size", plan)
            assertTrue(plan!!.size <= RuntimeDownloader.MAX_SEGMENTS)
            assertEquals(0L, plan.first().start)
            assertEquals(size - 1L, plan.last().endInclusive)
            plan.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive + 1L, b.start) }
        }
        assertEquals(null, RuntimeDownloader.segmentPlan(1024L))
        assertEquals(null, RuntimeDownloader.segmentPlan(9L * 1024 * 1024 * 1024))
    }

    @Test
    fun `concurrent downloads of one destination do not interleave`() {
        val dir = tempDir()
        val server = FakeServer()
        val destination = File(dir, "artifact.bin")

        runBlocking {
            val first = launch {
                downloader(server).download(
                    "https://example.test/artifact", destination, "unused", "SHA-256", payload.size.toLong(),
                ) { _, _ -> }
            }
            val second = launch {
                downloader(server).download(
                    "https://example.test/artifact", destination, "unused", "SHA-256", payload.size.toLong(),
                ) { _, _ -> }
            }
            first.join()
            second.join()
        }

        assertTrue(destination.readBytes().contentEquals(payload))
        assertFalse(File(dir, "artifact.bin.part").exists())
    }
}
