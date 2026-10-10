package com.jarves.mh.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-case review of the downloader: metadata trust, resume precision, fallback exclusion,
 * prompt cancellation and retry classification. Complements [RuntimeDownloaderTest], which
 * covers the happy paths and the basic range/validator rejections.
 */
class RuntimeDownloaderEdgeCaseTest {

    private val payload = ByteArray(12 * 1024 * 1024) { (it % 251).toByte() }
    private val checksum = "pinned-checksum"
    private val url = "https://example.test/artifact"

    /** Serves [payload] with scripted faults and records concurrency. */
    private inner class ScriptedServer(
        val inconsistentStarts: MutableSet<Long> = mutableSetOf(),
        /** Applies to every request, ranged or not. */
        val statusOverride: Int? = null,
        /** The request for this start blocks in `read` until closed. */
        val blockingStart: Long? = null,
    ) : HttpGet {
        private val concurrent = AtomicInteger()
        val openCount = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        val rangedRequests = mutableListOf<LongRange>()
        val concurrentAtFallback = AtomicInteger(-1)
        val blockingStarted = AtomicBoolean(false)
        val blocking = BlockingStream()

        override fun open(url: String, range: LongRange?, ifRange: String?): HttpSegment {
            openCount.incrementAndGet()
            concurrent.incrementAndGet().also { maxConcurrent.updateAndGet { max -> maxOf(max, it) } }
            val total = payload.size.toLong()
            // Closing may happen twice (watcher plus owner); count it once.
            val counted = AtomicBoolean(false)
            val release: () -> Unit = { if (counted.compareAndSet(false, true)) concurrent.decrementAndGet() }
            if (statusOverride != null) {
                return HttpSegment(statusOverride, 0L, null, "\"v1\"", null, ByteArrayInputStream(ByteArray(0)), release)
            }
            if (range == null) {
                // The single-connection fallback: nothing else may still be writing.
                concurrentAtFallback.set(concurrent.get())
                return HttpSegment(200, total, null, "\"v1\"", null, ByteArrayInputStream(payload), release)
            }
            rangedRequests += range
            if (range.first == blockingStart) {
                blockingStarted.set(true)
                return HttpSegment(206, 0L, "bytes ${range.first}-${range.last}/$total", "\"v1\"", null, blocking, release)
            }
            val start = range.first.toInt()
            val end = range.last.toInt().coerceAtMost(payload.size - 1)
            val contentRange = if (range.first in inconsistentStarts) {
                "bytes $start-${end - 1}/$total" // deliberately disagrees with the request
            } else {
                "bytes $start-$end/$total"
            }
            return HttpSegment(
                206,
                (end - start + 1).toLong(),
                contentRange,
                "\"v1\"",
                null,
                ByteArrayInputStream(payload, start, end - start + 1),
                release,
            )
        }
    }

