package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.yaqmc.android.R

internal data class QueueItem(val id: String, val track: Track, val index: Int)

internal data class QualityCapability(val quality: String, val entitlement: String, val resource: String, val client: String) {
    // Unknown rights/resources must remain selectable, just as in the old Android client.
    val unavailable: Boolean get() = entitlement == "denied" || resource == "unavailable" || client == "unsupported"
}

internal data class SourceSelection(
    val requested: String = "", val resolved: String = "", val fallback: String = "",
    val preview: Boolean = false, val capabilities: List<QualityCapability> = emptyList(),
)

internal data class PlayerState(
    val queue: List<Track> = emptyList(),
    val entryIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val phase: String = "idle",
    val mode: String = "sequential",
    val selection: SourceSelection = SourceSelection(),
    val error: String? = null,
    val errorCode: String = "",
    val retryable: Boolean = false,
    val upcomingIds: List<String> = emptyList(),
    val order: String = "sequential",
    val repeat: String = "off",
    val revision: Long = -1,
    val sampledAt: Long = 0,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)
    val quality: String get() = selection.resolved
    val authoritative: Boolean get() = entryIds.size == queue.size && entryIds.all(String::isNotBlank) && entryIds.distinct().size == entryIds.size
    val entries: List<QueueItem> get() = queue.mapIndexed { index, track -> QueueItem(entryIds.getOrNull(index) ?: "legacy:$index:${track.key}", track, index) }
    val currentEntry: QueueItem? get() = entries.getOrNull(currentIndex)
    val upcoming: List<QueueItem> get() {
        val byId = entries.associateBy { it.id }
        return upcomingIds.distinct().filter { it != currentEntry?.id }.mapNotNull(byId::get)
    }
    val busy: Boolean get() = phase in setOf("loading", "buffering")
}

internal object NativePlayback {
    fun decode(value: JSONObject, queue: List<Track> = NativeModels.tracks(value.optJSONArray("queue"))): PlayerState {
        val ids = value.optJSONArray("queueEntries").objects().map { it.text("id") }
        val authoritative = ids.size == queue.size && ids.all(String::isNotBlank) && ids.distinct().size == ids.size
        val currentId = value.text("currentQueueEntryId")
        val index = if (authoritative && currentId in ids) ids.indexOf(currentId)
            else if (value.isNull("currentIndex")) -1 else value.optInt("currentIndex", -1)
        val repeat = value.text("repeat", "off")
        val order = value.text("playbackOrder", if (value.optBoolean("shuffle")) "shuffle" else "sequential")
        val selection = value.optJSONObject("sourceSelection")
        val error = value.optJSONObject("playbackError")
        val state = PlayerState(
            queue = queue, entryIds = if (authoritative) ids else emptyList(), currentIndex = index,
            position = value.optLong("positionMs").coerceAtLeast(0),
            duration = (if (value.isNull("playbackDurationMs")) queue.getOrNull(index)?.duration ?: 0 else value.optLong("playbackDurationMs")).coerceAtLeast(0),
            playing = value.optBoolean("isPlaying"), phase = value.text("playbackState", "idle"),
            mode = value.text("primaryPlaybackMode", if (repeat == "one") "repeat-one" else order),
            selection = SourceSelection(selection?.text("requestedQuality").orEmpty(), selection?.text("resolvedQuality").orEmpty(),
                selection?.text("fallbackReason").orEmpty(), selection?.optBoolean("preview") == true,
                selection?.optJSONArray("qualityCapabilities").objects().map { QualityCapability(it.text("quality"), it.text("entitlement", "unknown"), it.text("resource", "unknown"), it.text("client", "unknown")) }),
            error = error?.text("message")?.takeIf(String::isNotBlank), errorCode = error?.text("code").orEmpty(), retryable = error?.optBoolean("retryable") == true,
            order = order, repeat = repeat, revision = value.optLong("snapshotRevision", -1), sampledAt = value.optLong("sampledAtMs"),
        )
        val upcoming = if (authoritative && value.has("upcomingQueueEntryIds")) {
            val array = value.optJSONArray("upcomingQueueEntryIds")
            if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
        } else state.entries.drop((index + 1).coerceAtLeast(0)).map { it.id }
        return state.copy(upcomingIds = upcoming)
    }

    fun status(state: PlayerState): NativeText? = when (state.phase) {
        "loading" -> NativeText(R.string.native_playback_loading)
        "buffering" -> NativeText(R.string.native_playback_buffering)
        "ended" -> NativeText(R.string.native_playback_ended)
        "stopped" -> NativeText(R.string.native_playback_stopped)
        "recoverable-error", "fatal-error" -> NativeText(when (state.errorCode) {
            "media-url-unavailable", "track-unavailable" -> R.string.native_error_track
            "media-url-expired", "network-failure" -> R.string.native_error_network
            "output-device-unavailable", "audio-engine-unavailable" -> R.string.native_error_output
            "media-open-failed", "decoder-unsupported" -> R.string.native_error_decoder
            "entitlement-insufficient" -> R.string.native_error_rights
            "entitlement-unknown" -> R.string.native_error_rights_unknown
            "media-decryption-failed" -> R.string.native_error_decryption
            "authentication-expired" -> R.string.native_session_expired
            "http-range-unsupported", "seek-unsupported" -> R.string.native_error_seek
            "media-too-large", "media-cache-failure" -> R.string.native_error_cache
            else -> R.string.native_playback_unavailable
        })
        "idle", "playing", "paused" -> null
        else -> NativeText(R.string.native_playback_unavailable)
    }

    fun fallback(reason: String): Int? = when (reason) {
        "source-unavailable" -> R.string.native_fallback_source
        "account-rights" -> R.string.native_fallback_rights
        "entitlement-unknown" -> R.string.native_fallback_rights_unknown
        "client-unsupported" -> R.string.native_fallback_client
        "preview-only" -> R.string.native_fallback_preview
        else -> null
    }

    fun qualityLabel(quality: String): Int = when (quality) {
        "" -> R.string.native_select_quality
        "automatic" -> R.string.native_quality_automatic; "standard" -> R.string.native_quality_standard
        "high" -> R.string.native_quality_high; "lossless" -> R.string.native_quality_lossless
        "hi-res" -> R.string.native_quality_hires; "master" -> R.string.native_quality_master
        else -> R.string.native_unknown
    }

    val qualities = listOf("automatic", "standard", "high", "lossless", "hi-res", "master")
}
