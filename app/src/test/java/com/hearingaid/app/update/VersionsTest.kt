package com.hearingaid.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun `parses tags with or without v and pads to three parts`() {
        assertEquals(listOf(1, 2, 0), Versions.parse("v1.2"))
        assertEquals(listOf(1, 2, 3), Versions.parse("1.2.3"))
        assertEquals(listOf(2, 0, 1), Versions.parse("v2.0.1-beta+7"))
        assertNull(Versions.parse("latest"))
        assertNull(Versions.parse(""))
    }

    @Test
    fun `compares numerically, not as text`() {
        assertTrue(Versions.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0.0", "1.99.99"))
        assertFalse(Versions.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("0.9.0", "1.0.0"))
        assertFalse(Versions.isNewer("nonsense", "1.0.0"))
    }

    @Test
    fun `apk name always uses three version parts`() {
        assertEquals("hearingaid-1.2.0.apk", Updater.apkName("v1.2"))
    }
}
