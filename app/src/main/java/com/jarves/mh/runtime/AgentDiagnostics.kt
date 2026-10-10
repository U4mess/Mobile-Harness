package com.jarves.mh.runtime

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

/** Closed operation identity. No free-form operation strings exist. */
internal enum class DiagnosticOperation(val id: String) {
    SESSION("session"),
    PERMISSION("permission"),
    GATEWAY("gateway"),
    INSTALLER("installer"),
    NOTIFICATION("notification"),
}

/** Closed emitting agent identity. */
internal enum class DiagnosticAgent(val id: String) {
    CLAUDE("claude"),
    DSH("dsh"),
    GATEWAY("gateway"),
    INSTALLER("installer"),
}

/**
 * Closed stage vocabulary. Every diagnostic is one of these; a caller cannot
 * invent a stage or concatenate raw text into one.
 */
internal enum class DiagnosticStage(val id: String) {
    LAUNCH_PREPARED("launch_prepared"),
    COMMAND_BUILT("command_built"),
    AUTO_APPROVED("auto_approved"),
    CHANGED("changed"),
    EXIT("exit"),
    UPSTREAM_STATUS("upstream_status"),
    NOTIFICATION_FAILED("notification_failed"),
    REPAIR_FAILED("repair_failed"),
}

/** Sanitised failure category. Derived from exception type only. */
internal enum class ErrorCategory(val id: String) {
    CANCELLED("cancelled"),
    TIMEOUT("timeout"),
    NETWORK("network"),
    CHECKSUM("checksum"),

    /**
     * Reserved. No exception maps here yet: a runtime-missing failure should only be
     * reported as such once a dedicated production type exists and is actually thrown,
     * so that a generic [java.io.FileNotFoundException] is never mislabelled.
     */
    RUNTIME_MISSING("runtime_missing"),
    PROVIDER_REJECTED("provider_rejected"),
    IO("io"),
    UNKNOWN("unknown"),
}

/**
 * Credential-free agent diagnostics.
 *
 * The event API accepts **only** closed enums and primitives. There is
 * deliberately no overload taking a `String`, `Throwable`, URI, File, collection
 * or map, so a caller cannot route prompts, conversation history, raw process
 * output, tool names, file paths, URLs, provider bodies or exception text into
 * Logcat. [redact] exists as a separately tested defence-in-depth utility for any
 * future non-event use, but event callers must not rely on it.
 *
 * Produces a stable `key=value` line of approved fields only:
 * `op=… agent=… stage=… id=… elapsedMs=… exitCode=… bytes=… paths=… args=…
 *  status=… cancelled=… category=…`
 */
internal open class AgentDiagnostics(
    private val emit: (String) -> Unit = { line -> Log.i(TAG, line) },
) {

    @JvmOverloads
    fun event(
        operation: DiagnosticOperation,
        agent: DiagnosticAgent,
        stage: DiagnosticStage,
        operationId: Long? = null,
        elapsedMs: Long? = null,
        exitCode: Int? = null,
        byteCount: Long? = null,
        pathCount: Int? = null,
        argumentCount: Int? = null,
        httpStatus: Int? = null,
        cancelled: Boolean = false,
        category: ErrorCategory? = null,
    ) {
        val line = buildString {
            append("op=").append(operation.id)
            append(" agent=").append(agent.id)
            append(" stage=").append(stage.id)
            operationId?.let { append(" id=").append(it) }
            elapsedMs?.let { append(" elapsedMs=").append(it) }
            exitCode?.let { append(" exitCode=").append(it) }
            byteCount?.let { append(" bytes=").append(it) }
            pathCount?.let { append(" paths=").append(it) }
            argumentCount?.let { append(" args=").append(it) }
            httpStatus?.let { append(" status=").append(it) }
            if (cancelled) append(" cancelled=true")
            category?.let { append(" category=").append(it.id) }
        }
        emit(line)
    }

    companion object {
        const val TAG = "AgentRuntime"

        /** Production instance: writes straight to Android Log. */
        val production: AgentDiagnostics = AgentDiagnostics()

        /**
         * Structure-based classification. Reads only the exception **type**;
         * never `message`, `cause.message`, stack frames or `toString()`.
         *
         * [ErrorCategory.RUNTIME_MISSING] is deliberately unreachable: no production
         * type expresses "runtime missing" yet, and a generic [FileNotFoundException]
         * is too weak evidence to justify that label, so it falls through to [IO].
         */
        fun errorCategory(error: Throwable?): ErrorCategory = when {
            error == null -> ErrorCategory.UNKNOWN
            error is CancellationException -> ErrorCategory.CANCELLED
            error is SocketTimeoutException -> ErrorCategory.TIMEOUT
            error is UnknownHostException ||
                error is ConnectException ||
                error is NoRouteToHostException ||
                error is SocketException -> ErrorCategory.NETWORK
            error is ChecksumMismatchException -> ErrorCategory.CHECKSUM
            error is HttpFailureException -> ErrorCategory.PROVIDER_REJECTED
            error is ClaudeRuntimeBridge.ProviderSessionException -> ErrorCategory.PROVIDER_REJECTED
            error is DshRuntimeBridge.DshSessionException -> ErrorCategory.PROVIDER_REJECTED
            error is IOException -> ErrorCategory.IO
            else -> ErrorCategory.UNKNOWN
        }

        /**
         * Defence in depth for non-event text. Masks credential-shaped values,
         * collapses whitespace and caps length. Event callers must never depend
         * on this to make a sensitive value safe — they must not pass it at all.
         */
        fun redact(value: String): String = value
            .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "sk-••••")
            .replace(Regex("gh[pous]_[A-Za-z0-9]{8,}"), "gh••••")
            .replace(Regex("github_pat_[A-Za-z0-9_]{8,}"), "github_pat_••••")
            .replace(Regex("xox[baprs]-[A-Za-z0-9-]{8,}"), "xox••••")
            .replace(Regex("AIza[0-9A-Za-z_-]{8,}"), "AIza••••")
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._-]{8,}"), "Bearer ••••")
            .replace(Regex("(?i)([?&](?:api[_-]?key|token|access_token|key)=)[^&\\s]+"), "$1••••")
            .replace(Regex("(://[^/@\\s:]+):[^/@\\s]+@"), "$1:••••@")
            .replace(Regex("(?i)(authorization|api[_-]?key|x-api-key)\\s*[:=]\\s*[^\\s,}]+"), "$1: ••••")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
    }
}
