package com.jarves.mh.runtime

import com.jarves.mh.BuildConfig
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edition rules for runtime bundle delivery, plus the checksum gate that every
 * downloaded bundle must pass before it can be promoted to a runtime overlay.
 */
class RuntimeBundleEditionTest {

    // ── Edition selection ────────────────────────────────────────────────────

    @Test
    fun `online edition downloads instead of using embedded assets`() {
        assertFalse(useEmbeddedRuntimeBundle(offlineRuntimeBundles = false))
    }

    @Test
    fun `offline edition requires the embedded bundle`() {
        assertTrue(useEmbeddedRuntimeBundle(offlineRuntimeBundles = true))
    }

    @Test
    fun `an explicit embedded request still wins`() {
        assertTrue(useEmbeddedRuntimeBundle(offlineRuntimeBundles = false, preferEmbedded = true))
    }

    /**
     * This suite runs against the online build variant, so the production call
     * site (`useEmbeddedRuntimeBundle(BuildConfig.OFFLINE_RUNTIME_BUNDLES, …)`)
     * must select the download path here. That is what lets an online build
     * install Antigravity CLI without an embedded bundle.
     */
    @Test
    fun `the online debug variant is wired to the download path`() {
        assertFalse(BuildConfig.OFFLINE_RUNTIME_BUNDLES)
        assertFalse(useEmbeddedRuntimeBundle(BuildConfig.OFFLINE_RUNTIME_BUNDLES))
    }

    // ── Checksum gate ────────────────────────────────────────────────────────

    @Test
    fun `checksum mismatch deletes the file and fails`() {
        val file = tempFileWith("payload")

        val error = runCatching { verifyChecksumOrFail(file, "0".repeat(64)) }.exceptionOrNull()

        assertEquals("Downloaded file checksum did not match", error?.message)
        assertFalse("a rejected download must not survive on disk", file.exists())
    }

    @Test
    fun `a matching checksum keeps the file`() {
        val file = tempFileWith("payload")

        verifyChecksumOrFail(file, sha256Hex(file.readBytes()))

        assertTrue(file.exists())
    }

    @Test
    fun `checksum comparison ignores case`() {
        val file = tempFileWith("payload")

        verifyChecksumOrFail(file, sha256Hex(file.readBytes()).uppercase())

        assertTrue(file.exists())
    }

    /** The Antigravity updater verifies its download with SHA-512. */
    @Test
    fun `sha-512 digests are supported`() {
        val file = tempFileWith("payload")
        val expected = MessageDigest.getInstance("SHA-512")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

        verifyChecksumOrFail(file, expected, algorithm = "SHA-512")
        assertTrue(file.exists())
    }

    @Test
    fun `digest matches the platform implementation`() {
        val file = tempFileWith("Mobile Harness")

        assertEquals(sha256Hex(file.readBytes()), digest(file, "SHA-256"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Computed independently of [digest] so a broken digest cannot pass these tests. */
    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tempFileWith(content: String): File {
        val directory = Files.createTempDirectory("mh-bundle-test").toFile()
        directory.deleteOnExit()
        return File(directory, "bundle.tar.zst").apply {
            deleteOnExit()
            writeText(content)
        }
    }
}
