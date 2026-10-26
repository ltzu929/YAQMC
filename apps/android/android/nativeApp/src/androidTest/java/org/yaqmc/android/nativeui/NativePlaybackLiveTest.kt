package org.yaqmc.android.nativeui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.yaqmc.android.media.PlaybackService

/** Explicit opt-in: plays audio and changes the local queue, then restores it. */
@UnstableApi
class NativePlaybackLiveTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: org.junit.rules.TestRule = RuleChain.outerRule(compose).around(NativeDevicePreferences())
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun transportBackgroundNotificationMediaControllerAndDeepLink() = runBlocking {
        withPlayback("transport", liveProvider = false) { core, receipt ->
            val available = listOf(fixture("first"), fixture("second"))
            val tracks = available + available.first()
            receipt.put("queueChanged", true)
            core.invoke("player_play_tracks", JSONObject().put("request", JSONObject().put("tracks", JSONArray(tracks.map { it.json })).put("shuffle", false)))
            val playing = awaitPlayer(core) { it.playing && it.position > 500 }
            receipt.put("corePlayback", true)
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .setApplicationLooper(Looper.getMainLooper()).buildAsync()
            val controller = future.get(15, TimeUnit.SECONDS)
            try {
                withTimeout(10000) {
                    while (!withContext(Dispatchers.Main) { controller.isPlaying && controller.mediaMetadata.title?.toString() == playing.current?.title }) delay(100)
                }
                receipt.put("mediaMetadataAndPlaying", true)
                withContext(Dispatchers.Main) { assertEquals(3, controller.mediaItemCount); assertNotNull(controller.sessionActivity) }
                withTimeout(10000) { while (context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty()) delay(100) }
                receipt.put("notification", true)
                shell("input keyevent KEYCODE_HOME")
                val before = awaitPlayer(core) { it.playing }.position
                delay(1500)
                assertTrue(awaitPlayer(core) { it.playing }.position > before + 700)
                receipt.put("backgroundClock", true)
                withContext(Dispatchers.Main) { controller.pause() }
                val paused = awaitPlayer(core) { it.phase == "paused" && !it.playing }
                val duplicate = paused.entries.last()
                core.invoke("player_reorder_queue_entry", JSONObject().put("entryId", duplicate.id).put("targetIndex", 1))
                core.invoke("player_play_next_queue_entry", JSONObject().put("entryId", duplicate.id))
                val scheduled = awaitPlayer(core) { it.upcoming.firstOrNull()?.id == duplicate.id }
                assertEquals(paused.currentEntry?.id, scheduled.currentEntry?.id)
                assertFalse(scheduled.playing)
                receipt.put("duplicateEntryReorderAndPlayNextStayPaused", true)
                withContext(Dispatchers.Main) { controller.seekTo(5000) }
                awaitPlayer(core) { it.position in 4500..7000 }
                withContext(Dispatchers.Main) { controller.play() }
                val resumed = awaitPlayer(core) { it.playing }
                withContext(Dispatchers.Main) { controller.seekToNextMediaItem() }
                val next = awaitPlayer(core) { it.playing && it.currentEntry?.id != resumed.currentEntry?.id }
                withContext(Dispatchers.Main) { controller.seekToPreviousMediaItem() }
                awaitPlayer(core) { it.playing && it.currentEntry?.id != next.currentEntry?.id }
                withContext(Dispatchers.Main) { controller.setShuffleModeEnabled(true); controller.repeatMode = Player.REPEAT_MODE_ONE }
                awaitPlayer(core) { it.order == "shuffle" && it.repeat == "one" }
                receipt.put("systemPauseSeekResumePreviousNextAndModes", true)
                core.invoke("player_set_primary_playback_mode", JSONObject().put("mode", "sequential"))
                val current = awaitPlayer(core) { it.playing && it.duration > 4000 }
                core.invoke("player_seek", JSONObject().put("positionMs", current.duration - 1500))
                awaitPlayer(core, 20000) { it.playing && it.currentEntry?.id != current.currentEntry?.id }
                receipt.put("automaticAdvance", true)
                val home = core.objectResult("provider_home", NativeViewModel.provider().put("refresh", false))
                val shareTrack = NativeModels.tracks(home.optJSONObject("guessSonglist")?.optJSONArray("tracks")).first()
                val public = NativeSongShare.resolve(shareTrack, SongShareKind.PUBLIC_LINK, core)
                assertTrue(public.text.substringAfter('\n').startsWith("https://"))
                receipt.put("livePublicShareTarget", true)
                val link = NativeSongShare.resolve(shareTrack, SongShareKind.YAQMC_LINK, core).text
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link), context, NativeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // This is a warm singleTask launch, so no new Activity creation is expected.
                shell("am start -W -n ${context.packageName}/${NativeActivity::class.java.name}")
                withContext(Dispatchers.Main) { context.startActivity(intent) }
                withTimeout(10000) {
                    while (!withContext(Dispatchers.Main) {
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<NativeActivity>().any { activity ->
                                val route = ViewModelProvider(activity)[NativeViewModel::class.java].route.value
                                route.kind == "song" && route.id == shareTrack.id
                            }
                    }) delay(100)
                }
                receipt.put("warmDeepLink", true)
            } finally { withContext(Dispatchers.Main) { controller.release() } }
        }
    }

    @Test fun guessAndRadarAutomaticallyRefillTheCoreQueue() = runBlocking {
        withPlayback("continuation") { core, receipt ->
            val home = core.objectResult("provider_home", NativeViewModel.provider().put("refresh", false))
            for (kind in listOf("guess", "radar")) {
                val tracks = (if (kind == "guess") NativeModels.tracks(home.optJSONObject("guessSonglist")?.optJSONArray("tracks"))
                    else NativeModels.tracks(home.optJSONArray("radarSongs"))).take(2)
                assertTrue("No initial $kind recommendations", tracks.isNotEmpty())
                val request = NativeViewModel.provider().put("kind", kind).put("tracks", JSONArray(tracks.map { it.json })).put("startAtId", tracks.first().id)
                if (kind == "radar") request.put("seedTrackIds", JSONArray(listOf(tracks.first().id)))
                receipt.put("queueChanged", true)
                core.invoke("continuation_start", JSONObject().put("request", request))
                val filled = awaitPlayer(core, 45000) { it.queue.size > tracks.size && it.playing }
                val continuation = core.objectResult("continuation_snapshot")
                assertTrue("$kind continuation stopped", continuation.optBoolean("active"))
                assertEquals(kind, continuation.text("kind"))
                assertEquals(filled.queue.size, filled.queue.map { it.key }.distinct().size)
                receipt.put(kind, JSONObject().put("initialCount", tracks.size).put("refilledCount", filled.queue.size).put("active", true))
                core.invoke("continuation_end")
            }
        }
    }

    @Test fun liveProviderCanPrepareAndPlayARecommendedSong() = runBlocking {
        withPlayback("provider") { core, receipt ->
            val home = core.objectResult("provider_home", NativeViewModel.provider().put("refresh", true))
            val candidates = (NativeModels.tracks(home.optJSONObject("guessSonglist")?.optJSONArray("tracks")) +
                NativeModels.tracks(home.optJSONArray("radarSongs"))).distinctBy { it.key }.take(3)
            assertTrue("No live recommendations", candidates.isNotEmpty())
            val attempts = JSONArray()
            receipt.put("attempts", attempts).put("queueChanged", true)
            for (track in candidates) {
                try {
                    core.invoke("player_play_tracks", JSONObject().put("request", JSONObject().put("tracks", JSONArray(listOf(track.json)))))
                    awaitPlayer(core) { it.playing && it.position > 500 }
                    attempts.put(JSONObject().put("played", true))
                    return@withPlayback
                } catch (error: CoreFailure) {
                    val snapshot = NativePlayback.decode(core.objectResult("player_snapshot"))
                    attempts.put(JSONObject().put("played", false).put("code", snapshot.errorCode.ifBlank { error.code }))
                    // Only source availability is allowed to try another song.
                    if (snapshot.errorCode !in setOf("media-url-expired", "media-url-unavailable", "track-unavailable", "network-failure")) throw error
                }
            }
            fail("The provider could not play any of the three sampled recommendations")
        }
    }

    @Test fun transientAudioFocusLossPausesAndRegainResumesPlayback() = runBlocking {
        withPlayback("focus", liveProvider = false) { core, receipt ->
            // A local Core fixture isolates Android focus handling from CDN availability.
            val tracks = listOf(fixture("focus"))
            receipt.put("queueChanged", true)
            core.invoke("player_play_tracks", JSONObject().put("request", JSONObject().put("tracks", JSONArray(tracks.map { it.json }))))
            awaitPlayer(core) { it.playing }
            val probe = "${instrumentation.context.packageName}/${NativeAudioFocusProbeActivity::class.java.name}"
            try {
                shell("am start -W -n $probe")
                withTimeout(10000) {
                    while (instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Focus request: 1").isNullOrEmpty()) delay(100)
                }
                receipt.put("externalFocusGranted", true)
                awaitPlayer(core, 10000) { it.phase == "paused" }
                receipt.put("transientLossPaused", true)
            } finally { shell("am start -W -a org.yaqmc.test.RELEASE_FOCUS -n $probe") }
            awaitPlayer(core, 10000) { it.playing }
            receipt.put("regainResumed", true)
        }
    }

    @Test fun reportedTrackWithNoHiresFileFallsBackAndPlays() = runBlocking {
        withPlayback("hires-source") { core, receipt ->
            val track = Track(core.objectResult("provider_song", NativeViewModel.provider().put("id", "qqmusic:track:003EXbLB4geLRQ")))
            assertEquals("寂寞烟火", track.title)
            receipt.put("queueChanged", true)
            core.invoke("player_play_tracks", JSONObject().put("request", JSONObject().put("tracks", JSONArray(listOf(track.json)))))
            core.invoke("provider_set_current_quality", NativeViewModel.provider().put("quality", "hi-res"))
            val playing = awaitPlayer(core, 45000) { it.playing && it.position > 3000 }
            assertEquals("hi-res", playing.selection.requested)
            assertEquals("source-unavailable", playing.selection.fallback)
            assertNotEquals("hi-res", playing.selection.resolved)
            assertEquals("unavailable", playing.selection.capabilities.find { it.quality == "hi-res" }?.resource)
            receipt.put("requestedQuality", playing.selection.requested)
                .put("resolvedQuality", playing.selection.resolved)
                .put("fallbackReason", playing.selection.fallback)
                .put("positionMs", playing.position)
                .put("hiresResource", "unavailable")
                .put("errorCode", playing.errorCode)
        }
    }

    @Test fun currentSongLyricsFollowPauseAndSeekWithoutReplacingTheQueue() = runBlocking {
        withPlayback("lyrics") { core, receipt ->
            val initial = NativePlayback.decode(core.objectResult("player_snapshot"))
            val track = requireNotNull(initial.current) { "Select a song before running the live lyrics check" }
            receipt.put("stage", "start-playback")
            core.invoke("player_play")
            awaitPlayer(core, 45000) { it.playing && it.position > initial.position + 400 }
            core.invoke("player_pause")
            awaitPlayer(core) { !it.playing && it.phase == "paused" }
            val model = withContext(Dispatchers.Main) {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<NativeActivity>().first()
                ViewModelProvider(activity)[NativeViewModel::class.java].also {
                    it.setLyrics(it.preferences.value.lyrics.copy(timingOffsetMs = 250, blur = false))
                    it.navigate(NativeRoute("player"))
                }
            }
            compose.waitForIdle()
            receipt.put("stage", "wait-for-model-document")
            withTimeout(15000) { while (model.lyrics.value.songId != track.id || model.lyrics.value.lines.isEmpty()) delay(100) }
            // Verify the document actually shown, rather than a second provider request that may differ.
            val document = model.lyrics.value
            assertTrue("The current song needs synchronized lyrics for this check", document.synchronized && document.lines.any { it.start != null })
            val target = document.lines.indices.firstOrNull { index ->
                val start = document.lines[index].start
                start != null && start in 15000..minOf(initial.duration - 2000, 120000) && document.end(index) - start >= 1500
            } ?: document.lines.indexOfFirst { it.start != null }
            receipt.put("targetLine", target).put("expectedText", document.lines[target].text)
            val seek = requireNotNull(document.seekPosition(target, 250))
            core.invoke("player_seek", JSONObject().put("positionMs", seek))
            val paused = awaitPlayer(core) { !it.playing && kotlin.math.abs(it.position - seek) < 100 }
            assertEquals(target, document.activeIndex(paused.position, 250))
            receipt.put("stage", "wait-for-visible-lyric").put("requestedPositionMs", seek)
            fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (node == null) emptyList()
                else listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
            try {
                compose.waitUntil(15000) {
                    runCatching { compose.onNodeWithText(document.lines[target].text.trim()).assertIsDisplayed(); true }.getOrDefault(false)
                }
            } catch (error: Throwable) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                receipt.put("windowPackage", root?.packageName).put("modelPositionMs", model.player.value.position)
                    .put("modelActiveLine", model.lyrics.value.activeIndex(model.player.value.position, 250))
                if (root?.packageName?.toString() == context.packageName) receipt.put("visibleText", JSONArray(texts(root)))
                saveScreenshot("batch4-live-lyrics-timeout.png")
                throw error
            }
            val before = paused.position
            delay(500)
            assertTrue(kotlin.math.abs(NativePlayback.decode(core.objectResult("player_snapshot")).position - before) < 100)
            saveScreenshot("batch4-live-lyrics-paused.png")
            for (preset in nativeBuiltinLyricPresets) {
                withContext(Dispatchers.Main) { model.setLyricPresets(model.preferences.value.lyricPresets.select(preset.id)) }
                receipt.put("stage", "visible-lyrics-${preset.layout}")
                compose.onNodeWithTag("player-layout-${preset.layout}").assertIsDisplayed()
                compose.waitUntil(15000) {
                    runCatching { compose.onNodeWithText(document.lines[target].text.trim()).assertIsDisplayed(); true }.getOrDefault(false)
                }
                saveScreenshot("pr2-live-lyrics-${preset.layout}.png")
            }
            receipt.put("presetLayoutsWithVisibleCurrentLine", JSONArray(nativeBuiltinLyricPresets.map { it.layout }))
            receipt.put("stage", "resume-playback")
            core.invoke("player_play")
            awaitPlayer(core) { it.playing && it.position > before + 700 }
            for (preset in nativeBuiltinLyricPresets) {
                val position = NativePlayback.decode(core.objectResult("player_snapshot")).position
                withContext(Dispatchers.Main) { model.setLyricPresets(model.preferences.value.lyricPresets.select(preset.id)) }
                awaitPlayer(core) { it.playing && it.position > position + 400 }
            }
            receipt.put("presetChangesKeepPlaying", true)
            core.invoke("player_pause")
            val final = awaitPlayer(core) { !it.playing }
            assertEquals(initial.entries.map { it.id }, final.entries.map { it.id })
            assertEquals(initial.currentEntry?.id, final.currentEntry?.id)
            receipt.put("synchronized", true).put("wordTimed", document.wordSynchronized).put("lines", document.lines.size)
                .put("requestedPositionMs", seek).put("pausedPositionMs", before).put("queueEntriesUnchanged", true)
                .put("visibleLyricMatchesDocument", true).put("pauseSeekAndResume", true)
                .put("stage", "complete")
        }
    }

    private fun saveScreenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try { File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
    }

    private fun fixture(id: String) = Track(JSONObject("""{
        "id":"native-probe-$id","title":"Playback probe $id","artists":[],
        "album":{"id":"native-probe-album","title":"Local test audio"},
        "artwork":{"src":"","alt":"","dominantColor":"#000000"},
        "durationMs":30000,"trackNumber":1,"isFavorite":false,"quality":"standard",
        "availability":{"status":"available"}
    }"""))

    private suspend fun withPlayback(name: String, liveProvider: Boolean = true, block: suspend (NativeCore, JSONObject) -> Unit) {
        assumeTrue("Run explicitly with -e livePlayback true", InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val core = NativeCore(context)
        val saved = core.objectResult("player_snapshot")
        val receipt = JSONObject().put("liveProvider", liveProvider).put("accountWrites", false).put("localPlaybackChanges", true)
        var failure: Throwable? = null
        try {
            core.invoke("player_set_volume", JSONObject().put("volume", 0.08))
            block(core, receipt)
            receipt.put("passed", true)
        } catch (error: Throwable) {
            receipt.put("passed", false).put("failure", (error as? CoreFailure)?.code ?: error.javaClass.simpleName)
            failure = error
        } finally {
            try {
                if (receipt.optBoolean("queueChanged")) { core.invoke("continuation_end"); restore(core, saved) }
                else {
                    core.invoke("player_pause")
                    if ((saved.optJSONArray("queue")?.length() ?: 0) > 0) {
                        core.invoke("player_seek", JSONObject().put("positionMs", saved.optLong("positionMs")))
                        if (saved.optBoolean("isPlaying")) core.invoke("player_play")
                    }
                    core.invoke("player_set_volume", JSONObject().put("volume", saved.optDouble("volume", 0.72)))
                }
                receipt.put("restoredQueueAndPlaybackPreferences", true)
            } catch (error: Throwable) {
                receipt.put("restoredQueueAndPlaybackPreferences", false).put("restoreFailure", (error as? CoreFailure)?.code ?: error.javaClass.simpleName)
                if (failure == null) failure = error
            } finally {
                shell("am start -W -n ${context.packageName}/${NativeActivity::class.java.name}")
                File(context.getExternalFilesDir(null), "batch3-live-$name.json").writeText(receipt.toString(2))
            }
        }
        failure?.let { throw it }
    }

    private suspend fun restore(core: NativeCore, saved: JSONObject) {
        val tracks = saved.optJSONArray("queue") ?: JSONArray()
        if (tracks.length() > 0) {
            val request = JSONObject().put("tracks", tracks).put("shuffle", false)
            val current = tracks.optJSONObject(saved.optInt("currentIndex", 0))
            if (current != null) request.put("startAtId", current.text("id"))
            try { core.invoke("player_play_tracks", JSONObject().put("request", request)) }
            catch (error: CoreFailure) { if (saved.optBoolean("isPlaying")) throw error }
            if (!saved.optBoolean("isPlaying")) core.invoke("player_pause")
            core.invoke("player_seek", JSONObject().put("positionMs", saved.optLong("positionMs")))
        } else {
            core.invoke("player_pause")
            while (true) {
                val entries = core.objectResult("player_snapshot").optJSONArray("queueEntries").objects()
                val last = entries.lastOrNull() ?: break
                core.invoke("player_remove_queue_entry", JSONObject().put("entryId", last.text("id")))
            }
        }
        core.invoke("player_set_shuffle", JSONObject().put("enabled", saved.text("playbackOrder") == "shuffle" || saved.optBoolean("shuffle")))
        core.invoke("player_set_repeat", JSONObject().put("mode", saved.text("repeat", "off")))
        core.invoke("player_set_volume", JSONObject().put("volume", saved.optDouble("volume", 0.72)))
    }

    private suspend fun awaitPlayer(core: NativeCore, timeout: Long = 30000, predicate: (PlayerState) -> Boolean): PlayerState = withTimeout(timeout) {
        var state = NativePlayback.decode(core.objectResult("player_snapshot"))
        while (!predicate(state)) {
            if (state.phase.endsWith("error")) throw CoreFailure(state.errorCode, state.error.orEmpty())
            delay(200)
            state = NativePlayback.decode(core.objectResult("player_snapshot"))
        }
        state
    }
    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
