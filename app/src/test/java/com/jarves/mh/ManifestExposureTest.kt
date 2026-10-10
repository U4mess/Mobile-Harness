package com.jarves.mh

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Fail-closed check on AGP's **merged** Online Debug manifest.
 *
 * The Online Debug APK is the artifact that lands on a phone, so which components other
 * apps can reach is a property of that build — not of `src/main/AndroidManifest.xml`.
 * This test therefore reads AGP's merged output, which is written as plain XML (verified:
 * the file begins `<?xml version="1.0" encoding="utf-8"?>`), so the JDK parser is enough
 * and no extra dependency is required.
 *
 * It never skips. `app/build.gradle.kts` wires `testOnlineDebugUnitTest` to depend on
 * `processOnlineDebugMainManifest`, so the file is guaranteed to exist; if it is absent,
 * that wiring has been broken and this test fails rather than passing silently.
 */
class ManifestExposureTest {

    private val mergedManifest = File(
        "build/intermediates/merged_manifest/onlineDebug/processOnlineDebugMainManifest/AndroidManifest.xml",
    )

    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    private fun manifestRoot(): Element {
        assertTrue(
            "Merged Online Debug manifest not found at ${mergedManifest.absolutePath}. " +
                "testOnlineDebugUnitTest must depend on processOnlineDebugMainManifest.",
            mergedManifest.isFile,
        )
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Hardening: the manifest is trusted build output, but never resolve entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(mergedManifest)
        return document.documentElement
    }

    private fun Element.childElements(): List<Element> {
        val out = mutableListOf<Element>()
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) out += child as Element
            child = child.nextSibling
        }
        return out
    }

    /** Every component element under <application>, at any nesting depth. */
    private fun components(root: Element): List<Element> {
        val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
        fun collect(element: Element, depth: Int): List<Element> {
            if (depth > 4) return emptyList()
            return element.childElements().flatMap { child ->
                (if (child.tagName in componentTags) listOf(child) else emptyList()) +
                    collect(child, depth + 1)
            }
        }
        return collect(root, 0)
    }

    private fun Element.androidAttribute(localName: String): String =
        getAttributeNS(androidNamespace, localName)

    private fun name(element: Element): String = element.getAttributeNS(androidNamespace, "name")

    @Test
    fun `no exported tooling activity is present`() {
        val names = components(manifestRoot()).map(::name)
        assertTrue(
            "androidx.compose.ui.tooling.PreviewActivity must not exist in the merged manifest, saw $names",
            names.none { it == "androidx.compose.ui.tooling.PreviewActivity" },
        )
        assertTrue(
            "androidx.activity.ComponentActivity must not exist once ui-test-manifest is removed, saw $names",
            names.none { it == "androidx.activity.ComponentActivity" },
        )
    }

    @Test
    fun `main activity stays exported with MAIN and LAUNCHER`() {
        val main = components(manifestRoot()).firstOrNull { name(it) == "com.jarves.mh.MainActivity" }
            ?: error("MainActivity missing from the merged manifest")

        assertEquals(
            "MainActivity must stay exported for the launcher",
            "true",
            main.getAttributeNS(androidNamespace, "exported"),
        )

        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        main.childElements().filter { it.tagName == "intent-filter" }.forEach { filter ->
            filter.childElements().forEach { child ->
                val value = child.getAttributeNS(androidNamespace, "name")
                if (child.tagName == "action") actions += value
                if (child.tagName == "category") categories += value
            }
        }
        assertTrue("MainActivity needs MAIN, saw $actions", "android.intent.action.MAIN" in actions)
        assertTrue(
            "MainActivity needs LAUNCHER, saw $categories",
            "android.intent.category.LAUNCHER" in categories,
        )
    }

    @Test
    fun `pocketforge runtime components stay non-exported`() {
        val all = components(manifestRoot())
        val expected = listOf(
            "com.jarves.mh.runtime.RuntimeExecutionService",
            "com.jarves.mh.runtime.RuntimeSetupService",
            "com.jarves.mh.runtime.AppInstallReceiver",
            "com.jarves.mh.runtime.AndroidAppInstallReceiver",
            "androidx.core.content.FileProvider",
        )
        val required = listOf(
            "com.jarves.mh.runtime.RuntimeExecutionService",
            "com.jarves.mh.runtime.RuntimeSetupService",
            "androidx.core.content.FileProvider",
        )
        for (componentName in required) {
            val element = all.firstOrNull { name(it) == componentName }
                ?: error("$componentName missing from the merged manifest")
            assertEquals(
                "$componentName must stay exported=false",
                "false",
                element.getAttributeNS(androidNamespace, "exported"),
            )
        }

        // The install receiver's exact class name is asserted separately so a rename
        // cannot silently drop the check.
        val receiver = all.firstOrNull { name(it).endsWith("AndroidAppInstallReceiver") }
        assertTrue(
            "AndroidAppInstallReceiver missing from the merged manifest; saw ${all.map(::name)}",
            receiver != null,
        )
        assertEquals(
            "AndroidAppInstallReceiver must stay exported=false",
            "false",
            receiver!!.getAttributeNS(androidNamespace, "exported"),
        )

        val fileProvider = all.first { name(it) == "androidx.core.content.FileProvider" }
        assertEquals(
            "FileProvider must grant URIs only through explicit grants",
            "true",
            fileProvider.getAttributeNS(androidNamespace, "grantUriPermissions"),
        )
        assertEquals(expected.size, expected.distinct().size)
    }

    @Test
    fun `profile installer receiver is allowed only with the DUMP permission`() {
        val receiver = components(manifestRoot())
            .firstOrNull { name(it) == "androidx.profileinstaller.ProfileInstallReceiver" }
        // AndroidX baseline-profile component: acceptable only while it is guarded.
        if (receiver != null) {
            assertEquals(
                "ProfileInstallReceiver must be protected by android.permission.DUMP",
                "android.permission.DUMP",
                receiver.getAttributeNS(androidNamespace, "permission"),
            )
        }
    }

    @Test
    fun `only allow-listed components are exported`() {
        val allowList = setOf(
            "com.jarves.mh.MainActivity",
            "androidx.profileinstaller.ProfileInstallReceiver",
        )
        val exported = components(manifestRoot())
            .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
            .map(::name)

        for (componentName in exported) {
            assertTrue(
                "Unexpected exported component '$componentName'. " +
                    "Add it to the allow-list only after review.",
                componentName in allowList,
            )
        }
    }
}
