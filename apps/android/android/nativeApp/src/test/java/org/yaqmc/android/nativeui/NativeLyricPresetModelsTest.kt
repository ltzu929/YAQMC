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
            .save(NativeLyricPreset("custom.test-2", "Night", "vinyl", 0.6f, 1.1f,
                showArtwork = false, showMetadata = false))
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
        assertTrue(recovered.current().showArtwork)
        assertTrue(recovered.current().showMetadata)
    }

    @Test fun idGuardPreventsTamperingAndDuplicateIds() {
        val state = NativeLyricPresetPreferences().duplicate("custom.same", "One")
        assertEquals(state, state.duplicate("custom.same", "Two"))
        assertEquals(state, state.save(NativeLyricPreset("builtin.classic", layout = "vinyl")))
        assertEquals("builtin.classic", state.select("custom.unknown").selectedId)
    }

    @Test fun draftHistoryKeepsUndoRedoSeparateFromSavedPreferences() {
        val factory = NativeLyricPresetPreferences().current()
        val start = NativeLyricPresetEditHistory(factory)
        val first = start.edit(factory.copy(lyricsFraction = 0.6f))
        val second = first.edit(factory.copy(lyricsFraction = 0.7f))
        assertEquals(0.7f, second.current.lyricsFraction)
        assertEquals(2, second.undoSteps.size)
        assertTrue(second.redoSteps.isEmpty())
        val undone = second.undo()
        assertEquals(0.6f, undone.current.lyricsFraction)
        assertEquals(0.7f, undone.redo().current.lyricsFraction)
        assertEquals(undone, undone.edit(undone.current))
        val branched = undone.edit(factory.copy(artworkScale = 1.2f))
        assertTrue(branched.redoSteps.isEmpty())
        assertEquals(branched, branched.redo())
        assertEquals(factory, NativeLyricPresetPreferences().current())
    }

    @Test fun artworkResizeClampsInvalidValuesAndSnapsBackToNormalSize() {
        assertEquals(1f, nativeSnapArtworkScale(1.02f))
        assertEquals(1f, nativeSnapArtworkScale(0.98f))
        assertEquals(0.7f, nativeSnapArtworkScale(-10f))
        assertEquals(1.35f, nativeSnapArtworkScale(10f))
        assertEquals(1f, nativeSnapArtworkScale(Float.NaN))
        val initial = NativeLyricPresetPreferences()
        val edited = NativeLyricPresetEditHistory(initial.current()).edit(
            initial.current().copy(artworkScale = 1.2f))
        assertEquals(initial.current(), edited.undo().current)
        assertEquals(1.2f, edited.undo().redo().current.artworkScale)
        assertEquals(1f, initial.current().artworkScale)
        val persisted = initial.save(edited.current)
        assertEquals(1.2f, NativeLyricPresetPreferences.decode(persisted.json()).current().artworkScale)
    }

    @Test fun layerVisibilitySupportsUndoRedoCopyResetAndOlderPresetJson() {
        val original = NativeLyricPresetPreferences()
        val hidden = original.current().copy(showArtwork = false, showMetadata = false)
        val draft = NativeLyricPresetEditHistory(original.current()).edit(hidden)
        assertTrue(draft.undo().current.showArtwork)
        assertFalse(draft.undo().redo().current.showMetadata)
        assertTrue(original.current().showMetadata) // Unsaved edits never change the active preset.

        val saved = original.save(draft.current)
        assertFalse(saved.current().showArtwork)
        assertFalse(saved.current().showMetadata)
        val copied = saved.duplicate("custom.hidden", "Minimal")
        assertFalse(copied.current().showArtwork)
        assertFalse(NativeLyricPresetPreferences.decode(copied.json()).current().showMetadata)
        assertEquals(nativeBuiltinLyricPresets.first(), saved.reset().current())

        val legacy = NativeLyricPresetPreferences.decode(
            """{"schemaVersion":2,"selectedId":"builtin.classic","overrides":{"builtin.classic":{"id":"builtin.classic","artworkScale":1.2}}}""")
        assertEquals(1.2f, legacy.current().artworkScale)
        assertTrue(legacy.current().showArtwork)
        assertTrue(legacy.current().showMetadata)
    }

    @Test fun historyIsBoundedAndDraggingSnapsOnlyCommittedFraction() {
        val factory = NativeLyricPresetPreferences().current()
        val history = (0..30).fold(NativeLyricPresetEditHistory(factory)) { state, index ->
            state.edit(factory.copy(name = "step-$index"))
        }
        assertEquals(24, history.undoSteps.size)
        assertEquals("step-30", history.current.name)
        assertEquals("step-29", history.undo().current.name)
        assertEquals(0.5f, nativeSnapLyricsFraction(0.492f))
        assertEquals(0.35f, nativeSnapLyricsFraction(-9f))
        assertEquals(0.75f, nativeSnapLyricsFraction(9f))
        assertEquals(0.55f, nativeSnapLyricsFraction(Float.NaN))
    }
}
