package com.jarves.mh.data

import android.util.Log
import com.jarves.mh.model.ProjectKind

/**
 * Redacted project-lifecycle diagnostics.
 *
 * The event API accepts **only** closed enums, booleans and integers. There is
 * deliberately no overload taking a `String`, `Throwable`, `File`, URI, collection or
 * map, so a caller cannot route a project name, workspace slug or path, chat or prompt
 * content, source filename, API key, token, provider endpoint or raw `projects_json`
 * into Logcat. A project is identified only by [ProjectLifecycle.projectRef], a
 * non-reversible hash.
 */
internal open class ProjectLifecycleDiagnostics(
    private val emit: (String) -> Unit = { line -> Log.i(TAG, line) },
) {

    @JvmOverloads
    fun event(
        event: ProjectLifecycleEvent,
        trigger: ProjectLifecycle.Trigger,
        outcome: ProjectLifecycle.Outcome? = null,
        projectRef: Int? = null,
        kind: ProjectKind? = null,
        count: Int? = null,
        confirmed: Boolean = false,
    ) {
        val line = buildString {
            append("event=").append(event.id)
            append(" trigger=").append(trigger.id)
            outcome?.let { append(" outcome=").append(it.id) }
            projectRef?.let { append(" ref=").append(it) }
            kind?.let { append(" kind=").append(it.name) }
            count?.let { append(" count=").append(it) }
            if (confirmed) append(" confirmed=true")
        }
        emit(line)
    }

    companion object {
        const val TAG = "ProjectLifecycle"

        /** Production instance: writes straight to Android Log. */
        val production: ProjectLifecycleDiagnostics = ProjectLifecycleDiagnostics()
    }
}

/** Closed set of lifecycle events. */
internal enum class ProjectLifecycleEvent(val id: String) {
    PROJECTS_LOAD_SUCCESS("projects_load_success"),
    PROJECTS_LOAD_MISSING("projects_load_missing"),
    PROJECTS_LOAD_MALFORMED("projects_load_malformed"),
    PROJECTS_LOAD_FAILED("projects_load_failed"),
    PROJECT_PRESERVED_ON_STARTUP("project_preserved_on_startup"),
    PROJECT_PRESERVED_ON_CLOSE("project_preserved_on_close"),
    PROJECT_DELETE_REQUESTED("project_delete_requested"),
    PROJECT_DELETE_CONFIRMED("project_delete_confirmed"),
    PROJECT_DELETE_COMPLETED("project_delete_completed"),
    PROJECT_DELETE_FAILED("project_delete_failed"),
}
