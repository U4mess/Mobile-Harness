package com.jarves.mh

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MIT notice shipped inside the APK is a verbatim copy of the repository's
 * LICENSE. This keeps the bundled text from drifting away from the licence that
 * actually governs the code.
 *
 * Unit tests run with the app module directory as the working directory.
 */
class BundledLicenseTest {
    private val rootLicense = File("../LICENSE")
    private val bundledLicense = File("src/main/assets/licenses/MOBILE-HARNESS-MIT.txt")

    @Test
    fun bundledLicenseMatchesRepositoryLicenseByteForByte() {
        assertTrue("Repository LICENSE not found at ${rootLicense.absolutePath}", rootLicense.isFile)
        assertTrue("Bundled licence not found at ${bundledLicense.absolutePath}", bundledLicense.isFile)

        val expected = rootLicense.readBytes()
        val actual = bundledLicense.readBytes()

        assertTrue("Repository LICENSE is empty", expected.isNotEmpty())
        assertArrayEquals(
            "app/src/main/assets/licenses/MOBILE-HARNESS-MIT.txt has drifted from LICENSE",
            expected,
            actual,
        )
    }

    @Test
    fun bundledLicenseKeepsTheOriginalCopyrightWording() {
        val text = bundledLicense.readText()
        assertTrue(
            "The original copyright line must be preserved exactly",
            text.contains("Copyright (c) 2026 Mobile Harness Contributors"),
        )
        assertTrue("MIT permission grant must be preserved", text.contains("Permission is hereby granted, free of charge"))
        assertTrue("MIT warranty disclaimer must be preserved", text.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
    }
}
