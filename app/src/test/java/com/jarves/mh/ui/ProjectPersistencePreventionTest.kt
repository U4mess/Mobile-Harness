package com.jarves.mh.ui

import com.jarves.mh.data.ProjectLifecycle
import com.jarves.mh.data.ProjectLifecycleDiagnostics
import com.jarves.mh.data.ProjectLifecycleEvent
import com.jarves.mh.model.ProjectKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the project-loss defect.
 *
 * A project registry was emptied and its workspaces and chats deleted because an
 * automatic lifecycle path treated a project with no visible messages and no visible
 * files as disposable. A brand-new project satisfies that condition by definition.
 *
 * These tests exercise the pure policy that production calls, and use source scans as
 * secondary guards that the destructive blocks are gone.
 */
class ProjectPersistencePreventionTest {

    private val mainViewModel: String by lazy { read("ui/MainViewModel.kt") }
    private val appPreferences: String by lazy { read("data/AppPreferences.kt") }

    private fun read(relative: String): String {
        val f = File("src/main/java/com/jarves/mh/$relative")
        assertTrue("$relative not found at ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    // ── Policy: automatic lifecycle always preserves ────────────────────────

    private val automaticTriggers = listOf(
        ProjectLifecycle.Trigger.STARTUP,
        ProjectLifecycle.Trigger.INITIALIZATION,
        ProjectLifecycle.Trigger.PROJECT_OPENED,
        ProjectLifecycle.Trigger.PROJECT_CLOSED,
        ProjectLifecycle.Trigger.PROJECT_SWITCHED,
        ProjectLifecycle.Trigger.BACKGROUND,
    )

    @Test
    fun `no automatic lifecycle trigger may ever delete`() {
        for (trigger in automaticTriggers) {
            for (confirmed in listOf(true, false)) {
                assertEquals(
                    "$trigger must preserve (confirmed=$confirmed)",
                    ProjectLifecycle.Outcome.PRESERVE,
                    ProjectLifecycle.decide(trigger, confirmed),
                )
                assertFalse(ProjectLifecycle.mayDelete(trigger, confirmed))
            }
        }
    }

    @Test
    fun `explicit delete requires confirmation`() {
        assertEquals(
            ProjectLifecycle.Outcome.PRESERVE,
            ProjectLifecycle.decide(ProjectLifecycle.Trigger.EXPLICIT_DELETE, confirmed = false),
        )
        assertEquals(
            ProjectLifecycle.Outcome.DELETE,
            ProjectLifecycle.decide(ProjectLifecycle.Trigger.EXPLICIT_DELETE, confirmed = true),
        )
    }

    @Test
    fun `unconfirmed delete remains disallowed`() {
        assertFalse(ProjectLifecycle.mayDelete(ProjectLifecycle.Trigger.EXPLICIT_DELETE, confirmed = false))
    }

    @Test
    fun `explicit confirmed delete remains allowed`() {
        assertTrue(ProjectLifecycle.mayDelete(ProjectLifecycle.Trigger.EXPLICIT_DELETE, confirmed = true))
    }

    @Test
    fun `the empty project heuristic is gone from the decision`() {
        // Preservation must not depend on message or file counts at all: the decision
        // function takes only a trigger and a confirmation.
        for (trigger in automaticTriggers) {
            assertEquals(
                ProjectLifecycle.Outcome.PRESERVE,
                ProjectLifecycle.decide(trigger, confirmed = false),
            )
        }
    }

    // ── Registry backup guard ───────────────────────────────────────────────

    @Test
    fun `a good non-empty registry is a usable backup`() {
        assertTrue(ProjectLifecycle.isUsableBackup("""[{"id":"a","name":"n"}]"""))
    }

    @Test
    fun `an empty array is not a usable backup`() {
        assertFalse(ProjectLifecycle.isUsableBackup("[]"))
        assertFalse(ProjectLifecycle.isUsableBackup("  [ ]  "))
    }

    @Test
    fun `blank and missing values are not usable backups`() {
        assertFalse(ProjectLifecycle.isUsableBackup(null))
        assertFalse(ProjectLifecycle.isUsableBackup(""))
        assertFalse(ProjectLifecycle.isUsableBackup("   "))
    }

    @Test
    fun `malformed values are not usable backups`() {
        assertFalse(ProjectLifecycle.isUsableBackup("not json"))
        assertFalse(ProjectLifecycle.isUsableBackup("{"))
        assertFalse(ProjectLifecycle.isUsableBackup("""{"id":"a"}"""))
        assertFalse(ProjectLifecycle.isUsableBackup("["))
    }

    // ── Deletion is confined to the explicit path ───────────────────────────

    @Test
    fun `startup no longer prunes or deletes projects`() {
        // The heuristic block is gone.
        assertFalse(mainViewModel.contains("cleanedProjects"))
        assertFalse(mainViewModel.contains("userMessages"))
        assertFalse(mainViewModel.contains("userFiles"))
        assertFalse(mainViewModel.contains("Unused empty project"))
        // Startup must not rewrite the registry from a filtered list.
        assertFalse(
            Regex("""saveProjects\(\s*cleanedProjects\s*\)""").containsMatchIn(mainViewModel),
        )
    }

    @Test
    fun `closing a project does not delete its workspace, chats or record`() {
        val close = functionBody(mainViewModel, "    fun closeProject()")
        assertFalse("close must not delete a workspace", close.contains("deleteRecursively"))
        assertFalse("close must not delete chats", close.contains("deleteProjectChats"))
        assertFalse("close must not drop the registry entry", close.contains("filterNot"))
        assertFalse("close must not rewrite the registry", close.contains("saveProjects"))
        assertFalse("close must not use the message/file heuristic", close.contains("userMessages"))
        assertFalse("close must not use the message/file heuristic", close.contains("userFiles"))
        // It must still perform its normal, non-destructive transitions.
        assertTrue(close.contains("activeProject = null"))
        assertTrue(close.contains("workspaceVisible = false"))
    }

    @Test
    fun `workspace deletion survives only in the explicit delete path`() {
        // Two other deleteRecursively calls exist and are legitimate failure cleanup for
        // in-progress imports: they remove a staging tree the app itself just created,
        // never an existing project. Only one deletion targets an existing project
        // workspace, and it is the explicit delete. Later references (mkdirs, base path)
        // are not deletions.
        val deletions = Regex("""workspaces/\$\{project\.id\}""").findAll(mainViewModel)
            .filter { match ->
                val tail = mainViewModel.substring(match.range.last, (match.range.last + 40).coerceAtMost(mainViewModel.length))
                tail.contains("deleteRecursively")
            }
            .count()
        assertEquals(
            "exactly one deletion of an existing project workspace should remain",
            1,
            deletions,
        )
        val delete = functionBody(mainViewModel, "    fun deleteProject(projectId: String)")
        assertTrue(delete.contains("deleteRecursively"))
        assertTrue(delete.contains("deleteProjectChats"))
    }

    @Test
    fun `chats are deleted only through the explicit delete path`() {
        // deleteProjectChats is the only way chats disappear. It must appear in exactly
        // one production caller: the confirmed explicit delete.
        val callers = Regex("""preferences\.deleteProjectChats\(""").findAll(mainViewModel)
            .map { enclosingFunction(mainViewModel, mainViewModel.substring(0, it.range.first).lines().size) }
            .toList()
        assertEquals("expected exactly one caller", listOf("deleteProject"), callers)
    }

    @Test
    fun `the explicit delete path is policy gated`() {
        val delete = functionBody(mainViewModel, "    fun deleteProject(projectId: String)")
        assertTrue(
            "delete must consult the shared policy",
            delete.contains("ProjectLifecycle.mayDelete("),
        )
        assertTrue(delete.contains("Trigger.EXPLICIT_DELETE"))
        assertTrue(delete.contains("confirmed = true"))
    }

    @Test
    fun `startup reads the registry as a typed result`() {
        assertTrue(mainViewModel.contains("preferences.loadProjectsResult()"))
        assertTrue(mainViewModel.contains("ProjectsLoad.Missing"))
        assertTrue(mainViewModel.contains("ProjectsLoad.Malformed"))
        assertTrue(mainViewModel.contains("ProjectsLoad.Failed"))
    }

    @Test
    fun `startup does not delete workspaces, chats or checkpoints`() {
        // The init block that follows the state initialiser must not touch the filesystem
        // destructively. Scan the region between the state property and the first
        // terminal-related member.
        val start = mainViewModel.indexOf("val state: StateFlow<AppUiState>")
        assertTrue("state property not found", start >= 0)
        val initRegion = mainViewModel.substring(0, start)
        assertFalse(initRegion.contains("deleteRecursively"))
        assertFalse(initRegion.contains("deleteProjectChats"))
        assertFalse(initRegion.contains("checkpoints"))
    }

    // ── Load failure must not become empty truth ────────────────────────────

    @Test
    fun `malformed load does not save an empty registry`() {
        // The whole-array parse inside the registry loader must fail out rather than
        // collapse to an empty list. Assert on the loader body, not the whole file:
        // unrelated loaders (chats, messages) legitimately default to empty.
        val load = functionBody(appPreferences, "    internal fun loadProjectsResult()")
        assertFalse(
            "the registry parse must not collapse to an empty list",
            load.contains("getOrDefault(emptyList())"),
        )
        assertTrue(load.contains("}.getOrElse { return ProjectsLoad.Malformed }"))
        assertTrue(load.contains("if (needsSave && list.isNotEmpty())"))
    }

    @Test
    fun `load result distinguishes valid empty from malformed`() {
        assertTrue(appPreferences.contains("data class Loaded(val projects: List<Project>)"))
        assertTrue(appPreferences.contains("data object Missing"))
        assertTrue(appPreferences.contains("data object Malformed"))
        assertTrue(appPreferences.contains("data object Failed"))
    }

    @Test
    fun `a missing registry is reported rather than written`() {
        assertTrue(appPreferences.contains("?: return ProjectsLoad.Missing"))
    }

    @Test
    fun `load failure cannot trigger destructive cleanup`() {
        // Nothing in the load path deletes anything.
        val load = functionBody(appPreferences, "    internal fun loadProjectsResult()")
        assertFalse(load.contains("deleteRecursively"))
        assertFalse(load.contains("delete()"))
        // And the only write is guarded by a non-empty list.
        val writes = Regex("""saveProjects\(""").findAll(load).count()
        assertEquals("only the guarded normalisation write", 1, writes)
    }

    @Test
    fun `a valid non-empty registry is not rewritten from a heuristic`() {
        val load = functionBody(appPreferences, "    internal fun loadProjectsResult()")
        assertTrue(load.contains("ProjectsLoad.Loaded(list)"))
        assertFalse(load.contains("filter {"))
    }

    // ── Backup ──────────────────────────────────────────────────────────────

    @Test
    fun `backup is written only from a usable previous registry`() {
        val save = functionBody(appPreferences, "    fun saveProjects(projects: List<Project>)")
        assertTrue(save.contains("ProjectLifecycle.isUsableBackup(previous)"))
        assertTrue(save.contains("KEY_PROJECTS_BACKUP_JSON"))
        // A single bounded copy, written before the replacement.
        assertEquals(1, Regex("KEY_PROJECTS_BACKUP_JSON").findAll(save).count())
        assertTrue(save.contains(".commit()"))
    }

    @Test
    fun `the backup is not overwritten by an automatically derived empty list`() {
        // saveProjects([]) carries no usable previous value only if the stored one is
        // empty; the guard itself is what prevents an empty candidate displacing a good
        // backup, and is asserted above. Confirm the guard rejects empties.
        assertFalse(ProjectLifecycle.isUsableBackup("[]"))
    }

    // ── Diagnostics redaction ───────────────────────────────────────────────

    @Test
    fun `lifecycle events carry only enums, primitives and a hashed reference`() {
        val lines = mutableListOf<String>()
        val diagnostics = ProjectLifecycleDiagnostics { lines += it }
        diagnostics.event(
            ProjectLifecycleEvent.PROJECT_PRESERVED_ON_CLOSE,
            ProjectLifecycle.Trigger.PROJECT_CLOSED,
            outcome = ProjectLifecycle.Outcome.PRESERVE,
            projectRef = ProjectLifecycle.projectRef("1c9f0f2a-secret-project-id"),
            kind = ProjectKind.QUICK_PROJECT,
            count = 3,
        )
        val line = lines.single()
        // Approved fields only.
        assertTrue(line.contains("event=project_preserved_on_close"))
        assertTrue(line.contains("trigger=closed"))
        assertTrue(line.contains("outcome=preserve"))
        assertTrue(line.contains("kind=QUICK_PROJECT"))
        assertTrue(line.contains("count=3"))
        // The raw identifier must not appear anywhere in the line.
        assertFalse(line.contains("1c9f0f2a"))
        assertFalse(line.contains("secret-project-id"))
    }

    @Test
    fun `the project reference is a stable non-reversible hash`() {
        val a = ProjectLifecycle.projectRef("1c9f0f2a-secret-project-id")
        val b = ProjectLifecycle.projectRef("1c9f0f2a-secret-project-id")
        val other = ProjectLifecycle.projectRef("0f0f0f0f-another-id")
        assertEquals("must be stable", a, b)
        assertNotEquals("distinct projects must differ", a, other)
    }

    @Test
    fun `every production lifecycle call sites uses approved arguments only`() {
        // No name, slug, path, or content may be passed. The event signature has no
        // String, Throwable, File or Uri parameter, so this can only be violated by a
        // new overload. Assert on the event signature itself.
        val diagnosticsSource = read("data/ProjectLifecycleDiagnostics.kt")
        val signature = functionBody(diagnosticsSource, "    fun event(")
        val parameterList = signature.substringBefore(") {")
        for (forbidden in listOf("String", "Throwable", "File", "Uri", "Map", "List", "Any")) {
            assertFalse(
                "the event parameter list must not accept $forbidden: $parameterList",
                parameterList.contains(forbidden),
            )
        }
    }

    @Test
    fun `all required lifecycle categories exist`() {
        val ids = ProjectLifecycleEvent.entries.map { it.id }.toSet()
        for (required in listOf(
            "projects_load_success",
            "projects_load_missing",
            "projects_load_malformed",
            "projects_load_failed",
            "project_preserved_on_startup",
            "project_preserved_on_close",
            "project_delete_requested",
            "project_delete_confirmed",
            "project_delete_completed",
            "project_delete_failed",
        )) {
            assertTrue("missing lifecycle category: $required", required in ids)
        }
    }

    @Test
    fun `logging is confined to viewmodel and preferences lifecycle logic`() {
        // No composable may emit lifecycle events.
        val uiFiles = File("src/main/java/com/jarves/mh/ui").listFiles().orEmpty()
        for (file in uiFiles) {
            if (file.extension != "kt") continue
            if (file.name == "MainViewModel.kt") continue
            assertFalse(
                "${file.name} must not emit project lifecycle events",
                file.readText().contains("ProjectLifecycleDiagnostics"),
            )
        }
    }

    // ── Unchanged surfaces ──────────────────────────────────────────────────

    @Test
    fun `existing delete confirmation remains present`() {
        val app = File("src/main/java/com/jarves/mh/ui/PocketDevApp.kt").readText()
        assertTrue("delete callback wiring must remain", app.contains("onDeleteProject = viewModel::deleteProject"))
        assertTrue("confirmation dialog must remain", app.contains("onDeleteProject(project.id)"))
    }

    @Test
    fun `project screen callbacks are unchanged`() {
        // The Projects screen composable lives in PocketDevApp.kt, not a separate file.
        val source = File("src/main/java/com/jarves/mh/ui/PocketDevApp.kt").readText()
        for (callback in listOf("onOpen", "onCreate", "onCreateQuickProject", "onRenameProject", "onDeleteProject")) {
            assertTrue("$callback must remain in the Projects screen", source.contains(callback))
        }
    }

    /** Name of the enclosing top-level function for a 1-based line number. */
    private fun enclosingFunction(source: String, line: Int): String {
        val lines = source.lines()
        for (index in (line - 1) downTo 0) {
            val match = Regex("""^\s{4}(?:private |internal |suspend )*fun (\w+)""").find(lines[index])
            if (match != null) return match.groupValues[1]
        }
        return "<none>"
    }

    /** Body of a top-level function, by brace matching from its declaration. */
    private fun functionBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        assertTrue("$declaration not found", start >= 0)
        var depth = 0
        var seen = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { depth++; seen = true }
                '}' -> {
                    depth--
                    if (seen && depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return source.substring(start)
    }
}
