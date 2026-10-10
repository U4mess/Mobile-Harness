package com.jarves.mh.runtime

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resumable, optionally parallel HTTP downloader for runtime bundles.
 *
 * The phones this targets sit behind release-asset CDNs that cap one TCP connection far
 * below what the link can carry, but scale well across connections: a measured 4x jump
 * from one to four ranged connections on the same artifact. Large, size-known artifacts
 * are therefore fetched as up to [MAX_SEGMENTS] ranges.
 *
 * Every segment re-checks its own `206`, exact `Content-Range`, total size and byte
 * count. Any inconsistency abandons the ranged transfer in favour of the plain
 * single-connection path instead of trusting bytes that cannot be attributed to the
 * artifact, and validators (`ETag`/`Last-Modified`) are carried through `If-Range` so a
 * changed artifact is detected rather than spliced together.
 */
internal class HttpSegment(
    val status: Int,
    val contentLength: Long,
    val contentRange: String?,
    val entityTag: String?,
    val lastModified: String?,
    val body: InputStream,
    private val onClose: () -> Unit,
) {
    /** Releases the stream and the underlying connection. */
    fun close() {
        runCatching { body.close() }
        runCatching { onClose() }
    }
}

/** Opens one HTTP response. Ranges are inclusive; [ifRange] carries the validator. */
internal fun interface HttpGet {
    fun open(url: String, range: LongRange?, ifRange: String?): HttpSegment
}

