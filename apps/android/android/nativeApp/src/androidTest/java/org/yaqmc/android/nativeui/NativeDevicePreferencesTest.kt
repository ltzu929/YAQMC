package org.yaqmc.android.nativeui

import androidx.activity.ComponentActivity
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@UnstableApi
class NativeDevicePreferencesTest {
    @get:Rule val isolation: org.junit.rules.TestRule = NativeDevicePreferences(ComponentActivity::class.java)

    @Test fun existingPresetIsIsolatedAndRestoredEvenWhenTestFails() {
        val storage = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0)
        val existing = NativeLyricPresetPreferences().select("builtin.vinyl").duplicate("custom.existing", "Existing layout")
        storage.edit().putString("lyric-presets", existing.json()).commit()
        val original = storage.all.toMap()
        for (fail in listOf(false, true)) {
            val failure = AssertionError("Intentional test failure")
            val body = object : Statement() {
                override fun evaluate() {
                    assertFalse(storage.contains("lyric-presets"))
                    val store = NativePreferenceStore(storage)
                    assertEquals(NativeLyricPresetPreferences(), store.state.value.lyricPresets)
                    store.setLyricPresets(existing.duplicate("custom.temporary", "Temporary copy"))
                    if (fail) throw failure
                }
            }
            try {
                NativeDevicePreferences(ComponentActivity::class.java)
                    .apply(body, Description.createTestDescription(javaClass, "preset isolation")).evaluate()
                assertFalse(fail)
            } catch (error: AssertionError) {
                assertTrue(fail)
                assertSame(failure, error)
            }
            assertEquals(original, storage.all)
        }
    }
}
