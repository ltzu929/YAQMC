package org.yaqmc.android.nativeui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NativePreferences(val theme: String = "system", val language: String = "system") {
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    fun toggledTheme(systemDark: Boolean): String = if (isDark(systemDark)) "light" else "dark"

    fun resolvedLanguage(systemLanguages: List<String>): String {
        if (language != "system") return language
        for (candidate in systemLanguages) {
            val normalized = candidate.replace('_', '-').lowercase(Locale.ROOT)
            if (normalized == "zh" || normalized.startsWith("zh-cn") || normalized.startsWith("zh-sg")) return "zh-CN"
            if (normalized == "en" || normalized.startsWith("en-")) return "en-US"
        }
        return "en-US"
    }
}

/** Keeps the existing native-ui/theme value; each edit preserves unrelated preference keys. */
internal class NativePreferenceStore(private val storage: SharedPreferences) {
    private val _state = MutableStateFlow(NativePreferences(
        theme = normalizeTheme(read("theme")), language = normalizeLanguage(read("language")),
    ))
    val state = _state.asStateFlow()

    fun setTheme(value: String) {
        val normalized = normalizeTheme(value)
        storage.edit { putString("theme", normalized) }
        _state.value = _state.value.copy(theme = normalized)
    }

    fun setLanguage(value: String) {
        val normalized = normalizeLanguage(value)
        storage.edit { putString("language", normalized) }
        _state.value = _state.value.copy(language = normalized)
    }

    private fun read(key: String): String? = try { storage.getString(key, null) } catch (_: ClassCastException) { null }

    companion object {
        private fun normalizeTheme(value: String?) = value?.takeIf { it in setOf("system", "light", "dark") } ?: "system"
        private fun normalizeLanguage(value: String?) = value?.takeIf { it in setOf("system", "zh-CN", "en-US") } ?: "system"
    }
}

internal fun Context.withNativeLanguage(preference: NativePreferences): Context {
    val configuration = Configuration(resources.configuration)
    val languages = (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    configuration.setLocale(Locale.forLanguageTag(preference.resolvedLanguage(languages)))
    return createConfigurationContext(configuration)
}
