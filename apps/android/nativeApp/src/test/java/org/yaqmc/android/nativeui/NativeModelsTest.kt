package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeModelsTest {
    @Test fun homeKeepsEmbeddedPlaylistTracksAndCollectionType() {
        val home = NativeModels.home(JSONObject("""{"newSongSonglist":{"id":"qqmusic:playlist:new-song-recommend","title":"New songs","tracks":[{"id":"song-1","title":"Track"}]},"recentlyPlayed":[{"type":"playlist","item":{"id":"playlist-1","title":"Recent"}}]}"""))
        val recommendation = home.sections.first().cards.single()
        assertEquals("song-1", NativeModels.detail(recommendation.kind, recommendation.json).tracks.single().id)
        assertEquals("playlist", home.sections.last().cards.single().kind)
    }

    @Test fun nullableFieldsDoNotBecomeLiteralNull() {
        val value = JSONObject("""{"title":null,"profile":null,"nextCursor":null}""")
        assertEquals("", value.text("title"))
        assertEquals("访客", value.text("profile", "访客"))
        assertEquals("", value.text("nextCursor"))
    }

    @Test fun accountPlaylistKeepsCursorAndOriginalTrackPayload() {
        val value = JSONObject("""{"summary":{"title":"我的歌单"},"tracks":{"items":[{"id":"qqmusic:track:1","title":"一首歌","provider":{"providerId":"qqmusic","trackId":"1"}}],"nextCursor":"page-2"}}""")
        val page = NativeModels.detail("account-playlist", value)
        assertEquals("我的歌单", page.title.value)
        assertTrue(page.hasMore)
        assertEquals("page-2", page.cursor)
        assertEquals("1", page.tracks.single().json.getJSONObject("provider").getString("trackId"))
    }

    @Test fun lyricClockDelaysByDocumentOffsetAndRespectsExplicitGaps() {
        val document = JSONObject("""{"songId":"1","metadata":{"offsetMs":100},"lines":[{"startMs":1000,"endMs":2000,"text":"第一句"},{"startMs":4000,"endMs":4500,"text":"第二句"}]}""")
        val lyrics = NativeModels.lyrics(document)
        assertEquals(-1, lyrics.activeIndex(1099))
        assertEquals(0, lyrics.activeIndex(1100))
        assertEquals(-1, lyrics.activeIndex(3000))
        assertEquals(1, lyrics.activeIndex(4100))
        assertEquals(-1, lyrics.activeIndex(4600))
    }

    @Test fun unsynchronizedLyricsHaveNoFalseTiming() {
        val lyrics = NativeModels.lyrics(JSONObject("""{"lines":[{"startMs":null,"endMs":null,"text":"纯文本"}]}"""))
        assertNull(lyrics.lines.single().start)
        assertEquals(-1, lyrics.activeIndex(10000))
    }

    @Test fun rustResponseFixtureDecodesWithoutChangingProtocol() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("responses.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        val player = decodeEnvelope(fixture.getJSONObject("player_snapshot").toString()) as JSONObject
        assertTrue(player.has("queue"))
        assertTrue(player.has("currentIndex"))
    }

    @Test fun failedCoreResponseKeepsErrorCodeAndMessage() {
        try {
            decodeEnvelope("""{"ok":false,"error":{"code":"provider.network","message":"网络不可用"}}""")
            fail("Core errors must not decode as successful empty data")
        } catch (error: CoreFailure) {
            assertEquals("provider.network", error.code)
            assertEquals("网络不可用", error.message)
        }
    }
}
