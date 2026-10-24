package org.yaqmc.android.nativeui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private fun model() = ViewModelProvider(compose.activity)[NativeViewModel::class.java]
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context().getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
