package org.yaqmc.android.nativeui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.json.JSONObject

@UnstableApi
class NativeShellTest {
    private val compose = createAndroidComposeRule<NativeActivity>()
    private val savedPreferences = NativeDevicePreferences()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(savedPreferences).around(compose)

    @Test fun lyricPresetEditsSaveCancelAndSurviveRecreationWithoutChangingPlayback() {
        openSettings()
        val before = readyPlayer()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Lyrics"))
        compose.onNodeWithText("Lyrics", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("open-lyric-presets").performClick()
        compose.onNodeWithTag("preset-builtin.vinyl").performClick()
        fun reveal(tag: String) = compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag(tag))
        reveal("preset-duplicate")
        compose.onNodeWithTag("preset-duplicate").performClick()
        val copiedId = compose.runOnIdle { model().preferences.value.lyricPresets.selectedId }
        assertTrue(copiedId.startsWith("custom."))
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("Device layout")
        reveal("preset-save")
        compose.onNodeWithTag("preset-save").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("Discarded name")
        reveal("preset-save")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("Device layout", model().preferences.value.lyricPresets.current().name) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("lyric-presets").assertIsDisplayed()
        compose.runOnIdle {
            val persisted = NativePreferenceStore(context().getSharedPreferences("native-ui", 0)).state.value.lyricPresets
            assertEquals(copiedId, persisted.selectedId)
            assertEquals("Device layout", persisted.current().name)
            assertEquals("vinyl", persisted.current().layout)
        }
        reveal("preset-delete")
        compose.onNodeWithTag("preset-delete").performClick()
        compose.onNodeWithTag("preset-cancel-delete").performClick()
        compose.runOnIdle { assertEquals(copiedId, model().preferences.value.lyricPresets.selectedId) }
        compose.onNodeWithTag("preset-delete").performClick()
        compose.onNodeWithTag("preset-confirm-delete").performClick()
        compose.runOnIdle { assertTrue(model().preferences.value.lyricPresets.custom.isEmpty()) }
        val after = readyPlayer()
        assertEquals(before.entries.map { it.id }, after.entries.map { it.id })
        assertEquals(before.currentEntry?.id, after.currentEntry?.id)
        assertEquals(before.playing, after.playing)
        capture("pr2-device-preset-editor")
    }

    @Test fun livePlayerPresetsMatchPreviewAcrossPortraitAndLandscape() {
        val previous = compose.activity.requestedOrientation
        val before = readyPlayer()
        assertNotNull("Select a song before running the device layout check", before.current)
        try {
            for (wide in listOf(false, true)) {
                compose.activityRule.scenario.onActivity {
                    it.requestedOrientation = if (wide) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                compose.waitUntil(10_000) { (compose.activity.resources.configuration.screenWidthDp >= 600) == wide }
                for (preset in nativeBuiltinLyricPresets) {
                    compose.runOnIdle { model().navigate(NativeRoute("lyric-settings")) }
                    compose.onNodeWithTag("open-lyric-presets").performClick()
                    compose.onNodeWithTag("preset-${preset.id}").performClick()
                    compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
                    compose.onNodeWithTag("lyric-preset-preview").assertIsDisplayed()
                    if (preset.layout == "classic") capture("pr2-device-preview-${if (wide) "wide" else "phone"}")
                    compose.runOnIdle { model().navigate(NativeRoute("player")) }
                    compose.onNodeWithTag("player-layout-${preset.layout}").assertIsDisplayed()
                    val controls = compose.onNodeWithTag("player-controls").fetchSemanticsNode().boundsInRoot
                    val lyrics = compose.onNodeWithTag("lyrics-viewport").fetchSemanticsNode().boundsInRoot
                    if (preset.layout == "immersive") assertTrue(lyrics.bottom <= controls.top + 1f)
                    else if (wide) assertTrue(controls.right <= lyrics.left + 1f)
                    else assertTrue(controls.bottom <= lyrics.top + 1f)
                    capture("pr2-device-${preset.layout}-${if (wide) "wide" else "phone"}")
                }
            }
            val after = readyPlayer()
            assertEquals(before.entries.map { it.id }, after.entries.map { it.id })
            assertEquals(before.currentEntry?.id, after.currentEntry?.id)
            assertEquals(before.playing, after.playing)
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previous }
        }
    }

