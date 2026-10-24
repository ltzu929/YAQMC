package org.yaqmc.android.nativeui

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

@UnstableApi
internal class NativeDevicePreferences : ExternalResource() {
        private var original: Map<String, *> = emptyMap<String, Any>()
        override fun before() {
            val storage = context().getSharedPreferences("native-ui", Context.MODE_PRIVATE)
            original = storage.all.filterKeys { it == "theme" || it == "language" }
            storage.edit().putString("theme", "light").putString("language", "en-US").commit()
            // Some ROMs block ActivityScenario's first launch from a background process.
            // A shell foreground launch avoids changing the device's permission settings.
            val component = "${context().packageName}/${NativeActivity::class.java.name}"
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $component",
            )
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        override fun after() {
            val editor = context().getSharedPreferences("native-ui", Context.MODE_PRIVATE).edit().remove("theme").remove("language")
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            editor.commit()
        }
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
}
