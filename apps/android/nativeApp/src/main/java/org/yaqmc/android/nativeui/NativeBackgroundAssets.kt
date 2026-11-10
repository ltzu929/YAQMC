package org.yaqmc.android.nativeui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Own app-private copies survive provider permission expiry and never expose arbitrary file paths. */
internal class NativeBackgroundAssets(private val directory: File) {
    fun resolve(reference: String): File? = if (!validReference(reference)) null else File(directory, reference).takeIf { it.isFile }

    fun import(input: InputStream): String {
        val bytes = ByteArrayOutputStream().use { out ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 16 * 1024 * 1024) { "Image input is too large" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 128_000_000) { "Image cannot be decoded" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
        }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Image cannot be decoded" }
        var image = decoded
        var temporary: File? = null
        try {
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) image = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val reference = "background-${UUID.randomUUID()}.png"
            check(directory.isDirectory || directory.mkdirs())
            temporary = File(directory, "$reference.tmp")
            temporary.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(temporary.renameTo(File(directory, reference)))
            return reference
        } finally {
            temporary?.delete()
            if (image !== decoded) image.recycle()
            decoded.recycle()
        }
    }

    companion object {
        fun validReference(value: String) = Regex("background-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.png").matches(value)
    }
}
