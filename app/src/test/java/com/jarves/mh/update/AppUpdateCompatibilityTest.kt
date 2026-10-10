package com.jarves.mh.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Updater compatibility, cleanup and suppression rules.
 *
 * Everything asserted here is the same production code the updater calls; the pure
 * decision functions are deliberately free of `PackageManager` so they can be tested
 * without an Android runtime.
 */
class AppUpdateCompatibilityTest {

    private val installedPackage = "com.jarves.mh.debug"
    private val installedVersion = 5L
    private val manifestVersion = 6L
    private val goodSigner = setOf("aabb")
    private val otherSigner = setOf("ccdd")

    private fun classify(
        archivePackage: String? = installedPackage,
        archiveVersionCode: Long = manifestVersion,
        expectedManifestVersionCode: Long = manifestVersion,
        archiveSignerDigests: Collection<String> = goodSigner,
        installedSignerDigests: Collection<String> = goodSigner,
    ) = UpdateCompatibilityCheck.classify(
        archivePackage = archivePackage,
        installedPackage = installedPackage,
        archiveVersionCode = archiveVersionCode,
        expectedManifestVersionCode = expectedManifestVersionCode,
        installedVersionCode = installedVersion,
        archiveSignerDigests = archiveSignerDigests,
        installedSignerDigests = installedSignerDigests,
    )

    private fun assertPermanent(expected: UpdateIncompatibility, result: UpdateCompatibility) {
        assertTrue("expected Permanent($expected) but was $result", result is UpdateCompatibility.Permanent)
        assertEquals(expected, (result as UpdateCompatibility.Permanent).reason)
    }

    // ── Permanent compatibility decisions ───────────────────────────────────

    @Test
    fun `compatible PocketForge inputs are accepted`() {
        assertEquals(UpdateCompatibility.Compatible, classify())
    }

    @Test
    fun `wrong package is permanent`() {
        // The upstream Mobile Harness release ID, as offered by the techjarves manifest.
        assertPermanent(
            UpdateIncompatibility.PACKAGE_MISMATCH,
            classify(archivePackage = "com.jarves.mh"),
        )
    }

    @Test
    fun `wrong certificate is permanent`() {
        assertPermanent(
            UpdateIncompatibility.CERTIFICATE_MISMATCH,
            classify(archiveSignerDigests = otherSigner),
        )
    }

    @Test
    fun `missing archive signers fail closed`() {
        assertPermanent(
            UpdateIncompatibility.SIGNER_MISSING,
            classify(archiveSignerDigests = emptySet()),
        )
    }

    @Test
    fun `missing installed signers fail closed`() {
        assertPermanent(
            UpdateIncompatibility.SIGNER_MISSING,
            classify(installedSignerDigests = emptySet()),
        )
    }

    @Test
    fun `apk version differing from the manifest is permanent`() {
        assertPermanent(
            UpdateIncompatibility.MANIFEST_VERSION_MISMATCH,
            classify(archiveVersionCode = 7L),
        )
    }

    @Test
    fun `apk version not newer than installed is permanent`() {
        assertPermanent(
            UpdateIncompatibility.VERSION_NOT_NEWER,
            classify(archiveVersionCode = installedVersion, expectedManifestVersionCode = installedVersion),
        )
    }

    @Test
    fun `unparseable archive is invalid`() {
        assertPermanent(UpdateIncompatibility.INVALID_APK, classify(archivePackage = null))
        assertPermanent(UpdateIncompatibility.INVALID_APK, classify(archivePackage = ""))
    }

    @Test
    fun `package mismatch is reported before any version concern`() {
        // Guards the check ordering: an upstream APK must be named as a package mismatch
        // even when its version also differs.
        assertPermanent(
            UpdateIncompatibility.PACKAGE_MISMATCH,
            classify(archivePackage = "com.jarves.mh", archiveVersionCode = 99L),
        )
    }

    // ── Session suppression ─────────────────────────────────────────────────

    @Test
    fun `the same permanently rejected update is suppressed`() {
        val policy = UpdateSuppressionPolicy()
        policy.suppress(manifestVersion, "https://example.invalid/a.apk")
        assertTrue(policy.isSuppressed(manifestVersion, "https://example.invalid/a.apk"))
    }

