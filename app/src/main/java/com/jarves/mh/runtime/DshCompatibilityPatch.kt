package com.jarves.mh.runtime

import java.io.File
import org.json.JSONObject

/**
 * Android cannot create POSIX hard links inside PRoot, and PRoot's `--link2symlink`
 * workaround leaves dangling symlinks because DSH deletes its staging file immediately
 * after publishing. DSH's own no-clobber publication therefore has to publish through
 * `copyFile(..., COPYFILE_EXCL)` instead of `link(...)`.
 *
 * That patch is expressed as source edits, so it has to survive incidental upstream
 * churn. Matching the whole import statement proved brittle: dsh 0.2.0-rc.2 added
 * `lstat` to one file and dropped `realpath` from the other, which aborted an update
 * *after* npm had already replaced the installation. The import is now edited by
 * identifier, and only the publishing call itself is matched verbatim.
 */
internal data class DshPatchTarget(
    val label: String,
    val relativePath: String,
    val callBefore: String,
    val callAfter: String,
)

internal val DSH_PATCH_TARGETS = listOf(
    DshPatchTarget(
        label = "dsh-session-persistence-jsonl",
        relativePath = "usr/local/lib/dsh/node_modules/@deepseek-ai/dsh-session-persistence-jsonl/lib/index.js",
        callBefore = "await link(tmp, finalPath);",
        callAfter = "await copyFile(tmp, finalPath, 1);",
    ),
    DshPatchTarget(
        label = "dsh-fs-local",
        relativePath = "usr/local/lib/dsh/node_modules/@deepseek-ai/dsh-fs-local/lib/index.js",
        callBefore = "await linkFile(tempPath, absolutePath);",
        callAfter = "await copyFile(tempPath, absolutePath, 1);",
    ),
)

private const val COPY_FILE = "copyFile"
private val FS_PROMISES_IMPORT = Regex("""import\s*\{([^}]*)\}\s*from\s*["']node:fs/promises["']\s*;""")

/**
 * Rewrites one DSH source file so it publishes with `copyFile(..., COPYFILE_EXCL)`.
 *
 * Idempotent: an already-patched file is returned unchanged, and a file whose import
 * lost `copyFile` is repaired. Every failure names the package version and the target
 * it could not patch, so a mismatch is diagnosable from the UI without log access.
 */
internal fun patchDshHardLinkSource(
    source: String,
    target: DshPatchTarget,
    packageVersion: String,
): String {
    fun unsupported(detail: String) = IllegalStateException(
        "DeepSeek Harness $packageVersion is not compatible with this PocketDev build: " +
            "${target.label} $detail",
    )

    val beforeCount = source.countOccurrences(target.callBefore)
    val afterCount = source.countOccurrences(target.callAfter)
    when {
        beforeCount + afterCount == 0 -> throw unsupported("has no publishing call to patch.")
        beforeCount > 1 || afterCount > 1 ->
            throw unsupported("has ${beforeCount + afterCount} publishing calls; expected exactly one.")
        beforeCount == 1 && afterCount == 1 ->
            throw unsupported("contains both the original and the patched publishing call.")
    }

    val importMatch = FS_PROMISES_IMPORT.find(source)
        ?: throw unsupported("has no node:fs/promises named import to extend.")
    val importedNames = importMatch.groupValues[1]
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

    var patched = source
    if (COPY_FILE !in importedNames) {
        // Keep every other identifier (and its order); only prepend the new one.
        val extended = (listOf(COPY_FILE) + importedNames).joinToString(", ")
        patched = patched.replaceRange(importMatch.range, "import { $extended } from \"node:fs/promises\";")
    }
    if (beforeCount == 1) {
        patched = patched.replace(target.callBefore, target.callAfter)
    }

    val remaining = patched.countOccurrences(target.callBefore)
    val published = patched.countOccurrences(target.callAfter)
    if (remaining != 0 || published != 1) {
        throw unsupported("could not be patched cleanly ($remaining hard-link calls, $published patched calls left).")
    }
    return patched
}

/** Package version recorded inside an installed DSH tree, or null when unreadable. */
internal fun readDshPackageVersion(dshLibraryDir: File): String? = runCatching {
    JSONObject(File(dshLibraryDir, "node_modules/@deepseek-ai/dsh/package.json").readText())
        .optString("version")
}.getOrNull()?.takeIf(String::isNotBlank)

/**
 * Validates every patch target under [root] before writing any of them, so a source
 * layout this build does not understand can never leave a half-patched installation.
 * Targets whose content is already correct are left untouched.
 */
internal fun applyDshHardLinkPatches(root: File, targets: List<DshPatchTarget>, packageVersion: String) {
    val staged = targets.map { target ->
        val file = File(root, target.relativePath)
        check(file.isFile) {
            "DeepSeek Harness $packageVersion is not compatible with this PocketDev build: " +
                "${target.label} (${target.relativePath}) is missing."
        }
        val original = file.readText()
        Triple(file, original, patchDshHardLinkSource(original, target, packageVersion))
    }
    // Only reached once every target has been read and validated.
    staged.forEach { (file, original, patched) ->
        if (patched != original) {
            val temporary = File(file.parentFile, "${file.name}.pocket-patch")
            temporary.writeText(patched)
            java.nio.file.Files.move(
                temporary.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

/** Marker files that describe which DSH build is installed and whether it is patched. */
internal data class DshMarkerSnapshot(val version: String?, val compatibility: String?)

internal fun captureDshMarkers(versionFile: File, compatibilityFile: File): DshMarkerSnapshot =
    DshMarkerSnapshot(versionFile.readMarkerOrNull(), compatibilityFile.readMarkerOrNull())

internal fun restoreDshMarkers(versionFile: File, compatibilityFile: File, snapshot: DshMarkerSnapshot) {
    versionFile.writeMarker(snapshot.version)
    compatibilityFile.writeMarker(snapshot.compatibility)
}

/**
 * Puts the previous DSH installation back after the new one failed compatibility
 * patching or verification. The unverified install is discarded, never left in place.
 */
internal fun restorePreviousDshInstall(libraryDir: File): Boolean {
    val previous = File(libraryDir, "dsh.previous")
    if (!previous.isDirectory) return false
    val current = File(libraryDir, "dsh")
    val rejected = File(libraryDir, "dsh.failed")
    rejected.deleteRecursively()
    if (current.isDirectory && !current.renameTo(rejected)) return false
    if (!previous.renameTo(current)) {
        // Never leave the tree without a dsh directory.
        if (rejected.isDirectory) rejected.renameTo(current)
        return false
    }
    rejected.deleteRecursively()
    return true
}

private fun String.countOccurrences(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = indexOf(needle)
    while (index >= 0) {
        count++
        index = indexOf(needle, index + needle.length)
    }
    return count
}

private fun File.readMarkerOrNull(): String? =
    runCatching { readText().trim() }.getOrNull()?.takeIf(String::isNotEmpty)

private fun File.writeMarker(value: String?) {
    if (value == null) {
        delete()
    } else {
        parentFile?.mkdirs()
        writeText(value)
    }
}
