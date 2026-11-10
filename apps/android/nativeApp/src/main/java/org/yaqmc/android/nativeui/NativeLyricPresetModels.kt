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
    val showArtwork: Boolean = true,
    val showMetadata: Boolean = true,
    val metadataFirst: Boolean = false,
    // Relative offsets within the controls column, shared by offline preview and live playback.
    val artworkOffsetX: Float = 0f,
    val artworkOffsetY: Float = 0f,
    val metadataOffsetX: Float = 0f,
    val metadataOffsetY: Float = 0f,
    val metadataScale: Float = 1f,
    val artworkLocked: Boolean = false,
    val metadataLocked: Boolean = false,
    val artworkOnTop: Boolean = false,
    val lyricsOffsetX: Float = 0f,
    val lyricsOffsetY: Float = 0f,
    val lyricsScale: Float = 1f,
    val lyricsLocked: Boolean = false,
    val transportOffsetX: Float = 0f,
    val transportOffsetY: Float = 0f,
    val transportScale: Float = 1f,
    val transportLocked: Boolean = false,
) {
    fun normalized(): NativeLyricPreset = copy(
        name = name.trim().take(48),
        layout = layout.takeIf { it in setOf("classic", "immersive", "vinyl") } ?: "classic",
        lyricsFraction = lyricsFraction.finiteOr(0.55f).coerceIn(0.35f, 0.75f),
        artworkScale = artworkScale.finiteOr(1f).coerceIn(0.7f, 1.35f),
        artworkOffsetX = nativeBoundLayerOffset(artworkOffsetX),
        artworkOffsetY = nativeBoundLayerOffset(artworkOffsetY),
        metadataOffsetX = nativeBoundLayerOffset(metadataOffsetX),
        metadataOffsetY = nativeBoundLayerOffset(metadataOffsetY),
        metadataScale = metadataScale.finiteOr(1f).coerceIn(0.7f, 1.35f),
        lyricsOffsetX = nativeBoundLayerOffset(lyricsOffsetX),
        lyricsOffsetY = nativeBoundLayerOffset(lyricsOffsetY),
        lyricsScale = lyricsScale.finiteOr(1f).coerceIn(0.7f, 1.35f),
        transportOffsetX = nativeBoundLayerOffset(transportOffsetX),
        transportOffsetY = nativeBoundLayerOffset(transportOffsetY),
        transportScale = transportScale.finiteOr(1f).coerceIn(0.7f, 1.35f),
    )

    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("layout", layout)
        .put("lyricsFraction", lyricsFraction).put("artworkScale", artworkScale)
        .put("showArtwork", showArtwork).put("showMetadata", showMetadata)
        .put("metadataFirst", metadataFirst)
        .put("artworkOffsetX", artworkOffsetX).put("artworkOffsetY", artworkOffsetY)
        .put("metadataOffsetX", metadataOffsetX).put("metadataOffsetY", metadataOffsetY)
        .put("metadataScale", metadataScale)
        .put("artworkLocked", artworkLocked).put("metadataLocked", metadataLocked)
        .put("artworkOnTop", artworkOnTop)
        .put("lyricsOffsetX", lyricsOffsetX).put("lyricsOffsetY", lyricsOffsetY)
        .put("lyricsScale", lyricsScale).put("lyricsLocked", lyricsLocked)
        .put("transportOffsetX", transportOffsetX).put("transportOffsetY", transportOffsetY)
        .put("transportScale", transportScale).put("transportLocked", transportLocked)

    companion object {
        fun parse(value: JSONObject): NativeLyricPreset = NativeLyricPreset(
            value.optString("id", ""), value.optString("name", ""), value.optString("layout", "classic"),
            value.optDouble("lyricsFraction", 0.55).toFloat(), value.optDouble("artworkScale", 1.0).toFloat(),
            value.optBoolean("showArtwork", true), value.optBoolean("showMetadata", true),
            value.optBoolean("metadataFirst", false),
            value.optDouble("artworkOffsetX", 0.0).toFloat(),
            value.optDouble("artworkOffsetY", 0.0).toFloat(),
            value.optDouble("metadataOffsetX", 0.0).toFloat(),
            value.optDouble("metadataOffsetY", 0.0).toFloat(),
            value.optDouble("metadataScale", 1.0).toFloat(),
            value.optBoolean("artworkLocked", false),
            value.optBoolean("metadataLocked", false),
            value.optBoolean("artworkOnTop", false),
            value.optDouble("lyricsOffsetX", 0.0).toFloat(),
            value.optDouble("lyricsOffsetY", 0.0).toFloat(),
            value.optDouble("lyricsScale", 1.0).toFloat(),
            value.optBoolean("lyricsLocked", false),
            value.optDouble("transportOffsetX", 0.0).toFloat(),
            value.optDouble("transportOffsetY", 0.0).toFloat(),
            value.optDouble("transportScale", 1.0).toFloat(),
            value.optBoolean("transportLocked", false),
        ).normalized()
    }
}

/** Controls order only; lyrics and transport retain their existing positions. */
internal enum class NativeLyricHeaderLayer { Artwork, Metadata, Lyrics, Transport }

