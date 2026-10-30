package org.yaqmc.android.nativeui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.yaqmc.android.update.AndroidUpdateResult
import org.yaqmc.android.update.ReleaseCandidate
import org.yaqmc.android.update.YaqmcVersion

/** Maintenance regressions use a fake Core and never modify listening history or accounts. */
@UnstableApi
class NativeMaintenanceUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences(ComponentActivity::class.java)).around(compose)
    private val calls = CopyOnWriteArrayList<String>()
    private var respond: suspend (String, JSONObject) -> Any? = { _, _ -> JSONObject() }
    private var update: AndroidUpdateResult = AndroidUpdateResult.NotAvailable
    private lateinit var model: NativeViewModel

    @After fun close() { compose.runOnIdle { if (::model.isInitialized) model.viewModelScope.cancel() } }

    private fun create() = compose.runOnUiThread {
        model = NativeViewModel(compose.activity.application, object : NativeCoreApi {
            override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
                calls += method
                if (method == "diagnostics_export_bundle_to") assertFalse("export uses the main UI origin", host)
                return respond(method, params)
            }
        }, checkUpdate = { update })
    }

    private fun install(route: String) {
        create()
        compose.runOnUiThread { model.navigate(NativeRoute(route), root = true) }
        compose.setContent { NativeApp(model, {}, { _, _ -> }, {}) }
    }

    @Test fun statisticsRangeCancellationAndClearConfirmationPreserveFailedData() {
        val old = CompletableDeferred<JSONObject>()
        var failClear = true
        respond = { method, params -> when (method) {
            "statistics_snapshot" -> if (params.optString("range") == "30-days") old.await()
                else JSONObject("""{"recordCount":2,"qualifiedPlayCount":4,"qualifiedListeningMs":90000}""")
            "statistics_clear" -> if (failClear) throw CoreFailure("timeout", "") else JSONObject("""{"deletedSessions":2}""")
            else -> JSONObject()
        } }
        install("statistics")
        compose.onNodeWithTag("statistics-7-days").performClick()
        compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 4L }
        old.complete(JSONObject("""{"recordCount":90,"qualifiedPlayCount":90}"""))
        compose.runOnIdle { assertEquals("7-days", model.statistics.value.range); assertEquals(4L, model.statistics.value.snapshot?.playCount) }
        compose.onNodeWithText("Clear listening history").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(calls.contains("statistics_clear"))
        compose.onNodeWithText("Clear listening history").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5000) { model.statistics.value.error }
        compose.runOnIdle { assertEquals(4L, model.statistics.value.snapshot?.playCount); assertFalse(model.statistics.value.clearing) }
        failClear = false
        compose.runOnIdle { model.clearStatistics() }
        compose.waitUntil(5000) { !model.statistics.value.clearing && !model.statistics.value.loading && !model.statistics.value.error }
        capture("pr9-statistics")
    }

    @Test fun statisticsChangesCoalesceAndIgnoreStaleOrOffScreenEvents() {
        val snapshots = java.util.concurrent.atomic.AtomicInteger(0)
        respond = { method, _ ->
            if (method == "statistics_snapshot")
                JSONObject().put("recordCount", 1).put("qualifiedPlayCount", snapshots.incrementAndGet())
            else JSONObject()
        }
        install("statistics")
        compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 1L }
        fun changed(revision: Long) = JSONObject().put("type", "statistics.changed")
            .put("data", JSONObject().put("revision", revision)).toString()
        compose.runOnIdle {
            model.onCoreEvent(1, "api://event", changed(5))
            model.onCoreEvent(2, "api://event", changed(6))
            model.onCoreEvent(3, "api://event", changed(7))
        }
        compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 2L }
        Thread.sleep(400)
        compose.runOnIdle {
            assertEquals(2, snapshots.get())
            assertFalse(model.statistics.value.loading) // quiet refresh keeps the UI usable
            model.onCoreEvent(4, "api://event", changed(6)) // stale revision
        }
        Thread.sleep(400)
        compose.runOnIdle {
            assertEquals(2, snapshots.get())
            model.navigate(NativeRoute("settings"), root = true)
            model.onCoreEvent(5, "api://event", changed(8))
        }
        Thread.sleep(400)
        compose.runOnIdle { assertEquals(2, snapshots.get()) }
        // The page always fetches a fresh snapshot upon returning.
        compose.runOnIdle { model.navigate(NativeRoute("statistics"), root = true) }
        compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 3L }
    }

    @Test fun statisticsRefreshRecoversAfterStopAndResume() {
        val snapshots = java.util.concurrent.atomic.AtomicInteger(0)
        respond = { method, _ ->
            if (method == "statistics_snapshot")
                JSONObject().put("recordCount", 1).put("qualifiedPlayCount", snapshots.incrementAndGet())
            else JSONObject()
        }
        install("statistics")
        compose.waitUntil(5000) { snapshots.get() == 1 }
        try {
            compose.runOnIdle { model.observe() }
            compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 2L }
            compose.runOnIdle {
                model.onCoreEvent(1, "api://event", JSONObject().put("type", "statistics.changed")
                    .put("data", JSONObject().put("revision", 1)).toString())
                model.stopObserving()
            }
            Thread.sleep(400)
            compose.runOnIdle { assertEquals(2, snapshots.get()); model.observe() }
            compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 3L }
            // A second resume must also recover events missed entirely in the background.
            compose.runOnIdle { model.stopObserving(); model.observe() }
            compose.waitUntil(5000) { model.statistics.value.snapshot?.playCount == 4L }
            compose.runOnIdle {
                model.stopObserving()
                model.navigate(NativeRoute("settings"), root = true)
                model.observe()
            }
            Thread.sleep(400)
            compose.runOnIdle { assertEquals(4, snapshots.get()) }
        } finally {
            compose.runOnIdle { model.stopObserving() }
        }
    }

    @Test fun diagnosticsSerializesLoadingAndMutationsAndRecoversFromFailure() {
        val pending = CompletableDeferred<JSONObject>()
        var level = "info"
        var first = true
        respond = { method, params -> when (method) {
            "diagnostics_snapshot" -> if (first) { first = false; pending.await() }
                else JSONObject().put("logLevel", level)
            "diagnostics_set_log_level" -> { level = params.getString("level"); null }
            "diagnostics_clear_logs" -> throw CoreFailure("timeout", "")
            else -> JSONObject()
        } }
        install("diagnostics")
        compose.onNodeWithTag("diagnostics-level-debug").assertIsNotEnabled()
        compose.runOnIdle { model.loadDiagnostics(); model.setDiagnosticsLevel("debug"); model.clearDiagnosticLogs() }
        assertEquals(1, calls.count { it == "diagnostics_snapshot" })
        assertFalse(calls.contains("diagnostics_set_log_level"))
        assertFalse(calls.contains("diagnostics_clear_logs"))
        pending.complete(JSONObject().put("logLevel", "info"))
        compose.waitUntil(5000) { !model.diagnostics.value.loading }
        compose.onNodeWithTag("diagnostics-level-debug").performClick()
        compose.waitUntil(5000) { model.diagnostics.value.logLevel == "debug" && !model.diagnostics.value.loading }
        compose.onNodeWithText("Clear old logs").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(calls.contains("diagnostics_clear_logs"))
        compose.onNodeWithText("Clear old logs").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5000) { calls.contains("diagnostics_clear_logs") && !model.diagnostics.value.busy }
        compose.onNodeWithTag("diagnostics-level-debug").assertIsSelected().assertIsEnabled()
        capture("pr9-diagnostics")
    }

    @Test fun cacheFailureStopsLoadingAndRetryRetainsPreviousCounts() {
        var fail = true
        respond = { method, _ -> when (method) {
            "provider_cache_stats" -> if (fail) throw CoreFailure("timeout", "") else JSONObject("""{"totalBytes":2048,"mediaEntries":3}""")
            "provider_clear_cache" -> throw CoreFailure("timeout", "")
            else -> JSONObject()
        } }
        install("settings")
        compose.waitUntil(5000) { model.cacheError.value && !model.cacheBusy.value }
        compose.onNodeWithText("Loading cache usage…").assertDoesNotExist()
        fail = false
        compose.runOnIdle { model.cacheStats() }
        compose.waitUntil(5000) { model.cache.value?.totalBytes == 2048L }
        compose.runOnIdle { model.cacheStats(clear = true) }
        compose.waitUntil(5000) { model.cacheError.value && !model.cacheBusy.value }
        compose.runOnIdle { assertEquals(3L, model.cache.value?.mediaEntries) }
    }

    @Test fun issueDraftSurvivesRestorationAndEditingInvalidatesPreview() {
        respond = { method, _ -> if (method == "issue_reporter_preview") JSONObject("""{"title":"Preview title","body":"Safe technical details","url":"https://github.com/YAQMC/YAQMC/issues/new","tooLongForBrowser":false}""") else JSONObject() }
        create()
        compose.runOnIdle { model.navigate(NativeRoute("issue-report"), root = true) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NativeApp(model, {}, { _, _ -> }, {}) }
        compose.onNodeWithText("Short summary").performTextInput("Playback stops")
        compose.onNodeWithText("Details and reproduction steps").performTextInput("Rotate the screen")
        compose.onNodeWithText("Preview report").performScrollTo().performClick()
        compose.onNodeWithText("Preview title").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Playback stops").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rotate the screen").assertExists()
        compose.onNodeWithText("Playback stops").performTextReplacement("Edited summary")
        compose.onNodeWithText("Open GitHub issue form").assertDoesNotExist()
        assertFalse(calls.contains("issue_reporter_validate_url"))
        capture("pr9-issue")
    }

    @Test fun diagnosticShareUsesReadableContentUriAndRejectsFailedExport() {
        var sent: Intent? = null
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { sent = intent }
        }
        var exported: File? = null
        var fail = false
        respond = { method, params -> if (method == "diagnostics_export_bundle_to") {
            assertFalse(params.getJSONObject("request").getBoolean("overrideUnresolved"))
            if (fail) throw CoreFailure("diagnostics.redaction", "")
            File(params.getString("path")).also { exported = it; it.writeText("fake safe bundle") }
            JSONObject().put("path", exported!!.absolutePath)
        } else JSONObject() }
        create()
        try {
            compose.runOnIdle { model.shareDiagnosticBundle(context) }
            compose.waitUntil(5000) { sent != null && !model.diagnostics.value.busy }
            @Suppress("DEPRECATION")
            val send = sent!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals(context.packageName + ".diagnostics", uri.authority)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            context.contentResolver.openInputStream(uri)!!.use { assertEquals("fake safe bundle", it.reader().readText()) }
            sent = null; fail = true
            compose.runOnIdle { model.shareDiagnosticBundle(context) }
            compose.waitUntil(5000) { calls.count { it == "diagnostics_export_bundle_to" } == 2 && !model.diagnostics.value.busy }
            assertNull(sent)
        } finally { exported?.delete() }
    }

    @Test fun updateStatesAreDistinctAndMissingBrowserDoesNotCrash() {
        install("about")
        compose.waitUntil(5000) { model.updateState.value.checked }
        compose.onNodeWithText("No newer release is available.").assertIsDisplayed()
        update = AndroidUpdateResult.Error("offline")
        compose.onNodeWithText("Check for updates").performClick()
        compose.waitUntil(5000) { model.updateState.value.error }
        compose.onNodeWithText("Could not check for updates. Please retry.").assertIsDisplayed()
        update = AndroidUpdateResult.Available(ReleaseCandidate(YaqmcVersion.parse("1.0.0")!!, "1.0.0", false,
            "https://github.com/YAQMC/YAQMC/releases/tag/v1.0.0", "Release notes"))
        compose.onNodeWithText("Check for updates").performClick()
        compose.waitUntil(5000) { model.updateState.value.availableVersion == "1.0.0" }
        compose.onNodeWithText("View release on GitHub").performScrollTo().assertIsDisplayed()
        val unavailable = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { throw android.content.ActivityNotFoundException() }
        }
        compose.runOnIdle { model.openProjectLink(unavailable, "https://github.com/YAQMC/YAQMC") }
        capture("pr9-about")
    }

    @Test fun chineseUpdatePageAndAccountContentStayBelowTheStatusBar() {
        respond = { method, _ -> if (method == "provider_account_snapshot") JSONObject("""{
            "state":"authenticated", "revision":1, "profile":{"nickname":"已登录用户"},
            "entitlement":{"tier":"super-vip","membership":"active","expiresAtMs":1893558896000}
        }""") else JSONObject() }
        install("settings")
        compose.runOnIdle {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            model.setLanguage("zh-CN")
            kotlinx.coroutines.runBlocking { model.refreshAccount() }
        }
        val inset = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val heading = compose.onNodeWithText("设置").fetchSemanticsNode().boundsInRoot
        assertTrue("settings heading clears status bar", heading.top >= inset)
        compose.onNodeWithText("已登录用户").assertIsDisplayed()
        compose.onNodeWithText("2030", substring = true).assertIsDisplayed()
        capture("feedback-settings-insets")
        compose.runOnIdle { model.navigate(NativeRoute("about")) }
        compose.waitUntil(5000) { model.updateState.value.checked }
        compose.onNodeWithText("检查更新").assertIsDisplayed()
        compose.onNodeWithText("当前没有可用的新版本。").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").assertDoesNotExist()
        capture("feedback-updates-chinese")
    }

    @Test fun yearlyTrendKeepsRankingsReachableAndLocalizesDimensions() {
        respond = { method, _ -> if (method == "statistics_snapshot") {
            JSONObject("""{"recordCount":365,"qualities":[{"key":"lossless","listenedMs":90000}],"providers":[{"key":"qqmusic","listenedMs":90000}]}""")
                .put("daily", JSONArray((0 until 365).map { day ->
                    JSONObject().put("dayStartMs", 1767225600000L + day * 86400000L).put("listenedMs", (day % 9 + 1) * 60000)
                }))
        } else JSONObject() }
        install("statistics")
        compose.onNodeWithTag("statistics-365-days").performClick()
        compose.onNodeWithTag("statistics-trend").performScrollTo().assertIsDisplayed()
        val bounds = compose.onNodeWithTag("statistics-trend").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top < 200.dp)
        compose.onNodeWithTag("statistics-trend").performScrollToIndex(364)
        compose.onNodeWithText("Most played songs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lossless").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("QQ Music").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("statistics-trend").performScrollTo()
        capture("pr9-yearly-statistics")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
