package org.yaqmc.android.nativeui

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class NativePreferencesTest {

    @Test fun presetChangesSurviveRestartWithoutTouchingLyricsOrOtherPreferences() {
        val storage = MemoryPreferences(mutableMapOf("theme" to "dark", "language" to "zh-CN", "future-setting" to "keep"))
        val store = NativePreferenceStore(storage)
        store.setLyrics(NativeLyricPreferences(timingOffsetMs = 350))
        store.setLyricPresets(NativeLyricPresetPreferences().select("builtin.vinyl")
            .duplicate("custom.test-persist", "My vinyl"))
        store.setLyrics(NativeLyricPreferences(timingOffsetMs = -200))
        val restarted = NativePreferenceStore(storage).state.value
        assertEquals("custom.test-persist", restarted.lyricPresets.selectedId)
        assertEquals("vinyl", restarted.lyricPresets.current().layout)
        assertEquals(-200L, restarted.lyrics.timingOffsetMs)
        assertEquals("dark", restarted.theme)
        assertEquals("zh-CN", restarted.language)
        assertEquals("keep", storage.getString("future-setting", null))
    }

    @Test fun appearanceChangesAndResetPreserveLyricsLanguageAndUnknownPreferences() {
        val storage = MemoryPreferences(mutableMapOf("theme" to "dark", "language" to "zh-CN", "future-setting" to "keep"))
        val store = NativePreferenceStore(storage)
        store.setLyrics(NativeLyricPreferences(timingOffsetMs = 850, romanization = "hide"))
        store.setAppearance(NativeAppearancePreferences().withPalette("ocean").copy(fontScale = 120))
        assertEquals("ocean", NativePreferenceStore(storage).state.value.appearance.palette)
        store.setAppearance(NativeAppearancePreferences())
        val restart = NativePreferenceStore(storage).state.value
        assertEquals(NativeAppearancePreferences(), restart.appearance)
        assertEquals(850L, restart.lyrics.timingOffsetMs)
        assertEquals("hide", restart.lyrics.romanization)
        assertEquals("zh-CN", restart.language)
        assertEquals("dark", restart.theme)
        assertEquals("keep", storage.getString("future-setting", null))
    }

    @Test fun lyricsSurviveRestartWithoutOverwritingThemeLanguageOrUnknownKeys() {
        val storage = MemoryPreferences(mutableMapOf("theme" to "dark", "language" to "zh-CN", "future-setting" to "keep"))
        val settings = NativeLyricPreferences("hide", "show", -850, 1.3f, 500, 1.4f, "fill", false, false, false, true, 0.2f)
        val store = NativePreferenceStore(storage)
        store.setLyrics(settings)
        store.setTheme("light")
        val restarted = NativePreferenceStore(storage).state.value
        assertEquals(settings, restarted.lyrics)
        assertEquals("light", restarted.theme)
        assertEquals("zh-CN", restarted.language)
        assertEquals("keep", storage.getString("future-setting", null))
    }

    @Test fun existingNativeThemeSurvivesLanguageChangesAndNewStore() {
        val storage = MemoryPreferences(mutableMapOf("theme" to "dark", "future-setting" to "keep"))
        val store = NativePreferenceStore(storage)
        assertEquals("dark", store.state.value.theme)
        store.setLanguage("zh-CN")
        val restarted = NativePreferenceStore(storage)
        assertEquals(NativePreferences("dark", "zh-CN"), restarted.state.value)
        restarted.setTheme("light")
        assertEquals("zh-CN", NativePreferenceStore(storage).state.value.language)
        assertEquals("keep", storage.getString("future-setting", null))
    }

    @Test fun missingInvalidAndWrongTypePreferencesUseSystemDefaults() {
        assertEquals(NativePreferences(), NativePreferenceStore(MemoryPreferences()).state.value)
        val store = NativePreferenceStore(MemoryPreferences(mutableMapOf("theme" to 42, "language" to "unsupported")))
        assertEquals(NativePreferences(), store.state.value)
        store.setTheme("invalid")
        store.setLanguage("invalid")
        assertEquals(NativePreferences(), store.state.value)
    }

    @Test fun themeShortcutUsesResolvedSystemModeAndSavesExplicitOpposite() {
        assertEquals("light", NativePreferences().toggledTheme(systemDark = true))
        assertEquals("dark", NativePreferences().toggledTheme(systemDark = false))
        assertFalse(NativePreferences(theme = "light").isDark(systemDark = true))
        assertTrue(NativePreferences(theme = "dark").isDark(systemDark = false))
    }

    @Test fun localeMatchingRetainsOldClientOrderAndFallback() {
        val system = NativePreferences()
        assertEquals("zh-CN", system.resolvedLanguage(listOf("fr-FR", "zh_SG", "en-US")))
        assertEquals("en-US", system.resolvedLanguage(listOf("en-GB", "zh-CN")))
        assertEquals("en-US", system.resolvedLanguage(listOf("zh-TW", "ja-JP")))
        assertEquals("en-US", system.resolvedLanguage(emptyList()))
        assertEquals("zh-CN", NativePreferences(language = "zh-CN").resolvedLanguage(listOf("en-US")))
    }

    @Test fun libraryChildrenShareOneRootWhileOtherDestinationsKeepTheirIdentity() {
        assertEquals(listOf("home", "discover", "library", "search"), nativeRootDestinations)
        listOf("favorites", "account-playlists", "account-playlist", "recent", "statistics").forEach {
            assertEquals("library", selectedRoot(it))
        }
        assertEquals("search", selectedRoot("search"))
        assertEquals("settings", selectedRoot("settings"))
    }
}

/** In-memory implementation of the Android storage contract; no Android runtime is needed. */
private class MemoryPreferences(private val values: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = if (values.containsKey(key)) values[key] as String? else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { updates[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { updates[key] = values?.toMutableSet() }
        override fun putInt(key: String, value: Int) = apply { updates[key] = value }
        override fun putLong(key: String, value: Long) = apply { updates[key] = value }
        override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
        override fun remove(key: String) = apply { updates[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
