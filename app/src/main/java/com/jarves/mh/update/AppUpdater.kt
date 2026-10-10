package com.jarves.mh.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jarves.mh.BuildConfig
import com.jarves.mh.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
)

/** Why a downloaded update can never be installed on this device. */
internal enum class UpdateIncompatibility {
    INVALID_APK,
    PACKAGE_MISMATCH,
    MANIFEST_VERSION_MISMATCH,
    VERSION_NOT_NEWER,
    SIGNER_MISSING,
    CERTIFICATE_MISMATCH,
}

/**
 * Result of checking a downloaded artifact against the installed app and the manifest.
 * Only [Compatible] may proceed to installation; every [Permanent] reason is
 * unrecoverable by retrying, which is what separates it from a transient network or
 * filesystem failure (which surfaces as a thrown [java.io.IOException] instead).
 */
internal sealed interface UpdateCompatibility {
    data object Compatible : UpdateCompatibility
    data class Permanent(val reason: UpdateIncompatibility) : UpdateCompatibility
}

/**
 * Reasons a downloaded update can never be installed. Distinct from transient failures
 * (network, interrupted transfer, filesystem) so the caller can suppress the artifact
 * permanently instead of offering the same broken download again.
 */
internal class IncompatibleUpdateException(
    val reason: UpdateIncompatibility,
) : IllegalStateException(reason.name)

/**
 * Pure compatibility decision, deliberately free of Android types so the rules can be
 * asserted directly in a unit test instead of being restated there.
 *
 * Signer digests are opaque non-empty strings: this function never inspects, formats or
 * reports them. Missing digests on either side fail closed.
 */
internal object UpdateCompatibilityCheck {

    fun classify(
        archivePackage: String?,
        installedPackage: String,
        archiveVersionCode: Long,
        expectedManifestVersionCode: Long,
        installedVersionCode: Long,
        archiveSignerDigests: Collection<String>,
        installedSignerDigests: Collection<String>,
    ): UpdateCompatibility {
        // A null package name means getPackageArchiveInfo could not parse the file at all.
        if (archivePackage.isNullOrBlank()) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.INVALID_APK)
        }
        if (archivePackage != installedPackage) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.PACKAGE_MISMATCH)
        }
        if (archiveVersionCode != expectedManifestVersionCode) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.MANIFEST_VERSION_MISMATCH)
        }
        if (archiveVersionCode <= installedVersionCode) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.VERSION_NOT_NEWER)
        }
        if (archiveSignerDigests.isEmpty() || installedSignerDigests.isEmpty()) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.SIGNER_MISSING)
        }
        if (archiveSignerDigests.toSet() != installedSignerDigests.toSet()) {
            return UpdateCompatibility.Permanent(UpdateIncompatibility.CERTIFICATE_MISMATCH)
        }
        return UpdateCompatibility.Compatible
    }
}

/**
 * Files the updater is allowed to remove. Cleanup is deliberately an explicit list
 * rather than "anything ending in .apk": the updates directory must never be swept by
 * extension, and an in-progress install must not have its payload deleted underneath it.
 */
internal object UpdateFilePolicy {
    const val DIRECTORY = "updates"

    /** Legacy Mobile Harness names, plus the PocketForge names, for both variants. */
    val recognizedStaleNames: Set<String> = buildSet {
        for (variant in listOf("online", "offline")) {
            add("mobile-harness-$variant.apk.part")
            add("mobile-harness-$variant.apk")
            add("pocketforge-$variant.apk.part")
            add("pocketforge-$variant.apk")
        }
    }

    /**
     * Resolves a candidate inside [updatesDirectory], refusing anything that would escape
     * it. Returns null when [name] is not a recognized updater file or the resolved path
     * leaves the directory, so cleanup can never touch projects, runtime bundles,
     * sessions or credentials.
     */
    fun resolveStaleFile(updatesDirectory: File, name: String, activeInstall: File?): File? {
        if (name !in recognizedStaleNames) return null
        val candidate = File(updatesDirectory, name)
        val root = updatesDirectory.canonicalFile
        val resolved = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!resolved.path.startsWith(root.path + File.separator)) return null
        if (resolved.parentFile != root) return null
        if (activeInstall != null && runCatching { activeInstall.canonicalFile }.getOrNull() == resolved) return null
        return resolved
    }
}

/**
 * Session-scoped suppression of permanently incompatible updates.
 *
 * A rejected artifact is identified by manifest version **and** artifact URL, so a
 * genuinely different release is still offered while the rejected one is not. Nothing
 * here is persisted: suppression lasts for the life of the process by design. Kept free
 * of Android types so the rules are asserted directly.
 */
internal class UpdateSuppressionPolicy {
    private val suppressed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun key(versionCode: Long, apkUrl: String): String = "$versionCode|$apkUrl"

    fun suppress(versionCode: Long, apkUrl: String) {
        suppressed.add(key(versionCode, apkUrl))
    }