internal class HttpUrlConnectionGet : HttpGet {
    override fun open(url: String, range: LongRange?, ifRange: String?): HttpSegment {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (range != null) connection.setRequestProperty("Range", "bytes=${range.first}-${range.last}")
        if (ifRange != null) connection.setRequestProperty("If-Range", ifRange)
        val status = connection.responseCode
        return HttpSegment(
            status = status,
            contentLength = connection.contentLengthLong,
            contentRange = connection.getHeaderField("Content-Range"),
            entityTag = connection.getHeaderField("ETag"),
            lastModified = connection.getHeaderField("Last-Modified"),
            body = if (status in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream,
            onClose = { runCatching { connection.disconnect() } },
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 120_000
    }
}

internal data class ContentRange(val start: Long, val endInclusive: Long, val total: Long)

/** Parses `bytes 0-1023/569652007`, or null when the header is absent or unusable. */
internal fun parseContentRange(value: String?): ContentRange? {
    val match = CONTENT_RANGE.find(value?.trim().orEmpty()) ?: return null
    return ContentRange(
        start = match.groupValues[1].toLong(),
        endInclusive = match.groupValues[2].toLong(),
        total = match.groupValues[3].toLong(),
    )
}

private val CONTENT_RANGE = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+)$")

/** A ranged transfer that cannot be trusted; the caller falls back to one connection. */
internal class RangeInconsistentException(message: String) : IOException(message)

/** The finished artifact failed its pinned checksum. Never retried. */
internal class ChecksumMismatchException(message: String) : IOException(message)

/**
 * A non-2xx response. Only 408, 429 and 5xx are worth another attempt; a 404 or 403 will
 * answer the same way forever, so retrying it just delays the failure by the backoff total.
 */
internal class HttpFailureException(val status: Int, message: String) : IOException(message) {
    val transient: Boolean get() = status == 408 || status == 429 || status in 500..599
}

internal data class SegmentState(val start: Long, val endInclusive: Long, val received: Long = 0L) {
    val length: Long get() = endInclusive - start + 1L
}

internal data class PartialState(
    val url: String,
    val totalSize: Long,
    /** Pinned checksum of the artifact this plan belongs to; part of its identity. */
    val checksum: String,
    val entityTag: String?,
    val lastModified: String?,
    val segments: List<SegmentState>,
) {
    val validator: String? get() = entityTag ?: lastModified

    fun toJson(): String = JSONObject()
        .put("url", url)
        .put("totalSize", totalSize)
        .put("checksum", checksum)
        .put("entityTag", entityTag ?: JSONObject.NULL)
        .put("lastModified", lastModified ?: JSONObject.NULL)
        .put(
            "segments",
            JSONArray().apply {
                segments.forEach { segment ->
                    put(
                        JSONObject()
                            .put("start", segment.start)
                            .put("end", segment.endInclusive)
                            .put("received", segment.received),
                    )
                }
            },
        )
        .toString()

    companion object {
        fun read(file: File): PartialState? = runCatching {
            val json = JSONObject(file.readText())
            val array = json.getJSONArray("segments")
            PartialState(
                url = json.getString("url"),
                totalSize = json.getLong("totalSize"),
                // A plan written without identity cannot be attributed, so it is not resumed.
                checksum = json.optString("checksum").orEmpty(),
                entityTag = json.optString("entityTag").takeIf { it.isNotBlank() && it != "null" },
                lastModified = json.optString("lastModified").takeIf { it.isNotBlank() && it != "null" },
                segments = (0 until array.length()).map { index ->
                    val entry = array.getJSONObject(index)
                    SegmentState(entry.getLong("start"), entry.getLong("end"), entry.getLong("received"))
                },
            )
        }.getOrNull()
    }
}

/**
 * Downloads [destination] from [url], resuming partial work and verifying the pinned
 * checksum before publishing.
 *
 * Segments are written in place into one preallocated `*.part` file, so disk use is
 * bounded by the artifact size and an interrupted transfer leaves a resume point instead
 * of a stack of temporary copies.
 *
 * Parallel segments live in `<dest>.part.segments` with `<dest>.part.segments.json`
 * metadata, strictly separate from the sequential `<dest>.part`. A preallocated segment
 * file therefore cannot masquerade as a resume prefix even when its metadata is lost: the
 * sequential path only reads `<dest>.part`, and the segmented path deletes its file when
 * the metadata that identifies it is missing.
 */
internal class RuntimeDownloader(
    private val http: HttpGet = HttpUrlConnectionGet(),
    private val digestOf: (File, String) -> String = { file, algorithm -> digest(file, algorithm) },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun download(
        url: String,
        destination: File,
        expectedChecksum: String,
        algorithm: String,
        expectedSize: Long?,
        onVerifying: suspend () -> Unit = {},
        onProgress: suspend (received: Long, total: Long) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        LOCK_FOR(destination).withLock {
            if (destination.isFile && digestOf(destination, algorithm).equals(expectedChecksum, ignoreCase = true)) {
                onProgress(destination.length(), destination.length())
                return@withLock
            }
            // Parallel data lives under its own names. A preallocated segment file can never be
            // mistaken for a legacy sequential prefix, because the legacy path only ever reads
            // `<dest>.part` and the segmented path refuses to run without its metadata.
            val legacyPart = File(destination.parentFile, "${destination.name}.part")
            val segmentedPart = File(destination.parentFile, "${destination.name}.part.segments")
            val segmentedMeta = File(destination.parentFile, "${destination.name}.part.segments.json")
            val source = try {
                val plan = resumePlan(url, segmentedPart, segmentedMeta, expectedSize, expectedChecksum)
                    ?: startParallelPlan(url, segmentedPart, segmentedMeta, expectedSize, expectedChecksum)
                if (plan != null) {
                    runSegments(url, segmentedPart, segmentedMeta, plan, onProgress)
                    segmentedPart
                } else {
                    segmentedPart.delete()
                    segmentedMeta.delete()
                    runSingle(url, legacyPart, expectedSize, onProgress)
                    legacyPart
                }
            } catch (inconsistent: RangeInconsistentException) {
                // Range support is absent or inconsistent: discard what we cannot attribute
                // and take the plain path rather than splice mismatched bytes together.
                segmentedPart.delete()
                segmentedMeta.delete()
                runSingle(url, legacyPart, expectedSize, onProgress)
                legacyPart
            }
            // Verification and extraction are separate phases with no byte progress; say so
            // rather than leaving the last download percentage on screen.
            onVerifying()
            publish(destination, source, legacyPart, segmentedMeta, expectedChecksum, algorithm, expectedSize)
        }
    }

    /** Reusable plan from a previous run, or null when none can be trusted. */
    private fun resumePlan(
        url: String,
        part: File,
        meta: File,
        expectedSize: Long?,
        expectedChecksum: String,
    ): PartialState? {
        if (!meta.isFile) {
            // Parallel data without its metadata cannot be attributed to any artifact, so it is
            // discarded here rather than being handed to the sequential path as a prefix.
            part.delete()
            return null
        }
        val state = PartialState.read(meta)
        val usable = state != null &&
            state.url == url &&
            // Identity is URL *and* pinned checksum: a plan for a different artifact must never
            // contribute bytes to this one, even when the size happens to match.
            state.checksum.equals(expectedChecksum, ignoreCase = true) &&
            (expectedSize == null || state.totalSize == expectedSize) &&
            state.segments.isNotEmpty() &&
            state.segments.all { it.length > 0L && it.received <= it.length } &&
            part.isFile &&
            part.length() == state.totalSize
        if (usable) return state
        // The plan no longer describes this file; its bytes are unattributable.
        meta.delete()
        part.delete()
        return null
    }

    /** Probes for range support and, when the artifact is large enough, lays out segments. */
    private suspend fun startParallelPlan(
        url: String,
        part: File,
        meta: File,
        expectedSize: Long?,
        expectedChecksum: String,
    ): PartialState? {
        val size = expectedSize ?: return null
        val segments = segmentPlan(size) ?: return null
        val response = try {
            http.open(url, 0L..0L, null)
        } catch (failure: IOException) {
            return null
        }
        try {
            if (response.status != HttpURLConnection.HTTP_PARTIAL) return null
            val contentRange = parseContentRange(response.contentRange) ?: return null
            if (contentRange.total != size) return null
            try {
                RandomAccessFile(part, "rw").use { it.setLength(size) }
            } catch (failure: IOException) {
                part.delete()
                return null
            }
            return PartialState(url, size, expectedChecksum, response.entityTag, response.lastModified, segments).also {
                persistPlan(meta, it)
            }
        } finally {
            response.close()
        }
    }

    private suspend fun runSegments(
        url: String,
        part: File,
        meta: File,
        plan: PartialState,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val total = plan.totalSize
        val received = AtomicLong(plan.segments.sumOf { it.received })
        val segments = plan.segments.toMutableList()
        val persistLock = Any()
        var lastPersistAt = 0L

        fun noteProgress(index: Int, value: Long, force: Boolean = false) {
            val now = System.currentTimeMillis()
            synchronized(persistLock) {
                segments[index] = segments[index].copy(received = value)
                if (force || now - lastPersistAt >= PERSIST_INTERVAL_MS) {
                    lastPersistAt = now
                    persistPlan(meta, plan.copy(segments = segments.toList()))
                }
            }
        }

        onProgress(received.get(), total)
        coroutineScope {
            segments.indices.map { index ->
                async(Dispatchers.IO) {
                    val done = fetchSegment(url, part, plan.segments[index], total, plan.validator) { delta, forSegment ->
                        // Only newly written bytes are counted, so retries never double-count.
                        onProgress(received.addAndGet(delta), total)
                        noteProgress(index, forSegment)
                    }
                    noteProgress(index, done, force = true)
                }
            }.awaitAll()
        }
    }

    private suspend fun fetchSegment(
        url: String,
        part: File,
        segment: SegmentState,
        totalSize: Long,
        validator: String?,
        onBytes: suspend (delta: Long, receivedForSegment: Long) -> Unit,
    ): Long {
        var received = segment.received
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            try {
                val from = segment.start + received
                val response = http.open(url, from..segment.endInclusive, validator)
                withPromptClose(response) {
                    if (response.status == HttpURLConnection.HTTP_OK) {
                        // A 200 answers a range request with the whole artifact; appending it
                        // to a partial segment would corrupt the file.
                        throw RangeInconsistentException("server ignored the range request")
                    }
                    if (response.status != HttpURLConnection.HTTP_PARTIAL) {
                        throw HttpFailureException(response.status, "Download failed with HTTP ${response.status}")
                    }
                    val contentRange = parseContentRange(response.contentRange)
                        ?: throw RangeInconsistentException("missing Content-Range")
                    if (contentRange.start != from ||
                        contentRange.endInclusive != segment.endInclusive ||
                        contentRange.total != totalSize
                    ) {
                        throw RangeInconsistentException("unexpected Content-Range ${response.contentRange}")
                    }
                    val served = response.entityTag ?: response.lastModified
                    if (validator != null && served != null && served != validator) {
                        throw RangeInconsistentException("artifact changed while downloading")
                    }
                    RandomAccessFile(part, "rw").use { file ->
                        file.seek(from)
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = response.body.read(buffer)
                            if (count < 0) break
                            file.write(buffer, 0, count)
                            received += count
                            onBytes(count.toLong(), received)
                        }
                    }
                }
                if (received != segment.length) {
                    throw IOException("segment truncated at $received of ${segment.length} bytes")
                }
                return received
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (inconsistent: RangeInconsistentException) {
                throw inconsistent
            } catch (failure: IOException) {
                if (!isRetryable(failure)) throw failure
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw failure
                sleeper(backoffMillis(attempt))
            }
        }
    }

