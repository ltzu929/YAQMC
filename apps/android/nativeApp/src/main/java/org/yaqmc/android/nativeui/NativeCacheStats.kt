package org.yaqmc.android.nativeui

import org.json.JSONObject

/** Direct projection of provider_cache_stats; bytes and entry counts are never rounded in storage. */
internal data class NativeCacheStats(
    val totalBytes: Long, val mediaBytes: Long, val artworkBytes: Long,
    val mediaEntries: Long, val artworkEntries: Long,
    val metadataEntries: Long, val lyricEntries: Long,
    val mediaLimitBytes: Long, val artworkLimitBytes: Long,
) {
    companion object {
        fun parse(value: JSONObject): NativeCacheStats {
            fun count(key: String) = value.optLong(key).coerceAtLeast(0)
            return NativeCacheStats(
                count("totalBytes"), count("mediaBytes"), count("artworkBytes"),
                count("mediaEntries"), count("artworkEntries"),
                count("metadataEntries"), count("lyricEntries"),
                count("mediaLimitBytes"), count("artworkLimitBytes"),
            )
        }
    }
}
