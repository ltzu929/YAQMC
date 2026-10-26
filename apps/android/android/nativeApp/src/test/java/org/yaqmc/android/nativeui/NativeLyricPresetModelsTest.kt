package org.yaqmc.android.nativeui

import org.junit.Assert.*
import org.junit.Test

class NativeLyricPresetModelsTest {
    @Test fun builtinsStartAtClassicAndHaveThreeStableIds() {
        assertEquals(listOf("builtin.classic", "builtin.immersive", "builtin.vinyl"), nativeBuiltinLyricPresets.map { it.id })
        val state = NativeLyricPresetPreferences()
        assertEquals("classic", state.current().layout)
        assertEquals("immersive", state.select("builtin.immersive").current().layout)
        assertEquals("vinyl", state.select("builtin.vinyl").current().layout)
    }

    @Test fun editingBuiltinStoresOverrideWithoutChangingFactoryAndResetRemovesIt() {
        val original = nativeBuiltinLyricPresets.first()
        val changed = NativeLyricPresetPreferences().save(original.copy(layout = "vinyl", lyricsFraction = 0.68f))
        assertEquals("vinyl", changed.current().layout)
        assertEquals("classic", nativeBuiltinLyricPresets.first().layout)
        assertTrue("builtin.classic" in changed.overrides)
        assertEquals(original, changed.reset().current())
        assertTrue(changed.reset().overrides.isEmpty())
    }

    @Test fun duplicateRenameAndDeleteOnlyChangeTheCopy() {
        val original = NativeLyricPresetPreferences().select("builtin.vinyl")
        val copied = original.duplicate("custom.test-1", "My record")
        assertEquals("custom.test-1", copied.selectedId)
        assertEquals("vinyl", copied.current().layout)
        val renamed = copied.save(copied.current().copy(name = "New name", artworkScale = 1.2f))
        assertEquals("New name", renamed.current().name)
        assertEquals(1.2f, renamed.current().artworkScale)
        assertEquals("vinyl", renamed.select("builtin.vinyl").current().layout)
        val removed = renamed.deleteCustom()
        assertEquals("builtin.classic", removed.selectedId)
        assertTrue(removed.custom.isEmpty())
        assertEquals(original.current(), removed.select("builtin.vinyl").current())
    }

    @Test fun roundTripKeepsSelectionAndEdits() {
        val custom = NativeLyricPresetPreferences().select("builtin.immersive")
            .save(nativeBuiltinLyricPresets[1].copy(lyricsFraction = 0.65f))
            .duplicate("custom.test-2", "Night")
            .save(NativeLyricPreset("custom.test-2", "Night", "vinyl", 0.6f, 1.1f))
        assertEquals(custom, NativeLyricPresetPreferences.decode(custom.json()))
    }

    @Test fun invalidOrOldJsonFallsBackAndClampsValues() {
        assertEquals(NativeLyricPresetPreferences(), NativeLyricPresetPreferences.decode("not json"))
        val corrupt = """{"selectedId":"custom.missing","overrides":{"builtin.classic":{"layout":"invalid","lyricsFraction":99,"artworkScale":-5}},"custom":[{"id":"../escape","layout":"vinyl"}]}"""
        val recovered = NativeLyricPresetPreferences.decode(corrupt)
        assertEquals("builtin.classic", recovered.selectedId)
        assertEquals("classic", recovered.current().layout)
        assertEquals(0.75f, recovered.current().lyricsFraction)
        assertEquals(0.7f, recovered.current().artworkScale)
        assertTrue(recovered.custom.isEmpty())
    }

    @Test fun idGuardPreventsTamperingAndDuplicateIds() {
        val state = NativeLyricPresetPreferences().duplicate("custom.same", "One")
        assertEquals(state, state.duplicate("custom.same", "Two"))
        assertEquals(state, state.save(NativeLyricPreset("builtin.classic", layout = "vinyl")))
        assertEquals("builtin.classic", state.select("custom.unknown").selectedId)
    }
}