    @Test
    fun `a different versionCode is still offered`() {
        val policy = UpdateSuppressionPolicy()
        policy.suppress(manifestVersion, "https://example.invalid/a.apk")
        assertFalse(policy.isSuppressed(7L, "https://example.invalid/a.apk"))
    }

    @Test
    fun `a different artifact URL is still offered`() {
        val policy = UpdateSuppressionPolicy()
        policy.suppress(manifestVersion, "https://example.invalid/a.apk")
        assertFalse(policy.isSuppressed(manifestVersion, "https://example.invalid/b.apk"))
    }

    @Test
    fun `an unrelated update is never suppressed`() {
        val policy = UpdateSuppressionPolicy()
        assertFalse(policy.isSuppressed(manifestVersion, "https://example.invalid/a.apk"))
    }

    @Test
    fun `suppression keys do not collide across fields`() {
        val policy = UpdateSuppressionPolicy()
        // "6|https://x/1" must not be reachable from a different (versionCode, url) pair.
        policy.suppress(6L, "https://x/1")
        assertFalse(policy.isSuppressed(61L, "https://x/"))
    }

    // ── Cleanup policy (allow-list, containment) ────────────────────────────

    private fun updatesDir(): File =
        File.createTempFile("updates", "").let {
            it.delete()
            it.mkdirs()
            it
        }

    @Test
    fun `recognized legacy mobile-harness partial is removed`() {
        val dir = updatesDir()
        val legacy = File(dir, "mobile-harness-online.apk.part").apply { writeText("partial") }
        assertTrue(legacy.isFile)
        val resolved = UpdateFilePolicy.resolveStaleFile(dir, "mobile-harness-online.apk.part", null)
        assertTrue(resolved != null && resolved.delete())
        assertFalse(legacy.exists())
    }

    @Test
    fun `the current pocketforge partial name is recognized`() {
        val dir = updatesDir()
        assertTrue(UpdateFilePolicy.resolveStaleFile(dir, "pocketforge-online.apk.part", null) != null)
        assertTrue(UpdateFilePolicy.resolveStaleFile(dir, "pocketforge-offline.apk", null) != null)
    }

    @Test
    fun `an arbitrary apk is never a cleanup candidate`() {
        val dir = updatesDir()
        // Not on the allow-list, even though it is an APK in the updates directory.
        assertNull(UpdateFilePolicy.resolveStaleFile(dir, "some-other-app.apk", null))
        assertNull(UpdateFilePolicy.resolveStaleFile(dir, "project-backup.zip", null))
        assertNull(UpdateFilePolicy.resolveStaleFile(dir, "../projects.db", null))
    }

    @Test
    fun `cleanup cannot escape the updates directory`() {
        val dir = updatesDir()
        for (name in listOf("../files.db", "../../databases/x", "subdir/mobile-harness-online.apk.part")) {
            assertNull("$name must not resolve", UpdateFilePolicy.resolveStaleFile(dir, name, null))
        }
    }

    @Test
    fun `an actively installing artifact is preserved`() {
        val dir = updatesDir()
        val active = File(dir, "pocketforge-online.apk")
        assertNull(UpdateFilePolicy.resolveStaleFile(dir, "pocketforge-online.apk", active))
        // A different variant is still eligible while another installs.
        assertTrue(UpdateFilePolicy.resolveStaleFile(dir, "pocketforge-offline.apk", active) != null)
    }

    @Test
    fun `every recognized name stays inside the updates directory`() {
        val dir = updatesDir()
        for (name in UpdateFilePolicy.recognizedStaleNames) {
            val resolved = UpdateFilePolicy.resolveStaleFile(dir, name, null)
            assertTrue("$name should resolve", resolved != null)
            assertEquals(dir.canonicalFile, resolved!!.parentFile)
        }
    }

    // ── Fail-closed default channel ─────────────────────────────────────────

    @Test
    fun `the default build config points at no upstream application update url`() {
        val url = com.jarves.mh.BuildConfig.APP_UPDATE_MANIFEST_URL
        assertFalse(
            "PocketForge must not default to the upstream application update manifest, was: $url",
            url.contains("techjarves"),
        )
        assertEquals("", url)
    }
}
