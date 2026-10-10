package com.jarves.mh.data

import org.json.JSONArray

/**
 * Project lifecycle policy.
 *
 * A project may only be destroyed by an **explicit, user-confirmed** delete. No
 * automatic lifecycle transition (startup, initialization, opening, closing,
 * switching, background work) may remove a registry entry, workspace, chats or
 * checkpoints.
 *
 * This exists because an earlier heuristic treated a project as disposable when it
 * had no persisted user messages and no visible user files. That condition is
 * satisfied by a genuinely new project, by messages that failed to persist, by
 * transient filesystem errors and by counting errors — and acting on it deleted
 * workspaces and rewrote the registry.
 */
internal object ProjectLifecycle {

    /** Why a lifecycle decision is being made. */
    enum class Trigger(val id: String) {
        STARTUP("startup"),
        INITIALIZATION("init"),
        PROJECT_OPENED("opened"),
        PROJECT_CLOSED("closed"),
        PROJECT_SWITCHED("switched"),
        BACKGROUND("background"),
        EXPLICIT_DELETE("explicit_delete"),
    }

    /** What to do with the project's registry entry, workspace, chats and checkpoints. */
    enum class Outcome(val id: String) {
        PRESERVE("preserve"),
        DELETE("delete"),
    }

    /**
     * The single decision point. Only [Trigger.EXPLICIT_DELETE] with a satisfied
     * confirmation may delete; every automatic trigger preserves.
     */
    fun decide(trigger: Trigger, confirmed: Boolean): Outcome = when {
        trigger != Trigger.EXPLICIT_DELETE -> Outcome.PRESERVE
        confirmed -> Outcome.DELETE
        else -> Outcome.PRESERVE
    }

    /** True only when destruction is authorised. Production and tests share this. */
    fun mayDelete(trigger: Trigger, confirmed: Boolean): Boolean =
        decide(trigger, confirmed) == Outcome.DELETE

    /**
     * Stable, non-reversible reference to a project for diagnostics. A caller cannot
     * route a project name, slug, path or message text through this.
     */
    fun projectRef(projectId: String): Int = projectId.hashCode()

    /**
     * Whether [candidate] is safe to keep as the last-known-good registry backup.
     *
     * Rejects blank, malformed and empty arrays so a failed or empty write can never
     * displace a good backup.
     */
    fun isUsableBackup(candidate: String?): Boolean {
        val raw = candidate?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        return array.length() > 0
    }
}
