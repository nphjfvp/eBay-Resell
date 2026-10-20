package de.dealcheck.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun comparesVersions() {
        assertTrue(Version.isNewer("v1.1.0", "1.0.0"))
        assertTrue(Version.isNewer("1.0.10", "1.0.9"))
        assertTrue(Version.isNewer("2.0", "1.9.9"))
        assertTrue(Version.isNewer("v1.0.0", "1.0.0-dev.42"))
        assertFalse(Version.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Version.isNewer("v1.0.0", "1.1.0-dev.3"))
        assertFalse(Version.isNewer("1.0.0-dev.50", "1.0.0"))
    }

    @Test
    fun parsesRelease() {
        val json = """
            {"tag_name":"v1.2.0","draft":false,"prerelease":false,"body":"Neu: Updates",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
                       {"name":"DealCheck-v1.2.0.apk","browser_download_url":"https://x/DealCheck-v1.2.0.apk","size":12345}]}
        """.trimIndent()
        val r = UpdateManager.parseRelease(json)!!
        assertEquals("1.2.0", r.version)
        assertEquals("https://x/DealCheck-v1.2.0.apk", r.apkUrl)
        assertEquals(12345L, r.apkSize)
        assertEquals("Neu: Updates", r.notes)
    }

    @Test
    fun ignoresReleaseWithoutApkOrPrerelease() {
        assertNull(UpdateManager.parseRelease("""{"tag_name":"v1.0.0","assets":[]}"""))
        assertNull(UpdateManager.parseRelease("""{"tag_name":"v1.0.0","prerelease":true,"assets":[{"name":"a.apk"}]}"""))
    }
}
