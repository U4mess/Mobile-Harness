package com.jarves.mh.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DSH hard-link patch is a source-text edit, so it must tolerate incidental upstream
 * churn. These fixtures are the real import lines from the two installs seen on device:
 * dsh 0.2.0-rc.2 added `lstat` to the persistence file and dropped `realpath` from the
 * fs-local file, which the previous whole-import match could not survive.
 */
class DshCompatibilityPatchTest {

    private val persistence = DshPatchTarget(
        label = "dsh-session-persistence-jsonl",
        relativePath = "lib/dsh-session-persistence-jsonl/lib/index.js",
        callBefore = "await link(tmp, finalPath);",
        callAfter = "await copyFile(tmp, finalPath, 1);",
    )

    private val localFs = DshPatchTarget(
        label = "dsh-fs-local",
        relativePath = "lib/dsh-fs-local/lib/index.js",
        callBefore = "await linkFile(tempPath, absolutePath);",
        callAfter = "await copyFile(tempPath, absolutePath, 1);",
    )

    // ── Import layouts ───────────────────────────────────────────────────────

    @Test
    fun `patches the older import layout`() {
        val source = """
            import { link, mkdir, mkdtemp, open, readFile, readdir, realpath, rm, stat, truncate } from "node:fs/promises";
            await link(tmp, finalPath);
        """.trimIndent()

        val patched = patchDshHardLinkSource(source, persistence, "0.1.2-rc.1")

        assertTrue(patched.contains("await copyFile(tmp, finalPath, 1);"))
        assertFalse(patched.contains("await link(tmp, finalPath);"))
        assertTrue(patched.contains("import { copyFile, link, mkdir, mkdtemp, open, readFile, readdir, realpath, rm, stat, truncate } from \"node:fs/promises\";"))
    }

    @Test
    fun `patches the 0_2_0 import that added lstat`() {
        val source = """
            import { link, lstat, mkdir, mkdtemp, open, readFile, readdir, realpath, rm, stat, truncate } from "node:fs/promises";
            await link(tmp, finalPath);
        """.trimIndent()

        val patched = patchDshHardLinkSource(source, persistence, "0.2.0-rc.2")

        assertTrue(patched.contains("await copyFile(tmp, finalPath, 1);"))
        // Every pre-existing identifier survives, in order.
        assertTrue(patched.contains("copyFile, link, lstat, mkdir, mkdtemp, open, readFile, readdir, realpath, rm, stat, truncate"))
    }

    @Test
    fun `patches the 0_2_0 fs-local import that dropped realpath`() {
        val source = """
            import { chmod, link, lstat, mkdir, open, readFile, readdir, rename, rm, stat } from "node:fs/promises";
            await linkFile(tempPath, absolutePath);
        """.trimIndent()

        val patched = patchDshHardLinkSource(source, localFs, "0.2.0-rc.2")

        assertTrue(patched.contains("await copyFile(tempPath, absolutePath, 1);"))
        assertFalse(patched.contains("realpath"))
        assertTrue(patched.contains("copyFile, chmod, link, lstat, mkdir, open, readFile, readdir, rename, rm, stat"))
    }

    @Test
    fun `accepts single quoted imports`() {
        val source = """
            import { link } from 'node:fs/promises';
            await link(tmp, finalPath);
        """.trimIndent()

        val patched = patchDshHardLinkSource(source, persistence, "0.2.0-rc.2")

        assertTrue(patched.contains("copyFile, link"))
        assertTrue(patched.contains("await copyFile(tmp, finalPath, 1);"))
    }

    // ── Idempotence and repair ───────────────────────────────────────────────

    @Test
    fun `already patched files are returned unchanged`() {
        val source = """
            import { copyFile, link, lstat, mkdir } from "node:fs/promises";
            await copyFile(tmp, finalPath, 1);
        """.trimIndent()

        assertEquals(source, patchDshHardLinkSource(source, persistence, "0.2.0-rc.2"))
    }

