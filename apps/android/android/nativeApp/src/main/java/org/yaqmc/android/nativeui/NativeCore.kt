package org.yaqmc.android.nativeui

import android.content.Context
import androidx.media3.common.util.UnstableApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.yaqmc.android.core.CoreManager
import org.yaqmc.android.media.PlaybackService

internal class CoreFailure(val code: String, message: String) : Exception(message)

/** A request deadline is a recoverable failure; parent cancellation still cancels navigation. */
internal suspend fun <T> withCoreDeadline(timeoutMs: Long = 30_000, block: suspend () -> T): T {
    val result = withTimeoutOrNull(timeoutMs) { Result.success(block()) } ?: throw CoreFailure("timeout", "")
    return result.getOrThrow()
}

internal fun decodeEnvelope(json: String): Any? {
    val envelope = JSONObject(json)
    if (!envelope.optBoolean("ok")) {
        val error = envelope.optJSONObject("error")
        throw CoreFailure(error?.text("code") ?: "core.protocol", error?.text("message").orEmpty())
    }
    return envelope.opt("result").takeUnless { it === JSONObject.NULL }
}

internal interface NativeCoreApi {
    suspend fun invoke(method: String, params: JSONObject = JSONObject(), host: Boolean = false): Any?
    suspend fun objectResult(method: String, params: JSONObject = JSONObject(), host: Boolean = false): JSONObject =
        invoke(method, params, host) as? JSONObject ?: throw CoreFailure("core.protocol", "")
}

@UnstableApi
internal class NativeCore(private val context: Context) : NativeCoreApi {
    override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? = withCoreDeadline {
        var prepared = false
        if (PlaybackService.isPlaybackStartMethod(method)) {
            prepared = suspendCancellableCoroutine { continuation ->
                PlaybackService.prepareRendererPlayback(context, method) { allowed, didPrepare ->
                    if (!continuation.isActive) {
                        if (didPrepare) PlaybackService.cancelPreparedRendererPlayback()
                    } else if (allowed) continuation.resume(didPrepare)
                    else continuation.resumeWithException(CoreFailure("audio.focus", "无法获得音频焦点，请稍后重试"))
                }
            }
        }
        try {
            suspendCancellableCoroutine { continuation ->
                val id = CoreManager.invoke(method, params.toString(), origin = if (host) "host" else "main", onResponse = { json ->
                    val result = runCatching { decodeEnvelope(json) }
                    if (continuation.isActive) continuation.resumeWith(result)
                })
                continuation.invokeOnCancellation { CoreManager.forgetResponse(id) }
            }
        } catch (error: Throwable) {
            if (prepared) PlaybackService.cancelPreparedRendererPlayback()
            throw error
        }
    }

}
