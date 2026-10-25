package org.yaqmc.android.nativeui

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeAppearanceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun paletteSelectionKeepsTheOldAndroidColorsAndCustomValuesSurviveRestart() {
        assertEquals(listOf("default", "ember", "ocean", "violet", "sakura", "mint", "mono"), nativePalettes.map { it.id })
        val ocean = NativeAppearancePreferences().withPalette("ocean")
        assertEquals("#4C9FE8", ocean.primary)
        assertEquals("#64C8C0", ocean.secondary)
        val custom = ocean.copy(palette = "custom", primary = "#abc", secondary = "012345", fontFamily = "serif", fontScale = 125).normalized()
        assertEquals("#AABBCC", custom.primary)
        assertEquals("#012345", custom.secondary)
        assertEquals(custom, NativeAppearancePreferences.decode(custom.json()))
    }

    @Test fun malformedAndOldAppearanceValuesUseSafeDefaults() {
        assertEquals(NativeAppearancePreferences(), NativeAppearancePreferences.decode("broken"))
        assertEquals(NativeAppearancePreferences(), NativeAppearancePreferences.decode(null))
        val value = NativeAppearancePreferences(fontScale = 900, fontFamily = "unknown", background = "script", image = "../private.db", fit = "stretch", opacity = -9, influence = 400, material = "glass").normalized()
        assertEquals(130, value.fontScale)
        assertEquals("application", value.fontFamily)
        assertEquals("default", value.background)
        assertEquals("", value.image)
        assertEquals("cover", value.fit)
        assertEquals(85, value.opacity)
        assertEquals(100, value.influence)
        assertEquals("opaque", value.material)
    }

    @Test fun nativeAccentsAndButtonLabelsRemainReadableAcrossEveryPalette() {
        for (palette in nativePalettes) for (base in listOf("#11120F", "#F5F5F1")) for (accent in listOf(palette.primary, palette.secondary, "#FFFFFF", "#000000", "#FF00FF")) {
            val readable = nativeReadableAccent(accent, base)
            assertTrue("$readable on $base", nativeContrast(readable, base) >= 4.5)
            assertTrue(nativeContrast(accent, nativeForeground(accent)) >= 4.5)
        }
    }

    @Test fun imageReferencesOnlyResolveManagedFilesAndMissingImagesStayMissing() {
        val root = temporary.newFolder("native-backgrounds")
        val store = NativeBackgroundAssets(root)
        val reference = "background-01234567-89ab-cdef-0123-456789abcdef.png"
        assertNull(store.resolve(reference))
        val image = File(root, reference).apply { writeText("owned image") }
        assertEquals(image, store.resolve(reference))
        assertNull(store.resolve("../$reference"))
        assertNull(store.resolve(image.absolutePath))
        assertNull(store.resolve("content://provider/picture"))
        assertNull(store.resolve("file:///private.db"))
    }

    @Test fun opacityAndMaterialStayWithinTheOldAndroidLimits() {
        assertEquals(0.96f, NativeAppearancePreferences().surfaceAlpha)
        assertEquals(0.78f, NativeAppearancePreferences(opacity = 85, material = "translucent").surfaceAlpha, 0.0001f)
        assertEquals(1f, NativeAppearancePreferences(opacity = 100).surfaceAlpha)
    }
}
