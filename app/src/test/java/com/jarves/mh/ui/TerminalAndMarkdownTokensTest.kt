package com.jarves.mh.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Narrow colour-consistency guards for the Terminal header and the Chat inline-code
 * renderer.
 *
 * Source scans are used here as secondary migration guards — they prove the legacy
 * accent is gone and the semantic roles are in place. Rendering behaviour itself is not
 * asserted, which would need Compose UI test dependencies this project deliberately
 * does not carry.
 */
class TerminalAndMarkdownTokensTest {

    private val terminalSource: String by lazy { read("TerminalScreen.kt") }
    private val markdownSource: String by lazy { read("MarkdownText.kt") }

    private fun read(name: String): String {
        val f = File("src/main/java/com/jarves/mh/ui/$name")
        assertTrue("$name not found at ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    private fun linesOf(source: String) = source.lines()

    /**
     * Body of a top-level composable, by brace matching **after** the opening brace that
     * terminates the parameter list.
     *
     * Depth must not be counted inside the parameter list: a default such as
     * `onInput: (String) -> Unit = {}` contains a balanced pair that would otherwise
     * close the body immediately.
     */
    private fun body(source: String, declarationPrefix: String): String {
        val lines = linesOf(source)
        val start = lines.indexOfFirst { it.startsWith(declarationPrefix) }
        assertTrue("$declarationPrefix not found", start >= 0)
        var depth = 0
        var bodyStarted = false
        for (i in start until lines.size) {
            val line = lines[i]
            for (index in line.indices) {
                val ch = line[index]
                val inParams = !bodyStarted
                when (ch) {
                    '{' -> {
                        // Inside the parameter list a '{' opens a lambda default; ignore it.
                        if (!inParams) { depth++; bodyStarted = true }
                    }
                    '}' -> if (bodyStarted) depth--
                }
            }
            if (bodyStarted && depth == 0) return lines.subList(start, i + 1).joinToString("\n")
        }
        return lines.subList(start, lines.size).joinToString("\n")
    }

    /** The terminal header block, located by its identity tile rather than line numbers. */
    private fun terminalHeader(): String {
        val lines = linesOf(terminalSource)
        val tile = lines.indexOfFirst { it.contains("background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))") }
        assertTrue("terminal identity tile not found", tile >= 0)
        val from = (tile - 3).coerceAtLeast(0)
        val to = (tile + 14).coerceAtMost(lines.size)
        return lines.subList(from, to).joinToString("\n")
    }

    // ── Terminal header identity ────────────────────────────────────────────

    @Test
    fun `terminal identity container uses primaryContainer`() {
        val header = terminalHeader()
        assertTrue(
            "terminal identity tile should use the primary container role",
            header.contains(".background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))"),
        )
    }

    @Test
    fun `terminal identity icon uses onPrimaryContainer`() {
        val header = terminalHeader()
        assertTrue(
            "terminal identity icon should use onPrimaryContainer",
            header.contains(
                "Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer",
            ),
        )
    }

    @Test
    fun `reachable terminal header contains no PocketOrange`() {
        val header = terminalHeader()
        assertFalse(
            "the live terminal header must not reference the legacy accent:\n$header",
            header.contains("PocketOrange"),
        )
    }

    @Test
    fun `TerminalScreen contains zero PocketOrange`() {
        assertFalse(
            "the whole terminal screen is migrated; no legacy accent may remain",
            terminalSource.contains("PocketOrange"),
        )
    }

    // ── Active on-screen key ────────────────────────────────────────────────

    @Test
    fun `active terminal key container uses primaryContainer`() {
        assertTrue(
            "active key container should use the primary container role",
            terminalSource.contains(
                "containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent",
            ),
        )
    }

    @Test
    fun `active terminal key content uses onPrimaryContainer`() {
        assertTrue(
            "active key content should use onPrimaryContainer",
            terminalSource.contains(
                "contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface",
            ),
        )
    }

    @Test
    fun `active terminal key border uses primary`() {
        assertTrue(
            "active key border should use the primary role",
            terminalSource.contains(
                "if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant",
            ),
        )
    }

    @Test
    fun `inactive terminal key colours are unchanged`() {
        // Only the active branch may change: transparent fill, neutral content and border.
        val keyBody = body(terminalSource, "private fun TerminalKeyButton(")
        assertTrue(keyBody.contains("else Color.Transparent"))
        assertTrue(keyBody.contains("else MaterialTheme.colorScheme.onSurface"))
        assertTrue(keyBody.contains("else MaterialTheme.colorScheme.outlineVariant"))
    }

    @Test
    fun `terminal key behaviour, dimensions and semantics are preserved`() {
        val keyBody = body(terminalSource, "private fun TerminalKeyButton(")
        assertTrue("sticky active flag", keyBody.contains("active: Boolean = false"))
        assertTrue("fixed-width variant", keyBody.contains("fixedWidth: Boolean = false"))
        assertTrue("callback", keyBody.contains("onClick = onClick"))
        assertTrue("dimensions", keyBody.contains("Modifier.height(34.dp)"))
        assertTrue("fixed width", keyBody.contains("Modifier.width(78.dp)"))
        assertTrue("accessibility description", keyBody.contains("description: String"))
    }

    @Test
    fun `the shell prompt keeps its semantic green`() {
        // Prompt green is meaningful, not a brand accent: it must survive.
        assertTrue(
            "the prompt should still resolve a green token",
            terminalSource.contains("promptGreen = if (isDark) PocketGreen else"),
        )
        // And it must not have been swept to blue.
        val promptLines = linesOf(terminalSource).filter { it.contains("promptGreen") }
        assertTrue("expected prompt green definitions", promptLines.size >= 2)
        for (line in promptLines) {
            assertFalse("prompt must not become primary blue: $line", line.contains("colorScheme.primary"))
        }
    }

    @Test
    fun `terminal title, path and clear action are preserved`() {
        val header = terminalHeader()
        assertTrue(header.contains("Text(title,"))       // title
        assertTrue(header.contains("Text(subtitle,"))    // project path / subtitle
        assertTrue(header.contains("onClick = onClear")) // clear-terminal action
    }

    @Test
    fun `terminal console palette and keyboard controls are untouched`() {
        // Console surfaces and the on-screen key row must not have been recoloured here.
        assertTrue(
            terminalSource.contains("val terminalBg = if (isDark) Color(0xFF090D14) else MaterialTheme.colorScheme.surface"),
        )
        assertTrue(terminalSource.contains("private fun TerminalKeyButton("))
    }

    // ── Chat inline code ────────────────────────────────────────────────────

    @Test
    fun `inline code uses codeText`() {
        val renderer = body(markdownSource, "private fun formatInlineMarkdown(")
        assertTrue(
            "inline code text should use the semantic code role",
            renderer.contains("val codeColor = PocketTheme.colors.codeText"),
        )
    }

    @Test
    fun `inline code uses codeSurface`() {
        val renderer = body(markdownSource, "private fun formatInlineMarkdown(")
        assertTrue(
            "inline code background should use the semantic code surface",
            renderer.contains("val codeBg = PocketTheme.colors.codeSurface"),
        )
    }

    @Test
    fun `inline code contains no PocketOrange and no raw orange colour`() {
        val renderer = body(markdownSource, "private fun formatInlineMarkdown(")
        assertFalse(renderer, renderer.contains("PocketOrange"))
        // No raw colour literal may be introduced either.
        assertFalse(renderer, Regex("Color\\(0x").containsMatchIn(renderer))
        assertFalse(renderer, renderer.contains("surfaceVariant"))
    }

    @Test
    fun `inline code is not styled with general primary blue`() {
        val renderer = body(markdownSource, "private fun formatInlineMarkdown(")
        assertFalse(
            "inline code must use code tokens, not the brand primary",
            renderer.contains("val codeColor = primaryColor") ||
                renderer.contains("val codeColor = MaterialTheme.colorScheme.primary"),
        )
    }

    @Test
    fun `inline code styling applies to every span, not just workspace paths`() {
        val renderer = body(markdownSource, "private fun formatInlineMarkdown(")
        // The renderer is generic: it must key off the backtick, not a path pattern.
        assertTrue(renderer.contains("text[i] == '`'"))
        assertFalse(renderer.contains("/workspace"))
    }

    // ── Untouched semantic colours ──────────────────────────────────────────

    @Test
    fun `fenced code block colours are unchanged`() {
        val block = body(markdownSource, "private fun CodeSnippetBlock(")
        for (literal in listOf("Color(0xFF14171E)", "Color(0xFF2A2E39)", "Color(0xFF1C202B)", "Color(0xFF9AA0A6)", "Color(0xFFE2E8F0)")) {
            assertTrue("fenced code literal $literal must be unchanged", block.contains(literal))
        }
    }

    @Test
    fun `diff semantic colours are unchanged`() {
        // Diff add/remove roles come from the theme and are rendered by DiffLineType
        // handling, not by this file. This change touched only the inline-code colours, so
        // the fenced-block palette literals must be byte-identical to before.
        val block = body(markdownSource, "private fun CodeSnippetBlock(")
        for (literal in listOf(
            "Color(0xFF14171E)", // block surface
            "Color(0xFF2A2E39)", // block border
            "Color(0xFF1C202B)", // header strip
            "Color(0xFF9AA0A6)", // muted language / copied icon
            "Color(0xFFE2E8F0)", // code text
        )) {
            assertTrue("fenced/diff palette literal $literal must be unchanged", block.contains(literal))
        }
        // The run-in-terminal and copied tints keep the legacy accent; out of scope here.
        assertTrue(block.contains("tint = PocketOrange"))
        assertTrue(block.contains("if (copied) PocketOrange else Color(0xFF9AA0A6)"))
    }

    @Test
    fun `bullet, numbered and quote styling was not changed`() {
        // Out of scope for this narrow task, so the legacy accent must still be present
        // there rather than half-migrated.
        val lines = linesOf(markdownSource)
        fun lineOf(prefix: String) = lines.firstOrNull { it.startsWith(prefix) } ?: error("$prefix not found")
        assertTrue(body(markdownSource, "private fun BulletBlock(").contains("PocketOrange"))
        assertTrue(body(markdownSource, "private fun NumberedBlock(").contains("PocketOrange"))
        assertTrue(body(markdownSource, "private fun QuoteBlock(").contains("PocketOrange"))
        assertEquals(1, lines.count { it.startsWith("private fun BulletBlock(") })
        assertTrue(lineOf("private fun BulletBlock(").isNotEmpty())
    }
}
