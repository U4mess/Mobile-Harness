package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the guest environment used to launch the DeepSeek Harness CLI. A regression here
 * is invisible until a real task runs, because `dsh --help` and a clean boot both succeed
 * even when the native addon fails to load during host preparation.
 */
class DshLaunchEnvironmentTest {

    /**
     * Without this, `node-addon-native-custom-loader` materialises prebuilts into a cache
     * whose hard link PRoot turns into a symlink to a `*.tmp` file; `require()` then treats
     * the ELF as JavaScript and fails with "No usable native binding found".
     */
    @Test
    fun `native cache is disabled so the addon loads from node_modules`() {
        assertEquals("1", dshGuestEnvironment()["NARB_DISABLE_NATIVE_CACHE"])
    }

    @Test
    fun `existing DSH settings are preserved`() {
        val environment = dshGuestEnvironment()

        assertEquals("/root/.dsh", environment["DSH_HOME"])
        assertEquals("danger-full-access", environment["DSH_PERMISSION_MODE"])
    }

    /** Credentials are added by the caller; the shared base must never carry one. */
    @Test
    fun `the base environment carries no credential`() {
        val environment = dshGuestEnvironment()

        assertFalse(environment.containsKey("DEEPSEEK_API_KEY"))
        assertFalse(environment.containsKey(DshRuntimeBridge.FALLBACK_KEY_ENV))
        assertTrue(environment.values.none { it.startsWith("sk-") })
    }

    /** The caller's key must survive the base map being applied first. */
    @Test
    fun `a caller supplied credential is not overwritten`() {
        val environment = linkedMapOf<String, String>().apply {
            putAll(dshGuestEnvironment())
            put("DEEPSEEK_API_KEY", "test-key")
        }

        assertEquals("test-key", environment["DEEPSEEK_API_KEY"])
        assertEquals("1", environment["NARB_DISABLE_NATIVE_CACHE"])
    }
}
