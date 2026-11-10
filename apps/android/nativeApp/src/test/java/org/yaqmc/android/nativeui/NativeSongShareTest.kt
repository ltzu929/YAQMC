package org.yaqmc.android.nativeui

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.yaqmc.android.core.DeepLinkManager

class NativeSongShareTest {
    @Test fun localShareKindsWorkOfflineAndRoundTripSpecialCharacters() = runBlocking {
        val track = song("qqmusic:track:中 +/&?")
        val core = object : NativeCoreApi {
            override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? = error("Local share must not request a provider")
        }
        val link = NativeSongShare.resolve(track, SongShareKind.YAQMC_LINK, core)
        assertEquals(track.id, DeepLinkManager.parse(link.text)?.entityId)
        assertEquals("qqmusic", DeepLinkManager.parse(link.text)?.providerId)
        assertEquals("A song — First artist, Second artist", NativeSongShare.resolve(track, SongShareKind.TEXT, core).text)
    }

    @Test fun publicShareChecksProviderAndSongBeforeUsingTheUrl() = runBlocking {
        val core = ShareCore(target())
        val payload = NativeSongShare.resolve(song(), SongShareKind.PUBLIC_LINK, core)
        assertEquals("A song\nhttps://y.qq.com/n/ryqq/songDetail/123", payload.text)
        assertEquals("catalog_share_song", core.method)
        assertEquals("123", core.params.text("id"))
        assertEquals("qqmusic", core.params.text("providerId"))
        for (field in listOf("providerId", "entityId", "entityKind")) {
            val error = runCatching { NativeSongShare.resolve(song(), SongShareKind.PUBLIC_LINK, ShareCore(target().put(field, "wrong"))) }.exceptionOrNull()
            assertEquals("native.share.invalid", (error as CoreFailure).code)
        }
    }

    @Test fun invalidOrNonPublicUrlsAreRejectedInsteadOfShared() = runBlocking {
        for (url in listOf("", "http://example.com/song", "file:///song", "https://user:secret@example.com/song", "https://example.com:8443/song", "https://example.com/song#fragment", "https://example.com/\nsecret")) {
            val error = runCatching { NativeSongShare.resolve(song(), SongShareKind.PUBLIC_LINK, ShareCore(target().put("canonicalHttpsUrl", url))) }.exceptionOrNull()
            assertEquals(url, "native.share.public", (error as CoreFailure).code)
        }
    }

    @Test fun invalidTrackCannotReachTheProvider() = runBlocking {
        for (track in listOf(song(""), song("x\ny"), song("x".repeat(257)), Track(song().json.put("provider", JSONObject().put("providerId", "unsupported"))), Track(song().json.put("title", "")))) {
            val core = ShareCore(target())
            val error = runCatching { NativeSongShare.resolve(track, SongShareKind.PUBLIC_LINK, core) }.exceptionOrNull()
            assertEquals("native.share.invalid", (error as CoreFailure).code)
            assertEquals("", core.method)
        }
    }

    @Test fun providerFailureRemainsAFailure() = runBlocking {
        val core = object : NativeCoreApi {
            override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? = throw CoreFailure("timeout", "")
        }
        assertEquals("timeout", (runCatching { NativeSongShare.resolve(song(), SongShareKind.PUBLIC_LINK, core) }.exceptionOrNull() as CoreFailure).code)
    }

    private class ShareCore(val result: JSONObject) : NativeCoreApi {
        var method = ""
        var params = JSONObject()
        override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? { this.method = method; this.params = params; return result }
    }
    private fun song(id: String = "123") = Track(JSONObject("""{"title":"A song","artists":[{"name":"First artist"},{"name":"Second artist"}],"provider":{"providerId":"qqmusic"}}""").put("id", id))
    private fun target() = JSONObject("""{"providerId":"qqmusic","entityKind":"song","entityId":"123","title":"A song","canonicalHttpsUrl":"https://y.qq.com/n/ryqq/songDetail/123"}""")
}
