package org.yaqmc.android.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidUpdateCheckerTest {
    private fun release(version: String, prerelease: Boolean = false) = ReleaseCandidate(
        version = requireNotNull(YaqmcVersion.parse(version)),
        versionName = version.removePrefix("v"),
        prerelease = prerelease,
        releaseUrl = "https://github.com/ltzu929/YAQMC/releases/tag/${version.removePrefix("v")}",
        notes = "notes",
    )

    @Test
    fun `stable builds ignore prereleases and select newest stable`() {
        val selected = selectUpdate(
            requireNotNull(YaqmcVersion.parse("1.2.0")),
            listOf(
                release("1.3.0-beta.1", prerelease = true),
                release("1.2.1"),
                release("1.4.0"),
            ),
        )
        assertEquals("1.4.0", selected?.versionName)
    }

    @Test
    fun `prerelease builds ignore other preview channels`() {
        val selected = selectUpdate(
            requireNotNull(YaqmcVersion.parse("2.0.0-beta.2")),
            listOf(
                release("2.0.0-beta.1", prerelease = true),
                release("2.0.0-beta.3", prerelease = true),
                release("2.0.0-rc.1", prerelease = true),
            ),
        )
        assertEquals("2.0.0-beta.3", selected?.versionName)
    }

    @Test
    fun `preview builds can graduate to a newer stable release`() {
        val selected = selectUpdate(
            requireNotNull(YaqmcVersion.parse("0.1.4-beta.1")),
            listOf(
                release("0.1.3"),
                release("0.1.4-beta.2", prerelease = true),
                release("0.1.4-rc.1", prerelease = true),
                release("0.1.4"),
            ),
        )
        assertEquals("0.1.4", selected?.versionName)
    }

    @Test
    fun `release links belong to this project and reject upstream or lookalike URLs`() {
        assertTrue(AndroidUpdateChecker.isProjectReleaseUrl(
            "https://github.com/ltzu929/YAQMC/releases/tag/v0.1.4-beta.1",
        ))
        listOf(
            "https://github.com/YAQMC/YAQMC/releases/tag/v1.0.0",
            "https://github.com/ltzu929/YAQMC-other/releases/tag/v1.0.0",
            "https://github.com.evil.example/ltzu929/YAQMC/releases/tag/v1.0.0",
            "https://github.com@evil.example/ltzu929/YAQMC/releases/tag/v1.0.0",
            "https://user@github.com/ltzu929/YAQMC/releases/tag/v1.0.0",
            "https://github.com:443/ltzu929/YAQMC/releases/tag/v1.0.0",
            "http://github.com/ltzu929/YAQMC/releases/tag/v1.0.0",
        ).forEach { assertFalse(AndroidUpdateChecker.isProjectReleaseUrl(it), it) }
    }

    @Test
    fun `invalid or mismatched release metadata is rejected`() {
        assertNull(YaqmcVersion.parse("1.0.0-preview.1"))
        val selected = selectUpdate(
            requireNotNull(YaqmcVersion.parse("1.0.0")),
            listOf(release("1.1.0", prerelease = true)),
        )
        assertNull(selected)
    }
}
