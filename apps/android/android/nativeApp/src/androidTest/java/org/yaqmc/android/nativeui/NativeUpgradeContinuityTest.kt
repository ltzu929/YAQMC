package org.yaqmc.android.nativeui

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.yaqmc.android.core.CoreManager
import org.yaqmc.android.core.CredentialStore

/** Run seed, install a newer APK with -r, then run verify. Uses only synthetic local data. */
class NativeUpgradeContinuityTest {
    @Test fun encryptedCredentialsAndLocalDataSurviveAnUpdate() {
        val phase = InstrumentationRegistry.getArguments().getString("upgradePhase")
        assumeTrue(phase == "seed" || phase == "verify")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation is NativeOfflineTestRunner)
        val context = instrumentation.targetContext
        assertEquals(Application::class.java, context.applicationContext.javaClass)
        assertFalse(CoreManager.isReady())
        val preferences = context.getSharedPreferences("upgrade-continuity-test", Context.MODE_PRIVATE)
        val marker = File(context.filesDir, "upgrade-continuity-test.txt")
        val vault = CredentialStore(context)
        val key = "yaqmc.test.upgrade-continuity"
        val value = "synthetic-upgrade-marker"
        if (phase == "seed") {
            vault.put(key, value)
            assertTrue(preferences.edit().putString("marker", value).putInt("uid", context.applicationInfo.uid).commit())
            marker.writeText(value)
        } else {
            assertEquals(value, vault.get(key))
            assertEquals(value, preferences.getString("marker", null))
            assertEquals(preferences.getInt("uid", -1), context.applicationInfo.uid)
            assertEquals(value, marker.readText())
            vault.remove(key)
            assertTrue(preferences.edit().clear().commit())
            assertTrue(marker.delete())
        }
        assertFalse(CoreManager.isReady())
    }
}
