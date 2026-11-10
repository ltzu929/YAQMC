package org.yaqmc.android.nativeui

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.yaqmc.android.R

internal enum class SongShareKind(val label: Int) {
    PUBLIC_LINK(R.string.native_share_public), YAQMC_LINK(R.string.native_share_yaqmc), TEXT(R.string.native_share_text),
}

internal data class SongSharePayload(val title: String, val text: String)

internal object NativeSongShare {
    suspend fun resolve(track: Track, kind: SongShareKind, core: NativeCoreApi): SongSharePayload {
        val id = track.id.trim()
        val title = track.title.trim()
        if (track.providerId != "qqmusic" || id.isBlank() || id.toByteArray(StandardCharsets.UTF_8).size > 256 || hasControl(id) || title.isBlank() || hasControl(title)) {
            throw CoreFailure("native.share.invalid", "")
        }
        val text = when (kind) {
            SongShareKind.TEXT -> {
                val artists = track.json.optJSONArray("artists").objects().map { it.text("name").trim() }.filter(String::isNotBlank).joinToString(", ")
                if (artists.isBlank()) title else "$title — $artists"
            }
            SongShareKind.YAQMC_LINK -> "yaqmc://catalog/${track.providerId}/song?id=${URLEncoder.encode(id, StandardCharsets.UTF_8.name()).replace("+", "%20")}"
            SongShareKind.PUBLIC_LINK -> {
                val target = core.objectResult("catalog_share_song", JSONObject().put("providerId", track.providerId).put("id", id))
                if (target.text("providerId") != track.providerId || target.text("entityKind") != "song" || target.text("entityId") != id || target.text("title").isBlank() || hasControl(target.text("title"))) {
                    throw CoreFailure("native.share.invalid", "")
                }
                val url = target.text("canonicalHttpsUrl")
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null || hasControl(url)) {
                    throw CoreFailure("native.share.public", "")
                }
                "$title\n$url"
            }
        }
        return SongSharePayload(title, text)
    }

    private fun hasControl(value: String) = value.any { it.code <= 31 || it.code == 127 }
}