/** A deliberately bounded translation leaves the transport controls in the original scroll region. */
internal fun nativeBoundLayerOffset(value: Float): Float = value.finiteOr(0f).coerceIn(-0.20f, 0.20f)

internal fun nativeSnapLayerOffset(value: Float): Float =
    nativeBoundLayerOffset(value).let { if (kotlin.math.abs(it) <= 0.015f) 0f else it }

internal fun NativeLyricPreset.headerLocked(layer: NativeLyricHeaderLayer): Boolean =
    when (layer) {
        NativeLyricHeaderLayer.Artwork -> artworkLocked
        NativeLyricHeaderLayer.Metadata -> metadataLocked
        NativeLyricHeaderLayer.Lyrics -> lyricsLocked
        NativeLyricHeaderLayer.Transport -> transportLocked
    }

internal fun NativeLyricPreset.headerZ(layer: NativeLyricHeaderLayer): Float =
    when (layer) {
        NativeLyricHeaderLayer.Artwork -> if (artworkOnTop) 1f else 0f
        NativeLyricHeaderLayer.Metadata -> if (artworkOnTop) 0f else 1f
        NativeLyricHeaderLayer.Lyrics -> 0f
        NativeLyricHeaderLayer.Transport -> 0f
    }

internal fun NativeLyricPreset.headerOffset(layer: NativeLyricHeaderLayer): Pair<Float, Float> =
    when (layer) {
        NativeLyricHeaderLayer.Artwork -> artworkOffsetX to artworkOffsetY
        NativeLyricHeaderLayer.Metadata -> metadataOffsetX to metadataOffsetY
        NativeLyricHeaderLayer.Lyrics -> lyricsOffsetX to lyricsOffsetY
        NativeLyricHeaderLayer.Transport -> transportOffsetX to transportOffsetY
    }

internal fun NativeLyricPreset.moveHeaderLayer(layer: NativeLyricHeaderLayer, x: Float, y: Float): NativeLyricPreset =
    if (headerLocked(layer)) this else when (layer) {
        NativeLyricHeaderLayer.Artwork -> copy(artworkOffsetX = nativeSnapLayerOffset(x),
            artworkOffsetY = nativeSnapLayerOffset(y))
        NativeLyricHeaderLayer.Metadata -> copy(metadataOffsetX = nativeSnapLayerOffset(x),
            metadataOffsetY = nativeSnapLayerOffset(y))
        NativeLyricHeaderLayer.Lyrics -> copy(lyricsOffsetX = nativeSnapLayerOffset(x),
            lyricsOffsetY = nativeSnapLayerOffset(y))
        NativeLyricHeaderLayer.Transport -> copy(transportOffsetX = nativeSnapLayerOffset(x),
            transportOffsetY = nativeSnapLayerOffset(y))
    }

internal fun NativeLyricPreset.visibleHeaderLayers(): List<NativeLyricHeaderLayer> =
    (if (metadataFirst) listOf(NativeLyricHeaderLayer.Metadata, NativeLyricHeaderLayer.Artwork)
    else listOf(NativeLyricHeaderLayer.Artwork, NativeLyricHeaderLayer.Metadata))
        .filter { when (it) {
            NativeLyricHeaderLayer.Artwork -> showArtwork
            NativeLyricHeaderLayer.Metadata -> showMetadata
            NativeLyricHeaderLayer.Lyrics, NativeLyricHeaderLayer.Transport -> false
        } }

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
        return JSONObject().put("schemaVersion", 4).put("selectedId", state.selectedId)
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

/** One edit gesture or completed form change is one undo step; drafts never write preferences. */
internal data class NativeLyricPresetEditHistory(
    val current: NativeLyricPreset,
    val undoSteps: List<NativeLyricPreset> = emptyList(),
    val redoSteps: List<NativeLyricPreset> = emptyList(),
) {
    fun edit(next: NativeLyricPreset): NativeLyricPresetEditHistory =
        if (next == current) this
        else copy(current = next, undoSteps = (undoSteps + current).takeLast(24), redoSteps = emptyList())

    fun undo(): NativeLyricPresetEditHistory =
        if (undoSteps.isEmpty()) this
        else copy(current = undoSteps.last(), undoSteps = undoSteps.dropLast(1),
            redoSteps = (redoSteps + current).takeLast(24))

    fun redo(): NativeLyricPresetEditHistory =
        if (redoSteps.isEmpty()) this
        else copy(current = redoSteps.last(), redoSteps = redoSteps.dropLast(1),
            undoSteps = (undoSteps + current).takeLast(24))
}

/** The editor snaps to the center only when a drag is committed, not on every pointer move. */
/** 封面缩放仅在手势提交时吸附到 100%，拖动过程中不吸附。 */
internal fun nativeSnapArtworkScale(value: Float): Float {
    val scale = value.finiteOr(1f).coerceIn(0.7f, 1.35f)
    return if (kotlin.math.abs(scale - 1f) <= 0.03f) 1f else scale
}

internal fun nativeSnapLyricsFraction(value: Float): Float {
    val fraction = value.finiteOr(0.55f).coerceIn(0.35f, 0.75f)
    return if (kotlin.math.abs(fraction - 0.5f) <= 0.02f) 0.5f else fraction
}