    @Test
    fun `patching twice is safe`() {
        val source = """
            import { link, lstat, mkdir } from "node:fs/promises";
            await link(tmp, finalPath);
        """.trimIndent()

        val once = patchDshHardLinkSource(source, persistence, "0.2.0-rc.2")
        val twice = patchDshHardLinkSource(once, persistence, "0.2.0-rc.2")

        assertEquals(once, twice)
    }

    @Test
    fun `a patched call with a lost import is repaired`() {
        val source = """
            import { link, lstat, mkdir } from "node:fs/promises";
            await copyFile(tmp, finalPath, 1);
        """.trimIndent()

        val patched = patchDshHardLinkSource(source, persistence, "0.2.0-rc.2")

        assertTrue(patched.contains("copyFile, link, lstat, mkdir"))
        assertEquals(1, patched.split("await copyFile(tmp, finalPath, 1);").size - 1)
    }

    // ── Unsupported layouts ──────────────────────────────────────────────────

    @Test
    fun `missing call anchor names the version and the target`() {
        val source = """
            import { link, lstat, mkdir } from "node:fs/promises";
            await somethingElse();
        """.trimIndent()

        val error = runCatching { patchDshHardLinkSource(source, persistence, "0.2.0-rc.2") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue("message was: ${error?.message}", error!!.message!!.contains("0.2.0-rc.2"))
        assertTrue(error.message!!.contains("dsh-session-persistence-jsonl"))
    }

    /** The error must describe the build that is actually installed, not the pinned one. */
    @Test
    fun `error messages never claim the pinned version`() {
        val source = """
            import { link } from "node:fs/promises";
            await somethingElse();
        """.trimIndent()

        val message = runCatching { patchDshHardLinkSource(source, persistence, "0.2.0-rc.2") }
            .exceptionOrNull()!!.message!!

        assertFalse(message.contains(RuntimeInstaller.DSH_VERSION))
    }

    @Test
    fun `ambiguous call anchors are rejected`() {
        val source = """
            import { link, lstat, mkdir } from "node:fs/promises";
            await link(tmp, finalPath);
            await link(tmp, finalPath);
        """.trimIndent()

        val error = runCatching { patchDshHardLinkSource(source, persistence, "0.2.0-rc.2") }.exceptionOrNull()

        assertTrue(error?.message!!.contains("expected exactly one"))
    }

    @Test
    fun `a half-patched file is rejected rather than guessed at`() {
        val source = """
            import { copyFile, link, lstat, mkdir } from "node:fs/promises";
            await link(tmp, finalPath);
            await copyFile(tmp, finalPath, 1);
        """.trimIndent()

        val error = runCatching { patchDshHardLinkSource(source, persistence, "0.2.0-rc.2") }.exceptionOrNull()

        assertTrue(error?.message!!.contains("both the original and the patched"))
    }

    @Test
    fun `a file without a node fs promises import is rejected`() {
        val source = """
            import { link } from "node:fs";
            await link(tmp, finalPath);
        """.trimIndent()

        val error = runCatching { patchDshHardLinkSource(source, persistence, "0.2.0-rc.2") }.exceptionOrNull()

        assertTrue(error?.message!!.contains("no node:fs/promises named import"))
    }

    // ── Two-phase apply: no partial writes ───────────────────────────────────

    @Test
    fun `a failing second target leaves the first untouched`() {
        val root = tempRoot()
        val good = fileIn(root, persistence.relativePath, """
            import { link, lstat, mkdir } from "node:fs/promises";
            await link(tmp, finalPath);
        """.trimIndent())
        val original = good.readText()
        // localFs target is intentionally absent, so validation must fail.

        val error = runCatching {
            applyDshHardLinkPatches(root, listOf(persistence, localFs), "0.2.0-rc.2")
        }.exceptionOrNull()

        assertTrue(error?.message!!.contains("dsh-fs-local"))
        assertEquals("the first target must not be written", original, good.readText())
    }

    @Test
    fun `an unsupported first target also prevents all writes`() {
        val root = tempRoot()
        val broken = fileIn(root, persistence.relativePath, "export const nothing = 1;\n")
        val second = fileIn(root, localFs.relativePath, """
            import { link, lstat } from "node:fs/promises";
            await linkFile(tempPath, absolutePath);
        """.trimIndent())

        runCatching { applyDshHardLinkPatches(root, listOf(persistence, localFs), "0.2.0-rc.2") }

        assertEquals("export const nothing = 1;\n", broken.readText())
        assertFalse(second.readText().contains("copyFile"))
    }

    @Test
    fun `all targets are written when every one validates`() {
        val root = tempRoot()
        val first = fileIn(root, persistence.relativePath, """
            import { link, lstat, mkdir } from "node:fs/promises";
            await link(tmp, finalPath);
        """.trimIndent())
        val second = fileIn(root, localFs.relativePath, """
            import { chmod, link } from "node:fs/promises";
            await linkFile(tempPath, absolutePath);
        """.trimIndent())

        applyDshHardLinkPatches(root, listOf(persistence, localFs), "0.2.0-rc.2")

        assertTrue(first.readText().contains("await copyFile(tmp, finalPath, 1);"))
        assertTrue(second.readText().contains("await copyFile(tempPath, absolutePath, 1);"))
        // The temporary staging files must not be left behind.
        assertTrue(first.parentFile!!.listFiles()!!.none { it.name.endsWith(".pocket-patch") })
    }

    // ── Markers and rollback ─────────────────────────────────────────────────

    @Test
    fun `markers round-trip through capture and restore`() {
        val versionFile = fileIn(tempRoot(), "version", "0.1.2-rc.1")
        val compatFile = fileIn(tempRoot(), "compat", "copyfile-excl-v1")

        val snapshot = captureDshMarkers(versionFile, compatFile)
        versionFile.writeText("0.2.0-rc.2")
        compatFile.delete()
        restoreDshMarkers(versionFile, compatFile, snapshot)

        assertEquals("0.1.2-rc.1", versionFile.readText())
        assertEquals("copyfile-excl-v1", compatFile.readText())
    }

    @Test
    fun `restoring a missing marker deletes it again`() {
        val versionFile = fileIn(tempRoot(), "version", "0.2.0-rc.2")
        val compatFile = File(versionFile.parentFile, "compat")

        restoreDshMarkers(versionFile, compatFile, DshMarkerSnapshot("0.1.2-rc.1", null))

        assertEquals("0.1.2-rc.1", versionFile.readText())
        assertFalse(compatFile.exists())
    }

    @Test
    fun `rollback restores the previous install and discards the rejected one`() {
        val lib = File(tempRoot(), "usr-local-lib").apply { mkdirs() }
        File(lib, "dsh").apply { mkdirs(); File(this, "marker").writeText("new") }
        File(lib, "dsh.previous").apply { mkdirs(); File(this, "marker").writeText("old") }

        assertTrue(restorePreviousDshInstall(lib))

        assertEquals("old", File(File(lib, "dsh"), "marker").readText())
        assertFalse(File(lib, "dsh.previous").exists())
        assertFalse(File(lib, "dsh.failed").exists())
    }

    @Test
    fun `rollback reports failure when no previous install exists`() {
        val lib = File(tempRoot(), "usr-local-lib").apply { mkdirs() }
        File(lib, "dsh").apply { mkdirs(); File(this, "marker").writeText("new") }

        assertFalse(restorePreviousDshInstall(lib))

        assertEquals("new", File(File(lib, "dsh"), "marker").readText())
    }

    @Test
    fun `package version is read from the installed tree`() {
        val dsh = File(tempRoot(), "dsh")
        File(dsh, "node_modules/@deepseek-ai/dsh").mkdirs()
        File(dsh, "node_modules/@deepseek-ai/dsh/package.json")
            .writeText("""{"name":"@deepseek-ai/dsh","version":"0.2.0-rc.2"}""")

        assertEquals("0.2.0-rc.2", readDshPackageVersion(dsh))
        assertEquals(null, readDshPackageVersion(File(tempRoot(), "absent")))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun tempRoot(): File = Files.createTempDirectory("mh-dsh-patch").toFile().apply { deleteOnExit() }

    private fun fileIn(root: File, relativePath: String, content: String): File =
        File(root, relativePath).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
}
