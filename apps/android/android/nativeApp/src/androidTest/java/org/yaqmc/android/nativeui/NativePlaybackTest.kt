package org.yaqmc.android.nativeui

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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

/** Playback UI and failures use an isolated Core; no real account writes or audio. */
@UnstableApi
class NativePlaybackTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences(ComponentActivity::class.java)).around(compose)
    private val core = PlaybackCore()
    private lateinit var model: NativeViewModel
    private val shares = CopyOnWriteArrayList<Pair<Track, SongShareKind>>()
    @After fun clear() { compose.runOnIdle { if (::model.isInitialized) model.viewModelScope.cancel() } }

    @Test fun openingPresetSettingsDoesNotLoadAProviderPage() {
        install("lyric-settings")
        compose.onNodeWithTag("open-lyric-presets").performClick()
        compose.onNodeWithTag("lyric-presets").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("lyric-presets", model.route.value.kind)
            assertFalse(core.calls.any { it.first == "provider_lyric-presets" })
        }
    }

    @Test fun presetPreviewAndLivePlayerUsePhoneAndWidePartitions() {
        val preset = mutableStateOf(nativeBuiltinLyricPresets.first())
        val wide = mutableStateOf(false)
        val preview = mutableStateOf(true)
        compose.runOnUiThread {
            model = NativeViewModel(compose.activity.application as Application, core)
            model.onCoreEvent(1, "player://snapshot", core.snapshot().toString())
        }
        compose.setContent {
            val configuration = Configuration(compose.activity.resources.configuration).apply {
                screenWidthDp = if (wide.value) 720 else 360
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        if (preview.value) NativeLyricPresetPreview(preset.value) else NowPlaying(model, {})
                    }
                }
            }
        }
        compose.waitUntil(5000) { model.player.value.current != null }
        for (isWide in listOf(false, true)) for (builtin in nativeBuiltinLyricPresets) {
            val selected = builtin.copy(lyricsFraction = 0.65f)
            for (showPreview in listOf(true, false)) {
                compose.runOnIdle {
                    wide.value = isWide
                    preset.value = selected
                    preview.value = showPreview
                    model.setLyricPresets(NativeLyricPresetPreferences().select(selected.id).save(selected))
                }
                val controls = compose.onNodeWithTag("player-controls").fetchSemanticsNode().boundsInRoot
                val lyrics = compose.onNodeWithTag("lyrics-viewport").fetchSemanticsNode().boundsInRoot
                val layout = compose.onNodeWithTag(if (showPreview) "lyric-preset-preview" else "player-layout-${selected.layout}")
                    .fetchSemanticsNode().boundsInRoot
                val case = "${selected.layout}, wide=$isWide, preview=$showPreview"
                if (selected.layout == "immersive") {
                    assertTrue(case, lyrics.bottom <= controls.top + 1f)
                    assertEquals(case, selected.lyricsFraction, lyrics.height / layout.height, 0.02f)
                } else if (isWide) {
                    assertTrue(case, controls.right <= lyrics.left + 1f)
                    assertEquals(case, selected.lyricsFraction, lyrics.width / layout.width, 0.02f)
                } else {
                    assertTrue(case, controls.bottom <= lyrics.top + 1f)
                    assertEquals(case, layout.width, lyrics.width, 1f)
                    val cap = 0.72f - (selected.lyricsFraction - 0.55f) * 0.5f
                    assertTrue(case, controls.height / layout.height <= cap + 0.02f)
                }
            }
        }
        assertFalse(core.calls.any { it.first.startsWith("player_") })
    }

    @Test fun queueSeparatesCurrentAndUsesCoreShuffleOrderAndEntryActions() {
        install("queue")
        compose.onNodeWithText("Up next · 3").assertIsDisplayed()
        val positions = listOf("e2", "e3", "e1").map { compose.onNodeWithTag("queue-row-$it").fetchSemanticsNode().boundsInRoot.top }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithTag("queue-menu-e2").performClick()
        compose.onNodeWithText("Play next").performClick()
        awaitCall("player_play_next_queue_entry")
        assertEquals("e2", core.calls.last { it.first == "player_play_next_queue_entry" }.second.text("entryId"))
        compose.onNodeWithTag("queue-menu-e2").performClick()
        compose.onNodeWithText("Remove from queue").performClick()
        awaitCall("player_remove_queue_entry")
        assertEquals("e2", core.calls.last { it.first == "player_remove_queue_entry" }.second.text("entryId"))
        assertFalse(core.calls.any { it.first in setOf("player_play", "player_toggle", "player_play_tracks") })
        capture("batch3-queue-shuffle")
    }

    @Test fun draggingInTraversalOrderSendsTheTargetsCanonicalIndex() {
        install("queue")
        val source = compose.onNodeWithTag("queue-drag-e2").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("queue-row-e3").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("queue-drag-e2").performTouchInput {
            down(center)
            moveBy(Offset(0f, target.center.y - source.center.y), delayMillis = 600)
            up()
        }
        awaitCall("player_reorder_queue_entry")
        val params = core.calls.last { it.first == "player_reorder_queue_entry" }.second
        assertEquals("e2", params.text("entryId"))
        assertEquals(3, params.optInt("targetIndex"))
        assertEquals("e0", model.player.value.currentEntry?.id)
    }

    @Test fun qualityShowsAutomaticResolutionAndDisablesOnlyKnownRestrictions() {
        install("player")
        compose.onNodeWithTag("quality-picker").performScrollTo().performClick()
        compose.onNodeWithText("Automatic selected: Lossless").assertIsDisplayed()
        compose.onNodeWithTag("quality-master").assertIsNotEnabled()
        compose.onNodeWithTag("quality-hi-res").assertIsNotEnabled()
        compose.onNodeWithTag("quality-high").assertIsEnabled()
        capture("batch3-quality-capabilities")
        compose.onNodeWithTag("quality-high").performClick()
        awaitCall("provider_set_current_quality")
        assertEquals("high", core.calls.last { it.first == "provider_set_current_quality" }.second.text("quality"))
        assertFalse(core.calls.any { it.first == "provider_set_preferred_quality" })
    }

    @Test fun playerSharesAllKindsAndCancellationDoesNotShareOrStartPlayback() {
        install("player")
        for (kind in SongShareKind.entries) {
            openPlayerMenu()
            compose.onNodeWithText("Share").performClick()
            compose.onNodeWithTag("share-${kind.name}").performClick()
            compose.runOnIdle { assertEquals(kind, shares.last().second); assertEquals("same-song", shares.last().first.id) }
        }
        openPlayerMenu()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(3, shares.size)
        compose.onNodeWithText("Fixture artist").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(NativeRoute("artist", "artist"), model.route.value) }
        assertFalse(core.calls.any { it.first.startsWith("player_") })
    }

    @Test fun miniPlayerHasPreviousQueueQualityAndShareWithoutNavigatingUnexpectedly() {
        install("home")
        compose.onNodeWithContentDescription("Previous track").performClick()
        awaitCall("player_previous")
        compose.onNodeWithContentDescription("More actions for Current song").performClick()
        compose.onNodeWithText("Play queue").performClick()
        compose.runOnIdle { assertEquals("queue", model.route.value.kind) }
        compose.onNodeWithTag("queue-play-e3").performClick()
        awaitCall("player_play_queue_entry")
        assertEquals("e3", core.calls.last { it.first == "player_play_queue_entry" }.second.text("entryId"))
        compose.onNodeWithTag("queue-menu-e0").performClick()
        compose.onNodeWithText("Song details").performClick()
        compose.runOnIdle { assertEquals(NativeRoute("song", "same-song"), model.route.value) }
        assertEquals(1, core.calls.count { it.first == "player_previous" })
    }

    @Test fun failedQueueEditClearsWaitStateAndDoesNotOptimisticallyMoveDuplicates() {
        install("queue")
        val release = CompletableDeferred<Unit>()
        core.override = { method, _ ->
            if (method == "player_reorder_queue_entry") { release.await(); throw CoreFailure("timeout", "") }
            null
        }
        compose.runOnIdle { model.moveQueueEntry("e2", "e3"); model.removeQueueEntry("e1") }
        compose.waitUntil(5000) { model.queueBusy.value }
        assertEquals(1, core.calls.count { it.first == "player_reorder_queue_entry" })
        assertFalse(core.calls.any { it.first == "player_remove_queue_entry" })
        release.complete(Unit)
        compose.waitUntil(5000) { !model.queueBusy.value }
        assertEquals(listOf("e0", "e1", "e2", "e3"), model.player.value.entryIds)
        assertEquals(listOf("e2", "e3", "e1"), model.player.value.upcomingIds)
    }

    @Test fun outdatedSnapshotCannotUndoNewStateAndContinuationFailureNotifiesOnce() {
        install("home")
        publish(core.snapshot().put("snapshotRevision", 12).put("positionMs", 12000))
        compose.waitUntil(5000) { model.player.value.revision == 12L }
        publish(core.snapshot().put("snapshotRevision", 11).put("positionMs", 1000))
        compose.waitForIdle()
        assertEquals(12000L, model.player.value.position)
        val event = """{"type":"continuation.changed","data":{"notificationRevision":1,"terminalReason":"provider-error"}}"""
        compose.runOnIdle { model.onCoreEvent(1, "api://event", event) }
        compose.waitUntil(5000) { model.message.value != null }
        compose.runOnIdle { model.dismissMessage(); model.onCoreEvent(2, "api://event", event) }
        compose.waitForIdle()
        assertNull(model.message.value)
    }

    @Test fun qualityFailureKeepsSelectionAndLoadingErrorsRemainVisible() {
        install("home")
        core.override = { method, _ -> if (method == "provider_set_current_quality") throw CoreFailure("timeout", "") else null }
        compose.runOnIdle { model.changeQuality("high", current = true) }
        awaitCall("provider_set_current_quality")
        compose.waitUntil(5000) { !model.qualityBusy.value }
        assertEquals("automatic", model.player.value.selection.requested)
        publish(core.snapshot().put("snapshotRevision", 2).put("playbackState", "buffering"))
        compose.onNodeWithTag("playback-status", useUnmergedTree = true).assertTextEquals("Buffering…")
        publish(core.snapshot().put("snapshotRevision", 3).put("playbackState", "recoverable-error")
            .put("playbackError", JSONObject("""{"code":"track-unavailable","message":"Unavailable","retryable":false}""")))
        compose.waitUntil(5000) { model.player.value.revision == 3L }
        compose.onNodeWithTag("playback-status", useUnmergedTree = true).assertTextEquals("This song has no available playback source.")
    }

    @Test fun radarSelectionStartsAtTheChosenSongAndKeepsOnlyTheRemainingSeedQueue() {
        install("home")
        val tracks = model.player.value.queue
        compose.runOnIdle { model.playTrack(tracks, tracks[1], continuation = "radar") }
        awaitCall("continuation_start")
        val request = core.calls.last { it.first == "continuation_start" }.second.getJSONObject("request")
        assertEquals("radar", request.text("kind"))
        assertEquals("second", request.text("startAtId"))
        assertEquals("second", request.getJSONArray("tracks").getJSONObject(0).text("id"))
        assertEquals(3, request.getJSONArray("tracks").length())
        assertEquals("second", request.getJSONArray("seedTrackIds").getString(0))
        assertFalse(core.calls.any { it.first == "player_play_tracks" })
    }

    private fun install(route: String) {
        compose.runOnUiThread {
            model = NativeViewModel(compose.activity.application as Application, core)
            model.navigate(NativeRoute(route), root = true)
            model.onCoreEvent(1, "player://snapshot", core.snapshot().toString())
        }
        compose.setContent { NativeApp(model, login = {}, share = { track, kind -> shares += track to kind }, reportDrawn = {}) }
        compose.waitUntil(5000) { model.player.value.current != null }
    }
    private fun publish(value: JSONObject) {
        compose.runOnIdle { model.onCoreEvent(2, "player://snapshot", value.toString()) }
        compose.waitUntil(5000) { model.player.value.revision == value.optLong("snapshotRevision") || value.optLong("snapshotRevision") < model.player.value.revision }
    }
    private fun openPlayerMenu() = compose.onNodeWithContentDescription("More actions for Current song").performScrollTo().performClick()
    private fun awaitCall(method: String) = compose.waitUntil(5000) { core.calls.any { it.first == method } }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class PlaybackCore : NativeCoreApi {
        val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        var override: (suspend (String, JSONObject) -> JSONObject?)? = null
        override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
            calls += method to JSONObject(params.toString())
            override?.invoke(method, params)?.let { return it }
            return when (method) {
                "provider_home", "provider_lyrics" -> JSONObject()
                "provider_account_snapshot" -> JSONObject("""{"state":"guest","capabilities":{}}""")
                "continuation_start" -> JSONObject("""{"active":true}""")
                else -> snapshot()
            }
        }
        fun snapshot(): JSONObject {
            val tracks = JSONArray(listOf(song("same-song", "Current song"), song("second", "Second song"), song("same-song", "Current song"), song("last", "Last song")))
            val entries = JSONArray((0..3).map { JSONObject().put("id", "e$it").put("track", tracks.getJSONObject(it)) })
            return JSONObject().put("queue", tracks).put("queueEntries", entries).put("currentIndex", 0).put("currentQueueEntryId", "e0")
                .put("upcomingQueueEntryIds", JSONArray(listOf("e2", "e3", "e1"))).put("playbackOrder", "shuffle").put("primaryPlaybackMode", "shuffle")
                .put("repeat", "off").put("positionMs", 1500).put("playbackDurationMs", 120000).put("playbackState", "paused").put("snapshotRevision", 1)
                .put("sourceSelection", JSONObject("""{"requestedQuality":"automatic","resolvedQuality":"lossless","fallbackReason":"account-rights","qualityCapabilities":[
                    {"quality":"master","entitlement":"denied","resource":"available","client":"supported"},
                    {"quality":"hi-res","entitlement":"allowed","resource":"unavailable","client":"supported"},
                    {"quality":"high","entitlement":"unknown","resource":"unknown","client":"supported"}]}"""))
        }
        private fun song(id: String, title: String) = JSONObject("""{"artists":[{"id":"artist","name":"Fixture artist"}],"album":{"id":"album","title":"Fixture album"},"durationMs":120000,"provider":{"providerId":"qqmusic","trackId":"$id"}}""").put("id", id).put("title", title)
    }
}
