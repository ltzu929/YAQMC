package org.yaqmc.android.nativeui

import java.util.Locale
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class NativePalette(val id: String, val primary: String, val secondary: String)
internal val nativePalettes = listOf(
    NativePalette("default", "#A8C95E", "#7FA3A0"), NativePalette("ember", "#E85D68", "#F0A36C"),
    NativePalette("ocean", "#4C9FE8", "#64C8C0"), NativePalette("violet", "#9A78E8", "#D174C8"),
    NativePalette("sakura", "#DF789B", "#F0AA91"), NativePalette("mint", "#56B98A", "#6FBAC8"),
    NativePalette("mono", "#AEB2AB", "#747A73"),
)

internal fun validNativeHex(value: String) = Regex("#?(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})").matches(value.trim())
internal fun nativeHex(value: String, fallback: String = "#A8C95E"): String {
    if (!validNativeHex(value)) return fallback
    val hex = value.trim().removePrefix("#")
    return "#" + (if (hex.length == 3) hex.flatMap { listOf(it, it) }.joinToString("") else hex).uppercase(Locale.ROOT)
}

private fun rgb(value: String): List<Int> = nativeHex(value).drop(1).chunked(2).map { it.toInt(16) }
internal fun mixNativeHex(first: String, second: String, amount: Float): String = "#" + rgb(first).zip(rgb(second)).joinToString("") { (a, b) ->
    (a + (b - a) * amount.coerceIn(0f, 1f)).roundToInt().toString(16).padStart(2, '0')
}.uppercase(Locale.ROOT)

internal fun nativeContrast(first: String, second: String): Double {
    fun luminance(hex: String): Double = rgb(hex).map { channel ->
        val s = channel / 255.0
        if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }
    val a = luminance(first); val b = luminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
internal fun nativeForeground(background: String): String {
    val softBlack = nativeContrast(background, "#11130F")
    val white = nativeContrast(background, "#FFFFFF")
    return if (softBlack >= white && softBlack >= 4.5) "#11130F" else if (white >= 4.5) "#FFFFFF" else "#000000"
}

/** Material text accents need contrast against the surface; containers retain the chosen color. */
internal fun nativeReadableAccent(accent: String, background: String): String {
    val ink = nativeForeground(background)
    for (step in 0..20) {
        val candidate = mixNativeHex(accent, ink, step / 20f)
        if (nativeContrast(candidate, background) >= 4.5) return candidate
    }
    return ink
}

internal data class NativeAppearancePreferences(
    val palette: String = "default", val primary: String = "#A8C95E", val secondary: String = "#7FA3A0",
    val fontScale: Int = 100, val fontFamily: String = "application",
    val background: String = "default", val backgroundColor: String = "#20231C", val image: String = "",
    val fit: String = "cover", val influence: Int = 38, val opacity: Int = 96, val material: String = "opaque",
) {
    fun normalized() = copy(
        palette = palette.takeIf { it == "custom" || nativePalettes.any { p -> p.id == it } } ?: "default",
        primary = nativeHex(primary), secondary = nativeHex(secondary, "#7FA3A0"),
        fontScale = fontScale.coerceIn(80, 130), fontFamily = fontFamily.takeIf { it in setOf("application", "system", "serif", "monospace") } ?: "application",
        background = background.takeIf { it in setOf("default", "artwork", "color", "image") } ?: "default",
        backgroundColor = nativeHex(backgroundColor, "#20231C"), image = image.takeIf { NativeBackgroundAssets.validReference(it) } ?: "",
        fit = fit.takeIf { it in setOf("cover", "contain") } ?: "cover", influence = influence.coerceIn(0, 100),
        opacity = opacity.coerceIn(85, 100), material = material.takeIf { it in setOf("opaque", "translucent") } ?: "opaque",
    )
    fun withPalette(id: String): NativeAppearancePreferences = nativePalettes.find { it.id == id }?.let { copy(palette = id, primary = it.primary, secondary = it.secondary) } ?: copy(palette = "custom")
    val surfaceAlpha: Float get() = (opacity / 100f - if (material == "translucent") 0.07f else 0f).coerceIn(0.78f, 1f)
    fun json(): String = JSONObject().put("version", 1).put("palette", palette).put("primary", primary).put("secondary", secondary)
        .put("fontScale", fontScale).put("fontFamily", fontFamily).put("background", background).put("backgroundColor", backgroundColor)
        .put("image", image).put("fit", fit).put("influence", influence).put("opacity", opacity).put("material", material).toString()

    companion object {
        fun decode(source: String?): NativeAppearancePreferences = runCatching {
            val v = JSONObject(source ?: "{}")
            NativeAppearancePreferences(v.text("palette", "default"), v.text("primary", "#A8C95E"), v.text("secondary", "#7FA3A0"),
                v.optInt("fontScale", 100), v.text("fontFamily", "application"), v.text("background", "default"), v.text("backgroundColor", "#20231C"),
                v.text("image"), v.text("fit", "cover"), v.optInt("influence", 38), v.optInt("opacity", 96), v.text("material", "opaque")).normalized()
        }.getOrDefault(NativeAppearancePreferences())
    }
}
