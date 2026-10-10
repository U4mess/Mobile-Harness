package com.jarves.mh.runtime

import java.util.Collections

/**
 * Per-test diagnostics capture.
 *
 * Each instance owns its own [AgentDiagnostics] with its own emit lambda and its own
 * synchronized line buffer, so two tests — including tests running in parallel — cannot
 * observe or corrupt each other's records. There is no global sink to install and
 * therefore no test state that needs restoring.
 */
internal class DiagnosticCapture {

    private val buffer = Collections.synchronizedList(mutableListOf<String>())

    val diagnostics: AgentDiagnostics = AgentDiagnostics { line -> buffer += line }

    /** Snapshot copy; safe to read while another thread is still emitting. */
    val recorded: List<String>
        get() = synchronized(buffer) { buffer.toList() }

    val lastLine: String?
        get() = recorded.lastOrNull()
}
