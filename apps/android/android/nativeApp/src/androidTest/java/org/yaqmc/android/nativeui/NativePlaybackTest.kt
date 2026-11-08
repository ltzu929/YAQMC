package org.yaqmc.android.nativeui

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import org.yaqmc.android.media.CorePlayer

/** Playback UI and failures use an isolated Core; no real account writes or audio. */
@UnstableApi
class NativePlaybackTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences(ComponentActivity::class.java)).around(compose)
    private val core = PlaybackCore()
    private lateinit var model: NativeViewModel
    private val shares = CopyOnWriteArrayList<Pair<Track, SongShareKind>>()
    @After fun clear() { compose.runOnIdle { if (::model.isInitialized) model.viewModelScope.cancel() } }

    @Test fun independentPlayerSwitchesViewsAndReturnsWithoutChangingPlayback() {
        core.lyricDocument = JSONObject("""{"songId":"same-song","syncMode":"line","lines":[
            {"startMs":0,"endMs":1000,"text":"Previous lyric"},
            {"startMs":1000,"endMs":5000,"text":"Current lyric"},
            {"startMs":5000,"endMs":10000,"text":"Next lyric"}]}""")
        install("library")
        compose.runOnIdle { model.navigate(NativeRoute("player")) }
        compose.onNodeWithTag("player-cover").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search").assertDoesNotExist()
        compose.onNodeWithTag("player-toggle").assertIsDisplayed()
        compose.onNodeWithTag("player-cover").performClick()
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithTag("lyric-line-1").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("player-cover").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play queue").performClick()
        compose.runOnIdle { assertEquals("queue", model.route.value.kind); assertTrue(model.back()) }
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithTag("lyrics-viewport").assertIsDisplayed()
        compose.onNodeWithTag("player-tab-cover").performClick()
        compose.onNodeWithTag("player-cover").assertIsDisplayed()
        compose.onNodeWithTag("player-collapse").performClick()
        compose.onNodeWithTag("nav-library").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("library", model.route.value.kind)
            assertEquals("e0", model.player.value.currentEntry?.id)
            assertEquals(1500L, model.player.value.position)
            assertFalse(core.calls.any { it.first.startsWith("player_") })
        }
    }

    @Test fun playerButtonsAndSeekSendTheExistingPlaybackCommands() {
        install("player")
        val queuePixels = compose.onNodeWithContentDescription("Play queue").captureToImage().toPixelMap()
        val backdrop = queuePixels[0, 0].luminance()
        assertTrue("Queue icon must contain visible ink", (0 until queuePixels.height).any { y ->
            (0 until queuePixels.width).any { x -> kotlin.math.abs(queuePixels[x, y].luminance() - backdrop) > 0.1f }
        })
        compose.onNodeWithTag("player-toggle").performClick()
        awaitCall("player_toggle")
        compose.onNodeWithContentDescription("Previous track").performClick()
        awaitCall("player_previous")
        compose.onNodeWithContentDescription("Next track").performClick()
        awaitCall("player_next")
        compose.onNodeWithTag("playback-mode").performClick()
        awaitCall("player_set_primary_playback_mode")
        assertEquals("repeat-one", core.calls.last { it.first == "player_set_primary_playback_mode" }.second.text("mode"))
        compose.onNodeWithTag("player-progress").performTouchInput {
            swipe(start = Offset(width * 0.05f, centerY), end = center, durationMillis = 500)
        }
        try { awaitCall("player_seek") } catch (error: Throwable) { capture("player-seek-failure"); throw error }
        val seeks = core.calls.filter { it.first == "player_seek" }
        assertEquals(1, seeks.size)
        assertTrue(seeks.single().second.getLong("positionMs") in 50_000..70_000)
        compose.onNodeWithTag("player-tab-cover").assertIsSelected()
        compose.onNodeWithTag("player-cover").assertIsDisplayed()
        openPlayerMenu()
        compose.onNodeWithText("Add to queue").performScrollTo().performClick()
        awaitCall("player_add_to_queue")
        assertEquals("same-song", core.calls.last { it.first == "player_add_to_queue" }.second.getJSONObject("track").text("id"))
    }

    @Test fun swipingPlayerChangesViewsWithoutChangingPlaybackAndRetainsThePageOnReturn() {
        core.lyricDocument = JSONObject().put("songId", "same-song").put("syncMode", "line")
            .put("lines", JSONArray((0..30).map { index -> JSONObject().put("startMs", index * 1000)
                .put("endMs", (index + 1) * 1000).put("text", "Lyric $index") }))
        install("player")
        compose.onNodeWithTag("player-pages").performTouchInput { swipeRight() }
        compose.onNodeWithTag("player-tab-cover").assertIsSelected()
        compose.onNodeWithTag("player-pages").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithTag("lyric-line-1").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-viewport").performTouchInput { swipeUp() }
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithTag("lyric-line-0").assertIsNotDisplayed()
        compose.onNodeWithTag("lyrics-viewport").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithContentDescription("Play queue").performClick()
        compose.runOnIdle { assertEquals("queue", model.route.value.kind); assertTrue(model.back()) }
        compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
        compose.onNodeWithTag("lyrics-viewport").performTouchInput { swipeRight() }
        compose.onNodeWithTag("player-tab-cover").assertIsSelected()
        compose.onNodeWithTag("player-cover").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("e0", model.player.value.currentEntry?.id)
            assertEquals(1500L, model.player.value.position)
            assertFalse(core.calls.any { it.first.startsWith("player_") })
        }
    }

    @Test fun playerPagesFollowTheFingerAndTabChangesAnimate() {
        core.lyricDocument = JSONObject("""{"songId":"same-song","syncMode":"line","lines":[
            {"startMs":0,"endMs":5000,"text":"Current lyric"}]}""")
        install("player")
        val cover = compose.onNodeWithTag("player-cover").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("player-pages").performTouchInput {
                down(Offset(width * 0.8f, height * 0.3f))
                moveTo(Offset(width * 0.6f, height * 0.3f), delayMillis = 100)
                moveTo(Offset(width * 0.4f, height * 0.3f), delayMillis = 100)
            }
            compose.mainClock.advanceTimeByFrame()
            val dragged = compose.onNodeWithTag("player-cover").getUnclippedBoundsInRoot()
            assertTrue("The cover follows the horizontal drag", dragged.left < cover.left - 20.dp)
            assertTrue("The cover remains partially visible during the drag", dragged.right > 0.dp)
            compose.onNodeWithTag("player-pages").performTouchInput {
                moveTo(Offset(width * 0.1f, height * 0.3f), delayMillis = 100)
                up()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("player-tab-lyrics").assertIsSelected()
            compose.onNodeWithTag("player-tab-cover").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(96)
            val returning = compose.onNodeWithTag("player-cover").getUnclippedBoundsInRoot()
            assertTrue("Tab selection animates through an intermediate position", returning.left < cover.left - 1.dp)
            assertTrue("The cover enters the viewport during the animation", returning.right > 0.dp)
            capture("player-page-transition")
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("player-tab-cover").assertIsSelected()
            compose.onNodeWithTag("player-cover").assertIsDisplayed()
        } finally { compose.mainClock.autoAdvance = true }
        assertFalse(core.calls.any { it.first.startsWith("player_") })
    }

    @Test fun playerScreensKeepControlsReachableAcrossThemesAndLargeText() {
        core.preview = true
        core.artwork = InstrumentationRegistry.getArguments().getString("uiArtwork").orEmpty()
        core.lyricDocument = JSONObject().put("songId", "same-song").put("syncMode", "line")
            .put("lines", JSONArray((0..8).map { index -> JSONObject().put("startMs", index * 1000).put("endMs", (index + 1) * 1000)
                .put("text", "有你别无所求了") }))
        install("player")
        for (theme in listOf("dark", "light")) {
            compose.runOnIdle { model.setLanguage("zh-CN"); model.setTheme(theme) }
            compose.waitUntil(5000) { model.preferences.value.theme == theme && model.preferences.value.language == "zh-CN" }
            compose.waitUntil(5000) {
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val light = pixels[4, pixels.height / 2].luminance() > 0.5f
                light == (theme == "light") && runCatching { compose.onNodeWithText("封面").assertIsDisplayed(); true }.getOrDefault(false)
            }
            compose.onNodeWithTag("player-cover").assertIsDisplayed()
            compose.onNodeWithTag("player-toggle").assertIsDisplayed()
            capture("player-cover-$theme")
            compose.onNodeWithTag("player-tab-lyrics").performClick()
            compose.onNodeWithTag("lyric-line-1").assertIsDisplayed().assertIsSelected()
            compose.waitUntil(5000) {
                val line = compose.onNodeWithTag("lyric-line-1").getUnclippedBoundsInRoot()
                val viewport = compose.onNodeWithTag("lyrics-viewport").getUnclippedBoundsInRoot()
                line.top >= viewport.top && line.bottom <= viewport.bottom
            }
            compose.onNodeWithTag("player-toggle").assertIsDisplayed()
            capture("player-lyrics-$theme")
            compose.onNodeWithTag("player-tab-cover").performClick()
        }
        val playbackBottom = compose.onNodeWithTag("player-toggle").getUnclippedBoundsInRoot().bottom
        compose.runOnIdle { model.setAppearance(model.preferences.value.appearance.copy(fontScale = 130)) }
        compose.waitUntil(5000) { model.preferences.value.appearance.fontScale == 130 }
        compose.onNodeWithTag("player-toggle").assertIsDisplayed()
        assertEquals(playbackBottom.value, compose.onNodeWithTag("player-toggle").getUnclippedBoundsInRoot().bottom.value, 1f)
        compose.onNodeWithContentDescription("下一首").assertIsDisplayed()
        compose.onNodeWithTag("quality-picker").performScrollTo().assertIsDisplayed()
        capture("player-large-text")
        assertFalse(core.calls.any { it.first.startsWith("player_") })
    }

    @Test fun systemShuffleNextDoesNotPublishAnUnselectedAdjacentSong() {
        assertAuthoritativeSystemSkip(startIndex = 0, targetIndex = 3, next = true)
    }

    @Test fun systemPreviousRestartDoesNotPublishThePreviousQueueItem() {
        assertAuthoritativeSystemSkip(startIndex = 2, targetIndex = 2, next = false)
    }

    private fun assertAuthoritativeSystemSkip(startIndex: Int, targetIndex: Int, next: Boolean) {
        val initial = core.snapshot().put("currentIndex", startIndex).put("shuffle", true)
            .put("playbackState", "playing").put("playWhenReady", true).put("positionMs", 9000)
        val requests = mutableListOf<String>()
        val transitions = mutableListOf<Int>()
        lateinit var response: (String) -> Unit
        lateinit var player: CorePlayer
        compose.runOnIdle {
            player = CorePlayer(Looper.getMainLooper(), { true }, {}, {}, {}, {},
                sendCoreRequest = { method, _, reply ->
                    if (method == "player_snapshot") reply(JSONObject().put("ok", true).put("result", initial).toString())
                    else { requests += method; response = reply }
                })
        }
        try {
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(startIndex, player.currentMediaItemIndex)
                player.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        transitions += player.currentMediaItemIndex
                    }
                })
                if (next) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
                assertEquals(listOf(if (next) "player_next" else "player_previous"), requests)
                // The request remains pending: no guessed adjacent metadata may reach controllers.
                assertEquals(startIndex, player.currentMediaItemIndex)
                assertTrue(transitions.isEmpty())
                val settled = JSONObject(initial.toString()).put("currentIndex", targetIndex).put("positionMs", 0)
                response(JSONObject().put("ok", true).put("result", settled).toString())
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(targetIndex, player.currentMediaItemIndex)
                assertTrue(transitions.all { it == targetIndex })
                if (next) assertEquals(listOf(targetIndex), transitions) else assertTrue(transitions.isEmpty())
            }
        } finally { compose.runOnIdle { player.close(); player.release() } }
    }

    @Test fun playerTransitionKeepsTheSourcePageVisibleUntilItIsCovered() {
        install("library")
        val source = compose.onNodeWithText("Guest")
        source.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { model.navigate(NativeRoute("player")) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("player-surface").assertExists()
            compose.mainClock.advanceTimeBy(112)
            source.assertIsDisplayed()
            val panel = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
            assertTrue(source.getUnclippedBoundsInRoot().bottom < panel.top)
            capture("feedback-player-opening")
            compose.mainClock.advanceTimeBy(500)
            source.assertDoesNotExist()
            compose.runOnUiThread { assertTrue(model.back()) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(112)
            source.assertIsDisplayed()
            assertTrue(source.getUnclippedBoundsInRoot().bottom <
                compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot().top)
            capture("feedback-player-closing")
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("player-surface").assertDoesNotExist()
            assertFalse(core.calls.any { it.first.startsWith("player_") })
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun movedAndScaledTransportKeepsAllControlsInsideTheViewport() {
        install("player")
        openPresetPlayer()
        for (offset in listOf(-0.2f, 0.2f)) {
            compose.runOnIdle {
                val preset = nativeBuiltinLyricPresets.first().copy(
                    transportOffsetX = offset, transportOffsetY = 0.2f, transportScale = 1.35f)
                model.setLyricPresets(NativeLyricPresetPreferences().save(preset))
            }
            val viewport = compose.onNodeWithTag("player-controls").getUnclippedBoundsInRoot()
            for (label in listOf("Previous track", "Next track", "Play queue")) {
                val control = compose.onNodeWithContentDescription(label)
                control.performScrollTo().assertIsDisplayed()
                val bounds = control.getUnclippedBoundsInRoot()
                assertTrue("$label left edge at offset $offset", bounds.left >= viewport.left)
                assertTrue("$label right edge at offset $offset", bounds.right <= viewport.right)
            }
            capture("pr8-transport-$offset")
        }
        compose.onNodeWithContentDescription("Play queue").performClick()
        compose.runOnIdle { assertEquals(NativeRoute("queue"), model.route.value) }
    }

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
                        if (preview.value) NativeLyricPresetPreview(preset.value, wide.value, {}, {}) else PresetNowPlaying(model, {})
                    }
                }
            }
        }
        compose.waitUntil(5000) { model.player.value.current != null }
        val visibility = listOf(true to true, false to true, true to false, false to false)
        for (isWide in listOf(false, true)) for (builtin in nativeBuiltinLyricPresets) for ((artwork, metadata) in visibility)
            for (metadataFirst in if (artwork && metadata) listOf(false, true) else listOf(false)) {
            val selected = builtin.copy(lyricsFraction = 0.65f, showArtwork = artwork,
                showMetadata = metadata, metadataFirst = metadataFirst)
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
                val case = "${selected.layout}, wide=$isWide, preview=$showPreview, artwork=$artwork, metadata=$metadata, metadataFirst=$metadataFirst"
                val cover = compose.onNodeWithTag(if (showPreview) "preset-artwork-preview" else "player-artwork")
                if (artwork) cover.assertExists() else cover.assertDoesNotExist()
                val title = compose.onNodeWithText(if (showPreview) "Hear Together" else "Current song")
                if (metadata) title.assertExists() else title.assertDoesNotExist()
                val artist = compose.onNodeWithText(if (showPreview) "YAQMC Studio" else "Fixture artist")
                if (metadata) artist.assertExists() else artist.assertDoesNotExist()
                if (artwork && metadata) {
                    // Scroll containers clip boundsInRoot; ordering is a layout invariant even offscreen.
                    val coverBounds = cover.getUnclippedBoundsInRoot()
                    val infoBounds = compose.onNodeWithTag(if (showPreview) "preset-metadata-preview"
                        else "player-metadata").getUnclippedBoundsInRoot()
                    if (metadataFirst) assertTrue(case, infoBounds.bottom <= coverBounds.top + 1.dp)
                    else assertTrue(case, coverBounds.bottom <= infoBounds.top + 1.dp)
                }
                if (showPreview) {
                    val resize = compose.onNodeWithTag("preset-artwork-resize")
                    if (artwork) resize.assertExists() else resize.assertDoesNotExist()
                } else {
                    compose.onNodeWithContentDescription("Playback progress").assertExists()
                    compose.onNodeWithText("Play").assertExists()
                }
                if (!isWide && selected.layout == "classic")
                    capture("pr7-${if (showPreview) "preview" else "player"}-artwork-$artwork-metadata-$metadata-info-first-$metadataFirst")
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

    @Test fun headerOrderKeepsSongLinksAndTransportReachableInTheFullPlayer() {
        install("player")
        openPresetPlayer()
        for (builtin in nativeBuiltinLyricPresets) for (metadataFirst in listOf(false, true)) {
            compose.runOnIdle {
                val selected = builtin.copy(metadataFirst = metadataFirst)
                model.setLyricPresets(NativeLyricPresetPreferences().select(selected.id).save(selected))
            }
            compose.onNodeWithTag("player-artwork").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Current song").performScrollTo().assertIsDisplayed()
            capture("pr7-full-player-${builtin.layout}-info-first-$metadataFirst")
            compose.onNodeWithText("Fixture artist").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(NativeRoute("artist", "artist"), model.route.value) }
            compose.runOnIdle { model.navigate(NativeRoute("player"), root = true) }
            compose.onNodeWithContentDescription("Playback progress").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Play").performScrollTo().assertIsDisplayed()
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

    @Test fun normalPlaybackDoesNotAddQualityExplanationsToAnyPlayerView() {
        install("player")
        val selection = JSONObject("""{"requestedQuality":"automatic","resolvedQuality":"lossless","fallbackReason":"source-unavailable"}""")
        val warning = "The requested quality is unavailable; playing in Lossless."
        var revision = 2L
        for (reason in listOf("source-unavailable", "network-failure", "link-expired", "account-rights", "entitlement-unknown", "client-unsupported")) {
            publish(core.snapshot().put("snapshotRevision", revision++).put("sourceSelection", selection.put("fallbackReason", reason)))
            compose.onNodeWithTag("quality-picker").assertTextContains("Lossless")
            compose.onNodeWithTag("playback-status").assertDoesNotExist()
            compose.onNodeWithText(warning).assertDoesNotExist()
            compose.onNodeWithText("Account rights limited the selected audio quality.").assertDoesNotExist()
            compose.onNodeWithText("A network problem affected the requested quality; playing in Lossless.").assertDoesNotExist()
        }
        compose.onNodeWithTag("quality-picker").performScrollTo().performClick()
        compose.onNodeWithText("Automatic selected: Lossless").assertIsDisplayed()
        compose.onNodeWithTag("quality-automatic").performClick()
        compose.onNodeWithTag("player-tab-lyrics").performClick()
        compose.onNodeWithTag("playback-status").assertDoesNotExist()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithTag("player-tab-cover").performClick()
        openPresetPlayer()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithTag("player-tab-cover").performClick()
        publish(core.snapshot().put("snapshotRevision", revision++).put("sourceSelection", selection.put("preview", true)))
        compose.onNodeWithTag("quality-picker").assertTextContains("Lossless · Preview")
        compose.onNodeWithText("Only a preview is available.").assertDoesNotExist()
        capture("playback-automatic-quality")
        compose.runOnIdle { model.navigate(NativeRoute("home"), root = true) }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithText("Only a preview is available.").assertDoesNotExist()
        publish(core.snapshot().put("snapshotRevision", revision).put("sourceSelection", selection.put("requestedQuality", "hi-res").put("preview", false)))
        compose.onNodeWithText(warning).assertDoesNotExist()
    }

    @Test fun networkFallbackShowsActualQualityAndAllowsReselectingRequestedQuality() {
        install("player")
        // Keep the fake clock paused while checking feedback and choices.
        publish(core.snapshot().put("snapshotRevision", 2)
            .put("sourceSelection", JSONObject("""{"requestedQuality":"hi-res","resolvedQuality":"lossless","fallbackReason":"network-failure","qualityCapabilities":[
                {"quality":"hi-res","entitlement":"allowed","resource":"available","client":"supported","playable":true}]}""")))
        compose.onNodeWithText("A network problem affected the requested quality; playing in Lossless.").assertDoesNotExist()
        compose.onNodeWithTag("quality-picker").assertTextContains("Lossless")
        compose.onNodeWithTag("playback-status", useUnmergedTree = true).assertDoesNotExist()
        capture("playback-network-quality")
        compose.onNodeWithTag("quality-picker").performScrollTo().performClick()
        compose.onNodeWithTag("quality-hi-res").assertIsEnabled()
        capture("playback-network-quality-picker")
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
    private fun openPlayerMenu() = compose.onNodeWithContentDescription("More actions for Current song").performClick()
    private fun openPresetPlayer() {
        openPlayerMenu()
        compose.onNodeWithTag("player-preset-layout").performClick()
    }
    private fun awaitCall(method: String) = compose.waitUntil(5000) { core.calls.any { it.first == method } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class PlaybackCore : NativeCoreApi {
        val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        var lyricDocument = JSONObject()
        var preview = false
        var artwork = ""
        var override: (suspend (String, JSONObject) -> JSONObject?)? = null
        override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
            calls += method to JSONObject(params.toString())
            override?.invoke(method, params)?.let { return it }
            return when (method) {
                "provider_home" -> JSONObject()
                "provider_lyrics" -> lyricDocument
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
        private fun song(id: String, title: String) = JSONObject("""{"artists":[{"id":"artist","name":"Fixture artist"}],"album":{"id":"album","title":"Fixture album"},"durationMs":120000,"provider":{"providerId":"qqmusic","trackId":"$id"}}""").put("id", id).put("title", title).apply {
            if (preview) {
                put("title", "倒数")
                getJSONArray("artists").getJSONObject(0).put("name", "G.E.M.邓紫棋")
                getJSONObject("album").put("title", "另一个童话")
                put("artwork", JSONObject().put("src", artwork))
            }
        }
    }
}
