package org.yaqmc.android.nativeui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Exercises the browsing and listening journeys with an offline account and no audio. */
@UnstableApi
class NativeExperienceUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences(ComponentActivity::class.java)).around(compose)
    private val core = ExperienceCore()
    private lateinit var model: NativeViewModel
    @After fun close() { compose.runOnIdle { if (::model.isInitialized) model.viewModelScope.cancel() } }

    @Test fun searchAndLibraryRestoreTheirOwnDestinationAndScrollWithoutReloading() {
        install()
        compose.onNodeWithTag("nav-search").performClick()
        compose.onNodeWithTag("search-input").performTextInput("音乐")
        compose.onNodeWithTag("search-input").performImeAction()
        compose.waitUntil(5000) { model.page.value.page.tracks.size == 60 }
        capture("ux-search")
        compose.onNodeWithTag("catalog-list").performScrollToIndex(20)
        val position = scrollPosition()
        assertTrue(position > 10f)
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("我喜欢的音乐").performClick()
        compose.waitUntil(5000) { model.route.value.kind == "favorites" && model.page.value.loaded }
        compose.onNodeWithTag("nav-search").performClick()
        compose.onNodeWithTag("search-input").assertTextContains("音乐")
        compose.onNodeWithTag("search-kind-song").assertIsSelected()
        compose.onNodeWithTag("mini-player-open").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
        assertEquals(position, scrollPosition(), 0.5f)
        assertEquals(1, core.calls.count { it.first == "provider_search" })
        capture("ux-search-restored")
        compose.onNodeWithTag("nav-library").performClick()
        compose.runOnIdle { assertEquals("favorites", model.route.value.kind) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals("library", model.route.value.kind) }
        compose.onNodeWithText("我的歌单").assertIsDisplayed()
        compose.onNodeWithText("最近播放").assertIsDisplayed()
        compose.onNodeWithText("收听统计").assertIsDisplayed()
        capture("ux-library")
        compose.runOnIdle { model.setTheme("dark") }
        capture("ux-library-dark")
        compose.runOnIdle { model.setTheme("light") }
    }

    @Test fun settingsHaveTheirOwnEntryAndAccountAndStorageRemainSeparate() {
        install()
        capture("ux-home")
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("settings-list").assertIsDisplayed()
        compose.onNodeWithText("音乐爱好者").assertDoesNotExist()
        compose.onNodeWithText("退出登录").assertDoesNotExist()
        assertFalse(core.calls.any { it.first == "provider_cache_stats" })
        capture("ux-settings")
        compose.onNodeWithTag("settings-language").performClick()
        compose.onNodeWithTag("language-en-US").performClick()
        compose.onNodeWithTag("settings-language").assertTextContains("English")
        compose.onNodeWithTag("settings-language").performClick()
        compose.onNodeWithTag("language-zh-CN").performClick()
        compose.onNodeWithTag("settings-storage").performClick()
        compose.waitUntil(5000) { model.cache.value?.totalBytes == 2048L }
        compose.onNodeWithText("清理缓存").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("settings-theme").performClick()
        compose.onNodeWithTag("theme-dark").performClick()
        compose.runOnIdle { assertEquals("dark", model.preferences.value.theme) }
        compose.onNodeWithContentDescription("返回").performClick()
        capture("ux-settings-dark")
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("settings-theme").performClick()
        compose.onNodeWithTag("theme-light").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("open-account").performClick()
        compose.onNodeWithTag("account-list").assertIsDisplayed()
        compose.onNodeWithText("音乐爱好者").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
        capture("ux-account")
    }

    @Test fun miniPlayerAndQueueUseThePrimaryClickAndKeepListeningContext() {
        install()
        compose.onNodeWithText("倒数").performClick()
        compose.onNodeWithTag("player-cover").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").assertDoesNotExist()
        capture("ux-player")
        compose.onNodeWithTag("player-tab-lyrics").performClick()
        compose.onNodeWithTag("lyrics-viewport").assertIsDisplayed()
        capture("ux-lyrics")
        compose.onNodeWithContentDescription("播放队列").performClick()
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").assertDoesNotExist()
        compose.onNodeWithTag("open-settings").assertDoesNotExist()
        compose.onNodeWithTag("mini-player-open").assertDoesNotExist()
        compose.onNodeWithTag("queue-row-e1").performClick()
        compose.waitUntil(5000) { core.calls.any { it.first == "player_play_queue_entry" } }
        assertEquals("e1", core.calls.last { it.first == "player_play_queue_entry" }.second.text("entryId"))
        compose.runOnIdle { assertEquals("queue", model.route.value.kind) }
        capture("ux-queue")
        compose.runOnIdle { model.setTheme("dark") }
        capture("ux-queue-dark")
        compose.runOnIdle { model.setTheme("light") }
        compose.onNodeWithTag("queue-menu-e1").performClick()
        compose.onNodeWithText("歌曲详情").performClick()
        compose.runOnIdle { assertEquals("song", model.route.value.kind) }
    }

    @Test fun cardPreparationIsImmediateDeduplicatedAndRecoversAfterFailure() {
        install()
        core.playlistGate = CompletableDeferred()
        core.failPlaylist = true
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("card-play-playlist:p1"))
        compose.onNodeWithTag("card-play-playlist:p1").performClick()
        compose.onNodeWithTag("card-preparing-playlist:p1").assertIsDisplayed()
        compose.onNodeWithTag("card-play-playlist:p1").assertIsNotEnabled()
        compose.runOnIdle { model.playCard(CatalogItem("playlist", JSONObject().put("id", "p1"))) }
        assertEquals(1, core.calls.count { it.first == "provider_playlist" })
        capture("ux-card-preparing")
        core.playlistGate!!.complete(Unit)
        compose.waitUntil(5000) { model.preparingCard.value == null }
        compose.onNodeWithTag("card-error-playlist:p1").assertIsDisplayed()
        compose.runOnIdle { assertNull(model.message.value) }
        capture("ux-card-retry")
        compose.onNodeWithTag("card-play-playlist:p1").assertIsEnabled()
        core.playlistGate = null
        core.failPlaylist = false
        core.failPlayback = true
        compose.onNodeWithTag("card-play-playlist:p1").performClick()
        compose.waitUntil(5000) { model.preparingCard.value == null && model.failedCard.value != null }
        compose.onNodeWithTag("card-error-playlist:p1").assertIsDisplayed()
        compose.runOnIdle { assertNull(model.message.value) }
        core.failPlayback = false
        compose.onNodeWithTag("card-play-playlist:p1").performClick()
        compose.waitUntil(5000) { model.preparingCard.value == null && core.calls.count { it.first == "player_play_tracks" } == 2 }
        assertEquals(3, core.calls.count { it.first == "provider_playlist" })
        assertNull(model.failedCard.value)
    }

    private fun install() {
        compose.runOnUiThread {
            model = NativeViewModel(compose.activity.application, core)
            model.setLanguage("zh-CN")
            model.setTheme("light")
            model.onCoreEvent(1, "player://snapshot", core.snapshot().toString())
            model.action { model.refreshAccount() }
        }
        compose.setContent { NativeApp(model, {}, { _, _ -> }, {}) }
        compose.waitUntil(5000) { model.page.value.loaded && model.account.value.authenticated && model.player.value.current != null }
    }
    private fun scrollPosition() = compose.onNodeWithTag("catalog-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(1000)
        val context = instrumentation.targetContext
        val rendered = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$name-compose.png").outputStream().use { rendered.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.waitForIdleSync()
        Thread.sleep(200)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class ExperienceCore : NativeCoreApi {
        val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        var playlistGate: CompletableDeferred<Unit>? = null
        var failPlaylist = false
        var failPlayback = false
        private val artwork = InstrumentationRegistry.getArguments().getString("uiArtwork").orEmpty()
        private fun song(index: Int): JSONObject = JSONObject().put("id", "s$index").put("title", if (index == 0) "倒数" else "歌曲 ${index + 1}")
            .put("artists", JSONArray().put(JSONObject().put("id", "artist").put("name", "G.E.M.邓紫棋")))
            .put("album", JSONObject().put("id", "album").put("title", "另一个童话"))
            .put("artwork", JSONObject().put("src", artwork)).put("durationMs", 210000)
            .put("provider", JSONObject().put("providerId", "qqmusic").put("trackId", "s$index"))
        private fun playlist(id: String, title: String) = JSONObject().put("id", id).put("title", title).put("cover", artwork)
        override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
            calls += method to JSONObject(params.toString())
            return when (method) {
                "provider_home" -> JSONObject().put("dailySonglist", playlist("daily", "每日推荐").put("tracks", JSONArray((0..2).map(::song))))
                    .put("recommendedSonglists", JSONArray().put(playlist("p1", "通勤路上的旋律")).put(playlist("p2", "安静听一会儿")))
                "provider_account_snapshot" -> JSONObject("""{"state":"authenticated","revision":1,"profile":{"nickname":"音乐爱好者"},"capabilities":{"playlistRead":true,"favoriteRead":true,"favoriteWrite":true},"entitlement":{"tier":"super-vip","membership":"active"}}""")
                "provider_search" -> JSONObject().put("items", JSONArray((0..59).map(::song))).put("hasMore", false)
                "provider_favorite_songs" -> JSONObject().put("items", JSONArray((0..3).map(::song))).put("total", 4).put("authRevision", 1)
                "provider_playlist" -> { playlistGate?.await(); if (failPlaylist) throw CoreFailure("timeout", ""); playlist("p1", "通勤路上的旋律").put("tracks", JSONArray((0..3).map(::song))) }
                "player_play_tracks" -> { if (failPlayback) throw CoreFailure("timeout", ""); snapshot() }
                "provider_song" -> song(1)
                "provider_lyrics" -> JSONObject().put("songId", "s0").put("syncMode", "line").put("lines", JSONArray((0..8).map {
                    JSONObject().put("startMs", it * 10000).put("endMs", (it + 1) * 10000).put("text", "有你别无所求了")
                }))
                "provider_cache_stats" -> JSONObject("""{"totalBytes":2048,"mediaEntries":3,"artworkEntries":2}""")
                else -> snapshot()
            }
        }
        fun snapshot(): JSONObject = JSONObject().put("queue", JSONArray((0..3).map(::song)))
            .put("queueEntries", JSONArray((0..3).map { JSONObject().put("id", "e$it").put("track", song(it)) }))
            .put("currentIndex", 0).put("currentQueueEntryId", "e0").put("upcomingQueueEntryIds", JSONArray(listOf("e1", "e2", "e3")))
            .put("playbackOrder", "sequential").put("primaryPlaybackMode", "sequential").put("positionMs", 65000)
            .put("playbackDurationMs", 210000).put("playbackState", "paused").put("snapshotRevision", 1)
            .put("sourceSelection", JSONObject("""{"requestedQuality":"automatic","resolvedQuality":"lossless"}"""))
    }
}
