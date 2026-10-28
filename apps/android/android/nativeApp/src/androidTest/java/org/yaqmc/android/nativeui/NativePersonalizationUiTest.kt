package org.yaqmc.android.nativeui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder

/** Isolated presentation and app-private files; no provider calls or playback commands. */
@UnstableApi
class NativePersonalizationUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val files = TemporaryFolder()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences(ComponentActivity::class.java)).around(files).around(compose)

    @Test fun openingLyricsMidTrackShowsCurrentLine() {
        val clock = mutableLongStateOf(50000L)
        val document = Lyrics("initial", lines = List(24) { index ->
            LyricLine(index * 5000L, (index + 1) * 5000L, "Initial line $index")
        })
        compose.setContent { MaterialTheme {
            LyricList(document, clock, NativeLyricPreferences(blur = false, spring = false), {},
                Modifier.fillMaxWidth().height(420.dp))
        } }
        compose.onNodeWithText("Initial line 10").assertIsDisplayed()
        compose.onNodeWithTag("lyric-line-10").assertIsSelected()
    }

    @Test fun timingVisibilityAndSeekingUseOneDocumentAndPreserveWordText() {
        val clock = mutableLongStateOf(1500)
        val document = mutableStateOf(Lyrics("one", 300, listOf(
            LyricLine(1000, 2200, "你好，🎵你好", "Hello", "ni hao", listOf(LyricWord(1000, 2000, "你好"), LyricWord(2000, 2100, "🎵"), LyricWord(2100, 2200, "你好"))),
            LyricLine(3000, 4000, "Goodnight", "晚安", "goodnight"),
        )))
        val seeks = mutableListOf<Long>()
        compose.setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red)) {
            LyricList(document.value, clock, NativeLyricPreferences(translation = "hide", timingOffsetMs = 500, blur = false), seeks::add,
                Modifier.fillMaxWidth().height(420.dp).padding(top = 24.dp))
        } }
        compose.onNodeWithText("你好，🎵你好").assertIsDisplayed()
        compose.onNodeWithText("Hello").assertDoesNotExist()
        compose.onNodeWithText("ni hao").assertIsDisplayed()
        val pixels = compose.onNodeWithTag("lyric-line-0").captureToImage().toPixelMap()
        var filledPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f && pixel.alpha > 0.5f) filledPixels++
        }
        assertTrue("Word highlighting must paint visible ink over the accessible text layout", filledPixels > 5)
        compose.onNodeWithTag("lyric-line-0").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(listOf(800L), seeks) }
        compose.runOnIdle { clock.longValue = 2300 }
        compose.onNodeWithTag("lyric-line-0").assertIsNotSelected()
        compose.runOnIdle { document.value = Lyrics("two", -200, listOf(LyricLine(2000, 5000, "Different song"))) }
        compose.onNodeWithText("你好，🎵你好").assertDoesNotExist()
        compose.onNodeWithText("Different song").performClick()
        compose.runOnIdle { assertEquals(1300L, seeks.last()) }
        compose.runOnIdle {
            document.value = Lyrics("long", lines = List(24) { index -> LyricLine(index * 5000L, (index + 1) * 5000L, "Timed line $index") })
            clock.longValue = 50000
        }
        compose.onNodeWithText("Timed line 10").assertIsDisplayed()
        compose.onNodeWithTag("lyric-line-10").assertIsSelected()
    }

    @Test fun cancellingColorPreviewKeepsPreferencesAndConfirmationPersistsOnlyAppearance() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyrics(NativeLyricPreferences(timingOffsetMs = 350))
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized, LocalConfiguration provides localized.resources.configuration,
                LocalActivityResultRegistryOwner provides compose.activity) {
            val state by store.state.collectAsState()
            MaterialTheme { NativeAppearanceSettings(state.appearance, false, store::setAppearance, { store.setAppearance(NativeAppearancePreferences()) }, {}) }
            }
        }
        compose.onNodeWithTag("appearance-settings").performScrollToNode(hasText("Ocean"))
        compose.onNodeWithText("Ocean").performClick()
        compose.onNodeWithTag("appearance-settings").performScrollToNode(hasText("Primary color · #4C9FE8"))
        compose.onNodeWithText("Primary color · #4C9FE8").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("#123456")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("#4C9FE8", store.state.value.appearance.primary) }
        compose.onNodeWithText("Primary color · #4C9FE8").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("#123456")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            val restarted = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0)).state.value
            assertEquals("#123456", restarted.appearance.primary)
            assertEquals("custom", restarted.appearance.palette)
            assertEquals(350L, restarted.lyrics.timingOffsetMs)
        }
    }

    @Test fun importedImageIsAnIndependentBoundedCopyAndInvalidReplacementLeavesItUsable() {
        val store = NativeBackgroundAssets(files.newFolder("backgrounds"))
        val bitmap = Bitmap.createBitmap(4096, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        bitmap.recycle()
        val reference = ByteArrayInputStream(bytes).use(store::import)
        val image = requireNotNull(store.resolve(reference))
        val before = image.readBytes()
        val decoded = BitmapFactory.decodeFile(image.absolutePath)!!
        assertTrue(decoded.width <= 2048)
        assertTrue(decoded.height <= 2048)
        assertEquals(android.graphics.Color.RED, decoded.getPixel(0, 0))
        decoded.recycle()
        try { ByteArrayInputStream("not an image".toByteArray()).use(store::import); fail("Invalid input must fail") }
        catch (_: IllegalArgumentException) { /* The existing copied asset remains usable. */ }
        assertArrayEquals(before, image.readBytes())
        assertEquals(image, NativeBackgroundAssets(image.parentFile!!).resolve(reference))
        assertFalse(image.parentFile!!.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test fun presetChooserPreviewAndCopyRemainOfflineAndSurviveRestart() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyrics(NativeLyricPreferences(timingOffsetMs = 275, translation = "hide"))
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("preset-builtin.immersive").performClick()
        compose.onNodeWithTag("lyric-preset-preview").assertIsDisplayed()
        compose.runOnIdle { assertEquals("immersive", store.state.value.lyricPresets.current().layout) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-duplicate"))
        compose.onNodeWithTag("preset-duplicate").performClick()
        compose.runOnIdle {
            assertTrue(store.state.value.lyricPresets.selectedId.startsWith("custom."))
            assertEquals("immersive", store.state.value.lyricPresets.current().layout)
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-delete"))
        compose.onNodeWithTag("preset-delete").performClick()
        compose.onNodeWithTag("preset-cancel-delete").performClick()
        compose.runOnIdle { assertEquals(1, store.state.value.lyricPresets.custom.size) }
        compose.onNodeWithTag("preset-delete").performClick()
        compose.onNodeWithTag("preset-confirm-delete").performClick()
        compose.runOnIdle {
            val restarted = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0)).state.value
            assertEquals("builtin.classic", restarted.lyricPresets.selectedId)
            assertTrue(restarted.lyricPresets.custom.isEmpty())
            assertEquals(275L, restarted.lyrics.timingOffsetMs)
            assertEquals("hide", restarted.lyrics.translation)
        }
    }

    @Test fun artworkResizeHandleSupportsDraftUndoCancelAndSavedRestart() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-artwork-resize"))
        compose.onNodeWithTag("preset-artwork-resize").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 95f), durationMillis = 350)
        }
        compose.runOnIdle { assertEquals(1f, store.state.value.lyricPresets.current().artworkScale) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-cancel"))
        compose.onNodeWithTag("preset-cancel").assertIsEnabled().performClick()
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-artwork-resize"))
        compose.onNodeWithTag("preset-artwork-resize").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 95f), durationMillis = 350)
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-undo"))
        compose.onNodeWithTag("preset-undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-redo"))
        compose.onNodeWithTag("preset-redo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            val persisted = NativePreferenceStore(InstrumentationRegistry.getInstrumentation()
                .targetContext.getSharedPreferences("native-ui", 0)).state.value.lyricPresets
            assertTrue(persisted.current().artworkScale > 1f)
            assertEquals("classic", persisted.current().layout)
        }
    }

    @Test fun layerVisibilityIsPreviewOnlyUntilSaveAndSurvivesRestart() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-artwork-visible"))
        compose.onNodeWithTag("preset-artwork-visible").assertTextEquals("Show album artwork").assertIsOn()
        compose.onNodeWithText("Show album artwork").performClick()
        compose.onNodeWithTag("preset-artwork-visible").assertIsOff()
        compose.runOnIdle { assertTrue(store.state.value.lyricPresets.current().showArtwork) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        compose.onNodeWithTag("preset-artwork-preview").assertDoesNotExist()
        compose.onNodeWithText("Hear Together").assertExists()

        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-undo"))
        compose.onNodeWithTag("preset-undo").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        compose.onNodeWithTag("preset-artwork-preview").assertExists()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-redo"))
        compose.onNodeWithTag("preset-redo").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").performClick()
        compose.runOnIdle {
            val restarted = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0)).state.value.lyricPresets
            assertFalse(restarted.current().showArtwork)
            assertTrue(restarted.current().showMetadata)
        }

        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-metadata-visible"))
        compose.onNodeWithTag("preset-metadata-visible").assertTextEquals("Show song information").assertIsOn()
        compose.onNodeWithTag("preset-metadata-visible").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        compose.onNodeWithText("Hear Together").assertDoesNotExist()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-cancel"))
        compose.onNodeWithTag("preset-cancel").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        compose.onNodeWithText("Hear Together").assertExists()
        compose.runOnIdle { assertTrue(store.state.value.lyricPresets.current().showMetadata) }
    }

    @Test fun headerOrderChangesOnlyOnSaveAndSurvivesPreferenceReload() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-metadata-first"))
        compose.onNodeWithText("Song information above artwork").assertIsOff().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-cancel"))
        compose.onNodeWithTag("preset-cancel").performClick()
        compose.runOnIdle { assertFalse(store.state.value.lyricPresets.current().metadataFirst) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-metadata-first"))
        compose.onNodeWithTag("preset-metadata-first").assertIsOff().performClick()
        compose.runOnIdle { assertFalse(store.state.value.lyricPresets.current().metadataFirst) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        val info = compose.onNodeWithTag("preset-metadata-preview").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("preset-artwork-preview").fetchSemanticsNode().boundsInRoot
        assertTrue(info.bottom <= cover.top + 1f)
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-undo"))
        compose.onNodeWithTag("preset-undo").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-redo"))
        compose.onNodeWithTag("preset-redo").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").performClick()
        compose.runOnIdle {
            val reloaded = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("native-ui", 0)).state.value.lyricPresets
            assertTrue(reloaded.current().metadataFirst)
        }
    }

    @Test fun draggableWidescreenPreviewIsUndoableAndPersistsOnlyAfterSaving() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-preview-wide"))
        compose.onNodeWithTag("preset-preview-wide").performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-divider-handle"))
        compose.onNodeWithTag("preset-divider-handle").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x - 95f), durationMillis = 350)
        }
        // Dragging changes a local draft, not the preference store or playback state.
        compose.runOnIdle {
            assertEquals(0.55f, store.state.value.lyricPresets.current().lyricsFraction)
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-undo"))
        compose.onNodeWithTag("preset-undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-redo"))
        compose.onNodeWithTag("preset-redo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            val persisted = NativePreferenceStore(
                InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0)
            ).state.value.lyricPresets
            assertTrue(persisted.current().lyricsFraction > 0.55f)
            assertEquals("classic", persisted.current().layout)
        }
    }

    @Test fun independentLayerMovesAreDraftOnlyAndPersistAfterUndoRedoAndSave() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-artwork-move"))
        compose.onNodeWithTag("preset-artwork-move").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 42f, y = center.y + 22f), durationMillis = 350)
        }
        compose.runOnIdle {
            assertEquals(0f, store.state.value.lyricPresets.current().artworkOffsetX)
            assertEquals(0f, store.state.value.lyricPresets.current().metadataOffsetX)
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-cancel"))
        compose.onNodeWithTag("preset-cancel").assertIsEnabled().performClick()
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()

        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-metadata-move"))
        compose.onNodeWithTag("preset-metadata-move").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x - 38f, y = center.y - 24f), durationMillis = 350)
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-undo"))
        compose.onNodeWithTag("preset-undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsNotEnabled()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-redo"))
        compose.onNodeWithTag("preset-redo").assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            val persisted = NativePreferenceStore(InstrumentationRegistry.getInstrumentation()
                .targetContext.getSharedPreferences("native-ui", 0)).state.value.lyricPresets.current()
            assertTrue(persisted.metadataOffsetX < 0f)
            assertTrue(persisted.metadataOffsetY < 0f)
            assertEquals(0f, persisted.artworkOffsetX)
            assertEquals(0f, persisted.artworkOffsetY)
        }
    }

    @Test fun lyricGestureAndTransportLockAreIsolatedUntilSavedAndSurviveRestart() {
        val store = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("native-ui", 0))
        store.setLyricPresets(NativeLyricPresetPreferences())
        val localized = compose.activity.withNativeLanguage(NativePreferences(language = "en-US"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalNativeContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val state by store.state.collectAsState()
                MaterialTheme { NativeLyricPresets(state.lyricPresets, store::setLyricPresets) }
            }
        }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-lyrics-move"))
        compose.onNodeWithTag("preset-lyrics-move").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 40f, y = center.y - 12f),
                durationMillis = 300)
        }
        compose.runOnIdle { assertEquals(0f, store.state.value.lyricPresets.current().lyricsOffsetX) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-transport-locked"))
        compose.onNodeWithTag("preset-transport-locked").performClick()
        compose.runOnIdle { assertFalse(store.state.value.lyricPresets.current().transportLocked) }
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("lyric-preset-preview"))
        compose.onNodeWithTag("preset-transport-move").assertDoesNotExist()
        compose.onNodeWithTag("lyric-presets").performScrollToNode(hasTestTag("preset-save"))
        compose.onNodeWithTag("preset-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            val saved = NativePreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("native-ui", 0)).state.value.lyricPresets.current()
            assertTrue(saved.lyricsOffsetX > 0f)
            assertTrue(saved.lyricsOffsetY < 0f)
            assertTrue(saved.transportLocked)
            assertEquals(1f, saved.transportScale)
        }
    }
}
