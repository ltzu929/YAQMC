package org.yaqmc.android.nativeui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Simple bounded two-pass blur. Called only on a tiny downsampled background image. */
internal fun nativeLegacyBoxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
    val r = radius.coerceIn(0, 12)
    if (r == 0) return pixels.copyOf()
    fun pass(source: IntArray, horizontal: Boolean): IntArray {
        val result = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (offset in -r..r) {
                val xx = if (horizontal) x + offset else x
                val yy = if (horizontal) y else y + offset
                if (xx !in 0 until width || yy !in 0 until height) continue
                val color = source[yy * width + xx]
                a += color ushr 24 and 255
                red += color ushr 16 and 255
                green += color ushr 8 and 255
                blue += color and 255
                count++
            }
            result[y * width + x] = ((a / count) shl 24) or
                ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
        }
        return result
    }
    return pass(pass(pixels, true), false)
}

/**
 * On Android 8–11 graphics-layer blur is unavailable.
 * Decode with Coil on its worker, downsample to at most 128px and cache the blurred result.
 * The small image is smoothly enlarged behind an opaque/translucent surface veil.
 */
internal class NativeLegacyBackdropBlur : Transformation() {
    override val cacheKey: String = "yaqmc-native-legacy-backdrop-128-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val scale = minOf(1f, 128f / maxOf(input.width, input.height).coerceAtLeast(1))
        val width = (input.width * scale).roundToInt().coerceAtLeast(1)
        val height = (input.height * scale).roundToInt().coerceAtLeast(1)
        val sampled = Bitmap.createScaledBitmap(input, width, height, true)
        try {
            val pixels = IntArray(width * height)
            sampled.getPixels(pixels, 0, width, 0, 0, width, height)
            val blurred = nativeLegacyBoxBlur(pixels, width, height, radius = 5)
            Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888)
        } finally {
            if (sampled !== input) sampled.recycle()
        }
    }
}