    fun isSuppressed(versionCode: Long, apkUrl: String): Boolean =
        suppressed.contains(key(versionCode, apkUrl))
}

class AppUpdater(
    private val context: Context,
    /**
     * Optional manifest URL override. When non-empty, used in place of
     * [BuildConfig.APP_UPDATE_MANIFEST_URL]. Debug builds populate this
     * from Settings → Update channel so the update flow can be exercised
     * end-to-end against a Cloudflare Tunnel or ngrok HTTPS URL without
     * publishing a release to GitHub.
     */
    private val manifestUrlOverride: String = "",
) {
    private val updatesDirectory: File
        get() = File(context.filesDir, UpdateFilePolicy.DIRECTORY)

    private fun partialFile() = File(updatesDirectory, "pocketforge-${BuildConfig.APP_VARIANT}.apk.part")

    private fun finalFile() = File(updatesDirectory, "pocketforge-${BuildConfig.APP_VARIANT}.apk")

    fun check(): AppUpdateInfo? {
        val manifestUrl = manifestUrlOverride.ifBlank { BuildConfig.APP_UPDATE_MANIFEST_URL }
        if (!manifestUrl.startsWith("https://")) return null
        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val versionCode = root.optLong("versionCode")
            if (versionCode <= BuildConfig.VERSION_CODE) return null
            val artifact = root.optJSONObject("artifacts")?.optJSONObject(BuildConfig.APP_VARIANT)
                ?: root.optJSONObject(BuildConfig.APP_VARIANT)
                ?: root
            val url = artifact.optString("url").ifBlank { artifact.optString("apkUrl") }
            if (!url.startsWith("https://")) return null
            AppUpdateInfo(
                versionCode = versionCode,
                versionName = root.optString("versionName", versionCode.toString()),
                apkUrl = url,
                sha256 = artifact.optString("sha256").lowercase(),
                sizeBytes = artifact.optLong("sizeBytes", -1L),
                notes = root.optString("notes"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun download(info: AppUpdateInfo, progress: (Long, Long) -> Unit): File {
        val directory = updatesDirectory.also { it.mkdirs() }
        val partial = File(directory, "pocketforge-${BuildConfig.APP_VARIANT}.apk.part")
        val target = File(directory, "pocketforge-${BuildConfig.APP_VARIANT}.apk")
        val connection = URL(info.apkUrl).openConnection() as HttpURLConnection

        // Every exit path removes the partial: a rejected artifact must not linger, and a
        // transient failure must not leave a corrupt file for the next attempt to reuse.
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            check(code in 200..299) { "Update download failed (HTTP $code)" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        progress(downloaded, total)
                    }
                }
            }
            if (info.sha256.isNotBlank()) {
                val actual = sha256(partial)
                check(actual.equals(info.sha256, ignoreCase = true)) { "Downloaded APK failed its SHA-256 verification" }
            }
            verifyApk(partial, info.versionCode)
            // Verification passed, so publication is the only remaining step.
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not prepare the downloaded update" }
            return target
        } catch (error: Throwable) {
            partial.delete()
            // A permanently incompatible artifact must not be left behind for a retry,
            // and neither must an older accepted copy of the same variant.
            if (error is IncompatibleUpdateException) target.delete()
            throw error
        } finally {
            connection.disconnect()
            if (partial.exists() && !target.exists()) partial.delete()
        }
    }

    /**
     * Removes recognized stale updater files from filesDir/updates. Bounded to the
     * explicit allow-list in [UpdateFilePolicy] and constrained to that directory, so
     * projects, runtime bundles, sessions and credentials are never candidates.
     * Returns the number of files removed.
     */
    fun cleanupStaleFiles(activeInstall: File? = null): Int {
        val directory = updatesDirectory
        if (!directory.isDirectory) return 0
        val names = directory.list()?.toList().orEmpty()
        var removed = 0
        for (name in names) {
            val file = UpdateFilePolicy.resolveStaleFile(directory, name, activeInstall) ?: continue
            if (file.isFile && file.delete()) removed++
        }
        return removed
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, expectedVersionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IncompatibleUpdateException(UpdateIncompatibility.INVALID_APK)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners
        } else {
            archive.signatures
        }
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners
        } else {
            installed.signatures
        }
        val result = UpdateCompatibilityCheck.classify(
            archivePackage = archive.packageName,
            installedPackage = context.packageName,
            archiveVersionCode = archiveVersion,
            expectedManifestVersionCode = expectedVersionCode,
            installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
            archiveSignerDigests = archiveSignatures.orEmpty().map { sha256(it.toByteArray()) },
            installedSignerDigests = installedSignatures.orEmpty().map { sha256(it.toByteArray()) },
        )
        when (result) {
            is UpdateCompatibility.Compatible -> Unit
            is UpdateCompatibility.Permanent -> throw IncompatibleUpdateException(result.reason)
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