    @Test fun plainPageTextHasVisibleInkInBothThemes() {
        openSettings()
        for (theme in listOf("dark", "light")) {
            compose.runOnIdle { model().setTheme(theme) }
            val pixels = compose.onNodeWithText("Settings").captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (if (theme == "dark") pixel.red > 0.8f && pixel.green > 0.8f && pixel.blue > 0.8f
                    else pixel.red < 0.2f && pixel.green < 0.2f && pixel.blue < 0.2f) ink++
            }
            assertTrue("Plain page text needs visible $theme theme ink", ink > 20)
        }
    }

    @Test fun appearanceAndPicturePickerSurviveLanguageChanges() {
        openSettings()
        val before = compose.runOnIdle { model().player.value }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Customize appearance"))
        compose.onNodeWithText("Customize appearance").performClick()
        for (language in listOf("en-US", "zh-CN")) {
            compose.runOnIdle { model().setLanguage(language) }
            compose.onNodeWithTag("appearance-settings").assertIsDisplayed()
            val imageMode = if (language == "zh-CN") "导入图片" else "Imported image"
            val choose = if (language == "zh-CN") "选择图片" else "Choose image"
            compose.onNodeWithTag("appearance-settings").performScrollToNode(hasText(imageMode))
            compose.onNodeWithText(imageMode).performClick()
            compose.onNodeWithTag("appearance-settings").performScrollToNode(hasText(choose))
            compose.onNodeWithText(choose).performClick()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.waitUntil(10000) {
                val owner = automation.rootInActiveWindow?.packageName?.toString().orEmpty()
                owner.isNotBlank() && owner != context().packageName && owner != "${context().packageName}.test"
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText(choose).assertIsDisplayed()
            compose.runOnIdle { assertEquals("", model().preferences.value.appearance.image) }
        }
        compose.runOnIdle {
            val after = model().player.value
            assertEquals(before.queue.map { it.id }, after.queue.map { it.id })
            assertEquals(before.current?.id, after.current?.id)
            assertEquals(before.playing, after.playing)
        }
        capture("batch4-zh-picker-cancelled")
    }

    @Test fun navigationOrderAndLibraryChildSelection() {
        val positions = nativeRootDestinations.map { compose.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot.left }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("Liked songs").performClick()
        compose.onNodeWithTag("nav-library").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My playlists").assertIsDisplayed()
        capture("batch1-en-library")
    }

    @Test fun languageAndThemeApplyWithoutChangingPlaybackAndSurviveRecreation() {
        openSettings()
        val before = compose.runOnIdle { model().player.value }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("language-zh-CN"))
        compose.onNodeWithTag("language-zh-CN").performClick()
        compose.onNodeWithTag("nav-home").assert(hasText("首页"))
        compose.onNodeWithTag("theme-dark").performClick()
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        compose.onNodeWithContentDescription("切换为浅色主题").assertIsDisplayed()
        compose.runOnIdle {
            val bars = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            assertFalse(bars.isAppearanceLightStatusBars)
            assertFalse(bars.isAppearanceLightNavigationBars)
        }
        capture("batch1-zh-dark-settings")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-home").assert(hasText("首页"))
        compose.runOnIdle {
            val after = model().player.value
            assertEquals(before.current?.id, after.current?.id)
            assertEquals(before.queue.map { it.id }, after.queue.map { it.id })
            assertEquals(before.playing, after.playing)
            val reread = NativePreferenceStore(context().getSharedPreferences("native-ui", Context.MODE_PRIVATE)).state.value
            assertEquals(NativePreferences("dark", "zh-CN"), reread)
        }
        compose.onNodeWithContentDescription("切换为浅色主题").performClick()
        compose.runOnIdle {
            assertEquals("light", model().preferences.value.theme)
            val bars = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            assertTrue(bars.isAppearanceLightStatusBars)
            assertTrue(bars.isAppearanceLightNavigationBars)
        }
    }

    @Test fun currentNativeBackStackAndEmptyRootFallbackArePreserved() {
        compose.onNodeWithTag("nav-library").performClick()
        openSettings()
        compose.runOnIdle { model().navigate(NativeRoute("player")); model().navigate(NativeRoute("queue")) }
        back()
        compose.runOnIdle { assertEquals("player", model().route.value.kind) }
        back()
        compose.runOnIdle { assertEquals("settings", model().route.value.kind) }
        back()
        compose.runOnIdle { assertEquals("library", model().route.value.kind) }
        compose.onNodeWithTag("nav-search").performClick()
        back()
        compose.runOnIdle { assertEquals("home", model().route.value.kind) }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        // Android may background a launcher root Activity instead of destroying it.
        compose.waitUntil(10_000) { compose.activityRule.scenario.state != Lifecycle.State.RESUMED }
    }

    @Test fun cachedLabelsChangeLanguageWhileProviderTitlesStayOriginal() {
        val page = NativeModels.home(JSONObject("""{"dailySonglist":{"id":"1","title":"原始歌单名"}}"""))
        val english = context().withNativeLanguage(NativePreferences(language = "en-US"))
        val chinese = context().withNativeLanguage(NativePreferences(language = "zh-CN"))
        assertEquals("Home", page.title.resolve(english))
        assertEquals("首页", page.title.resolve(chinese))
        assertEquals("Recommended for you", page.sections.first().title.resolve(english))
        assertEquals("为你推荐", page.sections.first().title.resolve(chinese))
        assertEquals("原始歌单名", page.sections.first().cards.single().title)
    }

    @Test fun wideNavigationAndSystemPreferencesRemainAvailable() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.runOnIdle { assertTrue(compose.activity.resources.configuration.screenWidthDp >= 600) }
            nativeRootDestinations.forEach { compose.onNodeWithTag("nav-$it").assertIsDisplayed() }
            capture("batch1-en-wide")
            openSettings()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("theme-system"))
            compose.onNodeWithTag("theme-system").performClick()
            compose.onNodeWithTag("theme-system").assertIsSelected()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("language-system"))
            compose.onNodeWithTag("language-system").performClick()
            compose.onNodeWithTag("language-system").assertIsSelected()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previous }
        }
    }

    private fun openSettings() = compose.onNodeWithContentDescription("Open account settings:", substring = true).performClick()
    private fun readyPlayer(): PlayerState {
        val viewModel = compose.runOnIdle { model() }
        compose.waitUntil(15_000) { viewModel.player.value.revision >= 0 }
        return compose.runOnIdle { viewModel.player.value }
    }
    private fun model() = ViewModelProvider(compose.activity)[NativeViewModel::class.java]
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context().getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