    /**
     * One connection. Resumes a legacy `*.part` prefix when present, so partial downloads
     * written by earlier app versions keep working; a `200` where a `206` was expected
     * restarts the file instead of appending.
     */
    private suspend fun runSingle(
        url: String,
        part: File,
        expectedSize: Long?,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            var existing = part.takeIf(File::isFile)?.length() ?: 0L
            if (expectedSize != null && existing >= expectedSize) {
                // A file at or beyond the expected size is not a partial prefix: it is either a
                // preallocated parallel part whose metadata was lost, or stale from another
                // artifact. Treating it as a prefix would splice unrelated bytes together.
                part.delete()
                existing = 0L
            }
            try {
                val response = http.open(url, if (existing > 0L) existing..Long.MAX_VALUE else null, null)
                withPromptClose(response) {
                    if (response.status != HttpURLConnection.HTTP_PARTIAL && response.status != HttpURLConnection.HTTP_OK) {
                        throw HttpFailureException(response.status, "Download failed with HTTP ${response.status}")
                    }
                    val resuming = response.status == HttpURLConnection.HTTP_PARTIAL && existing > 0L
                    if (!resuming) existing = 0L
                    val total = response.contentLength.takeIf { it >= 0L }?.plus(existing)
                        ?: expectedSize
                        ?: -1L
                    onProgress(existing, total)
                    RandomAccessFile(part, "rw").use { file ->
                        if (resuming) {
                            file.seek(existing)
                        } else {
                            file.setLength(0L)
                        }
                        val buffer = ByteArray(BUFFER_BYTES)
                        var written = existing
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = response.body.read(buffer)
                            if (count < 0) break
                            file.write(buffer, 0, count)
                            written += count
                            onProgress(written, total)
                        }
                    }
                }
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (!isRetryable(failure)) throw failure
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw failure
                sleeper(backoffMillis(attempt))
            }
        }
    }

    /**
     * Only failures a later attempt could plausibly fix. Timeouts, resets, truncated bodies
     * and 408/429/5xx qualify; a 4xx that will answer identically forever does not.
     */
    private fun isRetryable(failure: IOException): Boolean = when (failure) {
        is ChecksumMismatchException -> false
        is RangeInconsistentException -> false
        is HttpFailureException -> failure.transient
        else -> true
    }

    /**
     * Runs [block] with [segment] while watching for cancellation.
     *
     * A read that is already blocked on a stalled socket never returns on its own, so the
     * `ensureActive()` checks between reads cannot unblock it and cancellation would wait out
     * the whole read timeout. Closing the connection from a watcher releases the read
     * immediately; the resulting failure unwinds through the usual cancellation path.
     */
    private suspend fun <T> withPromptClose(segment: HttpSegment, block: suspend () -> T): T {
        val watcher = currentCoroutineContext()[Job]?.let { parent ->
            CoroutineScope(parent + Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { segment.close() }
                }
            }
        }
        return try {
            block()
        } finally {
            watcher?.cancel()
            segment.close()
        }
    }

    private fun persistPlan(meta: File, state: PartialState) {
        runCatching {
            val staged = File(meta.parentFile, "${meta.name}.tmp")
            staged.writeText(state.toJson())
            if (!staged.renameTo(meta)) {
                meta.delete()
                staged.renameTo(meta)
            }
        }
    }

    /** Final gate: exact size, then the pinned checksum, before anything is published. */
    private fun publish(
        destination: File,
        source: File,
        legacyPart: File,
        segmentedMeta: File,
        expectedChecksum: String,
        algorithm: String,
        expectedSize: Long?,
    ) {
        val part = source
        if (!part.isFile) throw IOException("Download did not produce a file")
        if (expectedSize != null && part.length() != expectedSize) {
            val actual = part.length()
            part.delete()
            segmentedMeta.delete()
            legacyPart.delete()
            throw IOException("Downloaded $actual bytes, expected $expectedSize")
        }
        if (!digestOf(part, algorithm).equals(expectedChecksum, ignoreCase = true)) {
            // Wrong bytes, not a network hiccup: retrying the same artifact would be futile.
            part.delete()
            segmentedMeta.delete()
            throw ChecksumMismatchException("Downloaded file checksum did not match")
        }
        destination.delete()
        if (!part.renameTo(destination)) throw IOException("Could not finish download")
        segmentedMeta.delete()
        // A stale sequential part from an earlier interrupted attempt has no further use.
        legacyPart.delete()
    }

    private fun backoffMillis(attempt: Int): Long =
        (BASE_BACKOFF_MS shl (attempt - 1)).coerceAtMost(MAX_BACKOFF_MS)

    private fun LOCK_FOR(destination: File): Mutex =
        LOCKS.computeIfAbsent(destination.absolutePath) { Mutex() }

    companion object {
        /** Beyond this, extra connections add little and multiply the failure surface. */
        const val MAX_SEGMENTS = 4
        const val PARALLEL_MIN_BYTES = 8L * 1024 * 1024
        private const val MIN_SEGMENT_BYTES = 4L * 1024 * 1024
        private const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024 * 1024
        private const val BUFFER_BYTES = 128 * 1024
        private const val MAX_ATTEMPTS = 4
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 8_000L
        private const val PERSIST_INTERVAL_MS = 2_000L

        /** One download per destination process-wide, so retries cannot race each other. */
        private val LOCKS = ConcurrentHashMap<String, Mutex>()

        /**
         * Splits [totalSize] into at most [MAX_SEGMENTS] ranges, or null when a single
         * connection is the better choice. ponytail: one Mutex per destination path is
         * never evicted; the set is bounded by the number of distinct artifacts.
         */
        fun segmentPlan(totalSize: Long): List<SegmentState>? {
            if (totalSize < PARALLEL_MIN_BYTES || totalSize > MAX_DOWNLOAD_BYTES) return null
            val count = minOf(MAX_SEGMENTS.toLong(), totalSize / MIN_SEGMENT_BYTES).toInt()
            if (count < 2) return null
            val chunk = totalSize / count
            return (0 until count).map { index ->
                val start = index * chunk
                val end = if (index == count - 1) totalSize - 1L else start + chunk - 1L
                SegmentState(start, end)
            }
        }
    }
}