    /** Blocks in `read` until closed, modelling a stalled socket. */
    private class BlockingStream : InputStream() {
        private val released = CountDownLatch(1)
        val wasClosed = AtomicBoolean(false)
        override fun read(): Int = block()
        override fun read(b: ByteArray, off: Int, len: Int): Int = block()
        private fun block(): Int {
            released.await()
            throw IOException("connection closed")
        }
        override fun close() {
            wasClosed.set(true)
            released.countDown()
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("mh-edge").toFile().apply { deleteOnExit() }

    private fun downloader(server: HttpGet, sha: String = checksum, sleeper: suspend (Long) -> Unit = {}) =
        RuntimeDownloader(http = server, digestOf = { file, _ -> if (file.isFile) sha else "" }, sleeper = sleeper)

    private fun seedPlan(
        dir: File,
        planChecksum: String = checksum,
        planUrl: String = url,
        received: List<Long>? = null,
        writePlanFile: Boolean = true,
    ) {
        val part = File(dir, "artifact.bin.part.segments")
        part.parentFile?.mkdirs()
        val base = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        java.io.RandomAccessFile(part, "rw").use { file ->
            file.setLength(payload.size.toLong())
            if (received != null) {
                // A genuine interruption leaves the confirmed prefix of each segment on disk.
                base.forEachIndexed { index, segment ->
                    val count = received[index].toInt()
                    if (count > 0) {
                        file.seek(segment.start)
                        file.write(payload, segment.start.toInt(), count)
                    }
                }
            }
        }
        if (!writePlanFile) return
        val segments = if (received == null) base else base.mapIndexed { i, s -> s.copy(received = received[i]) }
        File(dir, "artifact.bin.part.segments.json")
            .writeText(PartialState(planUrl, payload.size.toLong(), planChecksum, "\"v1\"", null, segments).toJson())
    }

    private fun runDownload(server: HttpGet, dir: File, expectedSize: Long? = payload.size.toLong()): File =
        runBlocking {
            val destination = File(dir, "artifact.bin")
            downloader(server).download(url, destination, checksum, "SHA-256", expectedSize) { _, _ -> }
            destination
        }

    // -- 1. Preallocated part with missing or corrupt metadata ----------------

    @Test
    fun `a preallocated part with no metadata is never treated as a legacy prefix`() {
        val dir = tempDir()
        seedPlan(dir, writePlanFile = false) // full-size part, metadata gone
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        // The preallocated file is all zeros; resuming from its end would splice zeros in.
        assertTrue(
            "no request may start at or beyond the artifact size, saw ${server.rangedRequests.map { it.first }}",
            server.rangedRequests.none { it.first >= payload.size },
        )
    }

    @Test
    fun `a part at the expected size is discarded rather than resumed as a prefix`() {
        val dir = tempDir()
        val server = ScriptedServer()

        // Unknown size, so no parallel plan is possible and the legacy path is taken directly.
        val artifact = runDownload(server, dir, expectedSize = null)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue(server.rangedRequests.isEmpty())
    }

    @Test
    fun `a lost-metadata parallel file cannot become a legacy prefix when the size is unknown`() {
        val dir = tempDir()
        // A parallel attempt left a full-size preallocated segment file; its metadata is gone,
        // and this caller does not know the artifact size, so the old size guard cannot help.
        File(dir, "artifact.bin.part.segments").apply {
            parentFile?.mkdirs()
            java.io.RandomAccessFile(this, "rw").use { it.setLength(payload.size.toLong()) }
        }
        val server = ScriptedServer()

        val artifact = runDownload(server, dir, expectedSize = null)

        assertTrue(artifact.readBytes().contentEquals(payload))
        // Nothing may resume from the orphaned file: this was a plain sequential transfer.
        assertTrue("expected no ranged request, saw ${server.rangedRequests}", server.rangedRequests.isEmpty())
        assertFalse(File(dir, "artifact.bin.part.segments").exists())
        assertFalse(File(dir, "artifact.bin.part").exists())
    }

    @Test
    fun `a lost-metadata parallel file is not reused even when the size is known`() {
        val dir = tempDir()
        File(dir, "artifact.bin.part.segments").apply {
            parentFile?.mkdirs()
            java.io.RandomAccessFile(this, "rw").use { it.setLength(payload.size.toLong()) }
        }
        val server = ScriptedServer()

        val artifact = runDownload(server, dir, expectedSize = payload.size.toLong())

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue(server.rangedRequests.none { it.first >= payload.size })
    }

    @Test
    fun `corrupt metadata is rejected and contributes no bytes`() {
        val dir = tempDir()
        File(dir, "artifact.bin.part.segments").apply { parentFile?.mkdirs(); writeBytes(ByteArray(1024)) }
        File(dir, "artifact.bin.part.segments.json").writeText("{ this is not json")
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
    }

    @Test
    fun `metadata for another artifact is rejected even when the size matches`() {
        val dir = tempDir()
        seedPlan(dir, planChecksum = "a-different-artifact")
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue("the unusable plan must restart from zero", server.rangedRequests.any { it.first == 0L })
    }

    // -- 2. Restart resumes only confirmed segment bytes ----------------------

    @Test
    fun `resume starts exactly at the confirmed bytes of each segment`() {
        val dir = tempDir()
        val plan = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        val confirmed = plan.map { it.length / 4 }
        seedPlan(dir, received = confirmed)
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        plan.forEachIndexed { index, segment ->
            val expectedStart = segment.start + confirmed[index]
            assertTrue(
                "segment $index should resume at $expectedStart, saw ${server.rangedRequests.map { it.first }}",
                server.rangedRequests.any { it.first == expectedStart },
            )
        }
    }

    // -- 3. Metadata cannot claim bytes that were not safely written ----------

    @Test
    fun `every byte claimed by resume metadata is genuinely present in the part file`() {
        val dir = tempDir()
        val plan = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        // The last segment stalls so the run is interrupted with earlier segments confirmed.
        val server = ScriptedServer(blockingStart = plan.last().start)

        runBlocking {
            val job = launch(Dispatchers.IO) {
                runCatching {
                    downloader(server).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", payload.size.toLong()) { _, _ -> }
                }
            }
            // Bounded: a mis-built fixture must fail rather than spin forever. Wait for the
            // stalled request *and* for at least one confirmed byte, so the metadata check
            // below has something to verify.
            withTimeout(20_000) {
                while (true) {
                    val state = PartialState.read(File(dir, "artifact.bin.part.segments.json"))
                    if (server.blockingStarted.get() && (state?.segments?.sumOf { it.received } ?: 0L) > 0L) break
                    delay(20)
                }
            }
            job.cancel()
            withTimeout(5_000) { job.join() }
        }

        val part = File(dir, "artifact.bin.part.segments")
        val meta = File(dir, "artifact.bin.part.segments.json")
        assertTrue("a plan should have been persisted", meta.isFile)

        val state = PartialState.read(meta)!!
        val bytes = part.readBytes()
        var confirmedTotal = 0L
        state.segments.forEach { segment ->
            assertTrue("claimed ${segment.received} exceeds segment length", segment.received <= segment.length)
            if (segment.received > 0L) {
                val claimed = bytes.copyOfRange(segment.start.toInt(), (segment.start + segment.received).toInt())
                val expected = payload.copyOfRange(segment.start.toInt(), (segment.start + segment.received).toInt())
                assertTrue("claimed bytes at ${segment.start} are not the artifact's", claimed.contentEquals(expected))
            }
            confirmedTotal += segment.received
        }
        assertTrue("the run should have confirmed some bytes", confirmedTotal > 0L)
    }

    // -- 4. Fallback excludes concurrent segment workers ----------------------

    @Test
    fun `concurrency never exceeds the segment cap`() {
        val dir = tempDir()
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue(
            "saw ${server.maxConcurrent.get()} concurrent requests",
            server.maxConcurrent.get() <= RuntimeDownloader.MAX_SEGMENTS,
        )
    }

    @Test
    fun `the fallback request never overlaps a live segment worker`() {
        val dir = tempDir()
        val plan = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        val server = ScriptedServer(inconsistentStarts = mutableSetOf(plan.last().start))

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertEquals(
            "the single-connection fallback must be the only request in flight",
            1,
            server.concurrentAtFallback.get(),
        )
    }

    // -- 5. Cancellation closes active connections promptly -------------------

    @Test
    fun `cancellation releases a blocked read without waiting for the socket timeout`() {
        val dir = tempDir()
        val plan = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        val server = ScriptedServer(blockingStart = plan.last().start)
        val destination = File(dir, "artifact.bin")

        val elapsed = runBlocking {
            val started = System.currentTimeMillis()
            val job = launch(Dispatchers.IO) {
                runCatching {
                    downloader(server).download(url, destination, checksum, "SHA-256", payload.size.toLong()) { _, _ -> }
                }
            }
            withTimeout(10_000) { while (!server.blockingStarted.get()) yield() }
            job.cancel()
            withTimeout(5_000) { job.join() }
            System.currentTimeMillis() - started
        }

        assertTrue("cancellation took ${elapsed}ms", elapsed < 5_000)
        assertTrue("the stalled connection must be closed by the watcher", server.blocking.wasClosed.get())
        assertFalse("a cancelled download must not publish", destination.exists())
    }

    // -- 6. Artifact identity in resume metadata ------------------------------

    @Test
    fun `metadata round-trips the pinned checksum`() {
        val dir = tempDir()
        val meta = File(dir, "artifact.bin.part.segments.json")
        meta.writeText(
            PartialState(url, 1234L, "abc123", "\"v1\"", null, listOf(SegmentState(0L, 1233L, 10L))).toJson(),
        )

        assertEquals("abc123", PartialState.read(meta)!!.checksum)
    }

    @Test
    fun `a plan persisted without a checksum is not resumed`() {
        val dir = tempDir()
        seedPlan(dir, writePlanFile = false)
        // Shape of a plan from a build that predates checksum identity.
        File(dir, "artifact.bin.part.segments.json").writeText(
            """{"url":"$url","totalSize":${payload.size},"entityTag":"\"v1\"","lastModified":null,""" +
                """"segments":[{"start":0,"end":${payload.size - 1},"received":1024}]}""",
        )
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertTrue("an unidentifiable plan must restart from zero", server.rangedRequests.any { it.first == 0L })
    }

    // -- 7. Only appropriate transient failures are retried -------------------

    @Test
    fun `a permanent 404 is attempted exactly once`() {
        val dir = tempDir()
        val server = ScriptedServer(statusOverride = 404)
        val destination = File(dir, "artifact.bin")

        val thrown = runCatching {
            runBlocking { downloader(server).download(url, destination, checksum, "SHA-256", null) { _, _ -> } }
        }.exceptionOrNull()

        assertTrue("expected an HTTP failure, got $thrown", thrown is HttpFailureException)
        assertEquals(404, (thrown as HttpFailureException).status)
        assertFalse((thrown as HttpFailureException).transient)
    }

    @Test
    fun `a 403 is likewise not retried`() {
        val dir = tempDir()
        val server = ScriptedServer(statusOverride = 403)
        val thrown = runCatching {
            runBlocking {
                downloader(server).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", null) { _, _ -> }
            }
        }.exceptionOrNull()

        assertTrue(thrown is HttpFailureException)
        assertFalse((thrown as HttpFailureException).transient)
    }

    @Test
    fun `a 503 exhausts the attempt budget instead of failing on the first try`() {
        val dir = tempDir()
        val server = ScriptedServer(statusOverride = 503)
        val attempts = AtomicInteger()
        val counting = HttpGet { url, range, ifRange ->
            attempts.incrementAndGet()
            server.open(url, range, ifRange)
        }

        runCatching {
            runBlocking {
                downloader(counting).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", null) { _, _ -> }
            }
        }

        assertEquals("a transient 5xx should use the whole budget", 4, attempts.get())
    }

    @Test
    fun `a 429 is treated as transient`() {
        val dir = tempDir()
        val server = ScriptedServer(statusOverride = 429)
        val attempts = AtomicInteger()
        val counting = HttpGet { u, r, i -> attempts.incrementAndGet(); server.open(u, r, i) }

        runCatching {
            runBlocking {
                downloader(counting).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", null) { _, _ -> }
            }
        }

        assertTrue(attempts.get() > 1)
    }

    @Test
    fun `a checksum mismatch runs the gate once and never retries`() {
        val dir = tempDir()
        val server = ScriptedServer()
        var digestCalls = 0

        val thrown = runCatching {
            runBlocking {
                RuntimeDownloader(
                    http = server,
                    digestOf = { _, _ -> digestCalls++; "wrong" },
                    sleeper = {},
                ).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", payload.size.toLong()) { _, _ -> }
            }
        }.exceptionOrNull()

        assertTrue(thrown is ChecksumMismatchException)
        assertEquals(1, digestCalls)
    }

    @Test
    fun `range inconsistency is not retried but handed straight to the fallback`() {
        val dir = tempDir()
        val plan = RuntimeDownloader.segmentPlan(payload.size.toLong())!!
        val server = ScriptedServer(inconsistentStarts = mutableSetOf(plan.first().start))

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertEquals(
            "an inconsistent range must not be retried",
            1,
            server.rangedRequests.count { it.first == plan.first().start },
        )
    }

    @Test
    fun `a plain connection reset uses the whole attempt budget`() {
        val dir = tempDir()
        val attempts = AtomicInteger()
        val failing = HttpGet { _, _, _ -> attempts.incrementAndGet(); throw IOException("connection reset") }

        runCatching {
            runBlocking {
                downloader(failing).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", null) { _, _ -> }
            }
        }

        assertEquals(4, attempts.get())
    }

    @Test
    fun `cancellation during a retry backoff does not hang`() {
        val dir = tempDir()
        val server = ScriptedServer(statusOverride = 503)

        runBlocking {
            val job = launch(Dispatchers.IO) {
                runCatching {
                    downloader(server).download(url, File(dir, "artifact.bin"), checksum, "SHA-256", null) { _, _ -> }
                }
            }
            while (server.openCount.get() == 0) yield()
            job.cancel()
            // A hang here fails the test via the timeout.
            withTimeout(5_000) { job.join() }
        }
    }

    @Test
    fun `a completed download is not disturbed by the cancellation watcher`() {
        val dir = tempDir()
        val server = ScriptedServer()

        val artifact = runDownload(server, dir)

        assertTrue(artifact.readBytes().contentEquals(payload))
        assertFalse(File(dir, "artifact.bin.part").exists())
        assertFalse(File(dir, "artifact.bin.part.segments.json").exists())
        assertNotNull(artifact)
    }
}
