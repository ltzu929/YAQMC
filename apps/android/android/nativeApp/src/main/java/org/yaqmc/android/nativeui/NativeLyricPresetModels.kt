package org.yaqmc.android.nativeui

import org.json.JSONArray
import org.json.JSONObject

/** Small native preset contract. Built-in definitions remain immutable; edits are stored as overrides. */
internal data class NativeLyricPreset(
    val id: String,
    val name: String = "",
    val layout: String = "classic",
    val lyricsFraction: Float = 0.55f,
    val artworkScale: Float = 1f,
) {
    fun normalized(): NativeLyricPreset = copy(
        name = name.trim().take(48),
        layout = layout.takeIf { it in setOf("classic", "immersive", "vinyl") } ?: "classic",
        lyricsFraction = lyricsFraction.finiteOr(0.55f).coerceIn(0.35f, 0.75f),
        artworkScale = artworkScale.finiteOr(1f).coerceIn(0.7f, 1.35f),
    )

    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("layout", layout)
        .put("lyricsFraction", lyricsFraction).put("artworkScale", artworkScale)

    companion object {
        fun parse(value: JSONObject): NativeLyricPreset = NativeLyricPreset(
            value.optString("id", ""), value.optString("name", ""), value.optString("layout", "classic"),
            value.optDouble("lyricsFraction", 0.55).toFloat(), value.optDouble("artworkScale", 1.0).toFloat(),
        ).normalized()
    }
}

internal val nativeBuiltinLyricPresets = listOf(
    NativeLyricPreset("builtin.classic"),
    NativeLyricPreset("builtin.immersive", layout = "immersive", lyricsFraction = 0.7f),
    NativeLyricPreset("builtin.vinyl", layout = "vinyl", lyricsFraction = 0.5f),
)

/** Saved separately from lyric timing and effects: resetting those must not erase the user's layout. */
internal data class NativeLyricPresetPreferences(
    val selectedId: String = "builtin.classic",
    val overrides: Map<String, NativeLyricPreset> = emptyMap(),
    val custom: List<NativeLyricPreset> = emptyList(),
) {
    fun normalized(): NativeLyricPresetPreferences {
        val builtins = nativeBuiltinLyricPresets.map { it.id }.toSet()
        val validCustom = custom.asSequence().map { it.normalized() }
            .filter { it.id.matches(Regex("custom\\.[A-Za-z0-9-]{1,64}")) }
            .distinctBy { it.id }.take(24).toList()
        val validOverrides = overrides.filterKeys { it in builtins }.mapValues { (id, preset) -> preset.normalized().copy(id = id) }
        val validIds = builtins + validCustom.map { it.id }
        return copy(
            selectedId = selectedId.takeIf { it in validIds } ?: "builtin.classic",
            overrides = validOverrides,
            custom = validCustom,
        )
    }

    fun current(): NativeLyricPreset {
        val state = normalized()
        return state.custom.find { it.id == state.selectedId }
            ?: state.overrides[state.selectedId]
            ?: nativeBuiltinLyricPresets.firstOrNull { it.id == state.selectedId }
            ?: nativeBuiltinLyricPresets.first()
    }

    fun select(id: String): NativeLyricPresetPreferences = copy(selectedId = id).normalized()

    fun save(preset: NativeLyricPreset): NativeLyricPresetPreferences {
        val state = normalized()
        val edited = preset.normalized()
        if (edited.id != state.selectedId) return state
        return if (edited.id.startsWith("builtin.")) {
            state.copy(overrides = state.overrides + (edited.id to edited)).normalized()
        } else {
            state.copy(custom = state.custom.map { if (it.id == edited.id) edited else it }).normalized()
        }
    }

    fun duplicate(id: String, name: String): NativeLyricPresetPreferences {
        val state = normalized()
        if (state.custom.size >= 24 || !id.matches(Regex("custom\\.[A-Za-z0-9-]{1,64}")) ||
            state.custom.any { it.id == id }) return state
        val item = state.current().copy(id = id, name = name).normalized()
        return state.copy(custom = state.custom + item, selectedId = id)
    }

    fun reset(): NativeLyricPresetPreferences {
        val state = normalized()
        return if (state.selectedId.startsWith("builtin."))
            state.copy(overrides = state.overrides - state.selectedId)
        else state
    }

    fun deleteCustom(): NativeLyricPresetPreferences {
        val state = normalized()
        return if (state.selectedId.startsWith("custom."))
            state.copy(custom = state.custom.filterNot { it.id == state.selectedId }, selectedId = "builtin.classic")
        else state
    }

    fun json(): String {
        val state = normalized()
        val overridden = JSONObject()
        state.overrides.forEach { (id, value) -> overridden.put(id, value.json()) }
        val customArray = JSONArray()
        state.custom.forEach { customArray.put(it.json()) }
        return JSONObject().put("schemaVersion", 2).put("selectedId", state.selectedId)
            .put("overrides", overridden).put("custom", customArray).toString()
    }

    companion object {
        fun decode(source: String?): NativeLyricPresetPreferences = runCatching {
            val value = JSONObject(source ?: "{}")
            val overridden = mutableMapOf<String, NativeLyricPreset>()
            val overrides = value.optJSONObject("overrides") ?: JSONObject()
            val keys = overrides.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                overrides.optJSONObject(id)?.let { overridden[id] = NativeLyricPreset.parse(it) }
            }
            val custom = mutableListOf<NativeLyricPreset>()
            val items = value.optJSONArray("custom") ?: JSONArray()
            for (index in 0 until minOf(items.length(), 64))
                items.optJSONObject(index)?.let { custom += NativeLyricPreset.parse(it) }
            NativeLyricPresetPreferences(value.optString("selectedId", "builtin.classic"), overridden, custom).normalized()
        }.getOrDefault(NativeLyricPresetPreferences())
    }
}
