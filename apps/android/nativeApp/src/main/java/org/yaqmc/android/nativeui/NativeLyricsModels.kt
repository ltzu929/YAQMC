package org.yaqmc.android.nativeui

import java.util.Locale
import org.json.JSONObject

internal data class LyricLine(
    val start: Long?, val end: Long?, val text: String,
    val translation: String = "", val romanization: String = "", val words: List<LyricWord> = emptyList(),
) {
    /** UTF-16 ranges match Compose's text layout, including surrogate pairs and repeated words. */
    fun wordSpans(): List<LyricWordSpan> {
        var offset = 0
        val spans = mutableListOf<LyricWordSpan>()
        for (word in words.filter { it.text.isNotEmpty() }) {
            val start = text.indexOf(word.text, offset)
            // Some plain lyrics contain incompatible word text. Keep readable line highlighting.
            if (start < 0) return emptyList()
            offset = start + word.text.length
            spans += LyricWordSpan(start, offset, word)
        }
        return spans
    }
}

internal data class LyricWord(val start: Long, val end: Long, val text: String) {
    fun progress(time: Long): Float = ((time - start).toDouble() / (end - start).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}
internal data class LyricWordSpan(val start: Int, val end: Int, val word: LyricWord)

internal data class Lyrics(
    val songId: String = "", val offset: Long = 0, val lines: List<LyricLine> = emptyList(),
    val synchronized: Boolean = true, val wordSynchronized: Boolean = true,
) {
    private val ends = LongArray(lines.size).also { result ->
        var next = Long.MAX_VALUE
        for (index in lines.indices.reversed()) {
            result[index] = lines[index].end ?: next
            lines[index].start?.let { next = it }
        }
    }
    // The document offset delays lyrics; the user's positive adjustment advances them.
    fun time(position: Long, adjustment: Long = 0): Long = position + adjustment - offset
    fun end(index: Int): Long = ends[index]
    fun activeIndex(position: Long, adjustment: Long = 0): Int {
        if (!synchronized) return -1
        val time = time(position, adjustment)
        return lines.indices.firstOrNull { index -> lines[index].start?.let { time >= it && time < end(index) } == true } ?: -1
    }
    fun seekPosition(index: Int, adjustment: Long = 0): Long? = if (!synchronized) null
        else lines.getOrNull(index)?.start?.let { (it + offset - adjustment).coerceAtLeast(0) }
}

internal data class NativeLyricPreferences(
    val translation: String = "auto", val romanization: String = "auto", val timingOffsetMs: Long = 0,
    val fontScale: Float = 1f, val fontWeight: Int = 700, val lineHeight: Float = 1.16f,
    val wordEffect: String = "jump", val spring: Boolean = true, val scale: Boolean = true,
    val blur: Boolean = true, val hidePassed: Boolean = false, val fadeWidth: Float = 0.5f,
) {
    fun normalized() = copy(
        translation = translation.takeIf { it in visibilityModes } ?: "auto",
        romanization = romanization.takeIf { it in visibilityModes } ?: "auto",
        timingOffsetMs = timingOffsetMs.coerceIn(-2000, 2000),
        fontScale = fontScale.finiteOr(1f).coerceIn(0.7f, 1.45f),
        fontWeight = fontWeight.takeIf { it in 400..900 && it % 100 == 0 } ?: 700,
        lineHeight = lineHeight.finiteOr(1.16f).coerceIn(1.05f, 1.6f),
        wordEffect = wordEffect.takeIf { it in setOf("fill", "jump") } ?: "jump",
        fadeWidth = fadeWidth.finiteOr(0.5f).coerceIn(0.05f, 1f),
    )

    fun json(): String = JSONObject().put("version", 1).put("translation", translation).put("romanization", romanization)
        .put("timingOffsetMs", timingOffsetMs).put("fontScale", fontScale).put("fontWeight", fontWeight).put("lineHeight", lineHeight)
        .put("wordEffect", wordEffect).put("spring", spring).put("scale", scale).put("blur", blur)
        .put("hidePassed", hidePassed).put("fadeWidth", fadeWidth).toString()

    companion object {
        val visibilityModes = setOf("auto", "show", "hide")
        fun decode(source: String?): NativeLyricPreferences = runCatching {
            val value = JSONObject(source ?: "{}")
            NativeLyricPreferences(
                value.text("translation", "auto"), value.text("romanization", "auto"), value.optLong("timingOffsetMs"),
                value.optDouble("fontScale", 1.0).toFloat(), value.optInt("fontWeight", 700), value.optDouble("lineHeight", 1.16).toFloat(),
                value.text("wordEffect", "jump"), value.optBoolean("spring", true), value.optBoolean("scale", true),
                value.optBoolean("blur", true), value.optBoolean("hidePassed"), value.optDouble("fadeWidth", 0.5).toFloat(),
            ).normalized()
        }.getOrDefault(NativeLyricPreferences())
    }
}

internal fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

internal fun showLyricSecondary(mode: String, value: String, primary: String, romanization: Boolean): Boolean {
    if (value.isBlank() || mode == "hide") return false
    if (mode == "show") return true
    if (value.trim().lowercase(Locale.ROOT) == primary.trim().lowercase(Locale.ROOT)) return false
    return !romanization || primary.codePoints().anyMatch { it > 0x024f }
}
