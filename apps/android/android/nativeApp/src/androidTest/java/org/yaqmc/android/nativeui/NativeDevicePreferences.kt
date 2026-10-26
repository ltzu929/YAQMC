package org.yaqmc.android.nativeui

import android.app.Application
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import org.yaqmc.android.core.CoreManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.rules.ExternalResource

@UnstableApi
internal class NativeDevicePreferences(private val activityClass: Class<out ComponentActivity> = NativeActivity::class.java) : ExternalResource() {
        private val keys = setOf("theme", "language", "lyrics", "appearance")
        private var original: Map<String, *> = emptyMap<String, Any>()
        override fun before() {
            assertOfflineCoreInactive()
            val storage = context().getSharedPreferences("native-ui", Context.MODE_PRIVATE)
            original = storage.all.filterKeys { it in keys }
            storage.edit().putString("theme", "light").putString("language", "en-US").remove("lyrics").remove("appearance").commit()
            // Some ROMs block ActivityScenario's first launch from a background process.
            // A shell foreground launch avoids changing the device's permission settings.
            val component = "${context().packageName}/${activityClass.name}"
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $component",
            )
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        override fun after() {
            val editor = context().getSharedPreferences("native-ui", Context.MODE_PRIVATE).edit()
            keys.forEach { editor.remove(it) }
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            editor.commit()
            assertOfflineCoreInactive()
        }
    private fun assertOfflineCoreInactive() {
        if (InstrumentationRegistry.getInstrumentation() is NativeOfflineTestRunner) {
            assertEquals(Application::class.java, context().applicationContext.javaClass)
            assertFalse("Offline UI tests must not initialize the real Rust Core", CoreManager.isReady())
        }
    }
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
}
