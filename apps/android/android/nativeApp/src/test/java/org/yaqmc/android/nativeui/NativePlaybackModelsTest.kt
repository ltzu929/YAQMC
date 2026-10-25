package org.yaqmc.android.nativeui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.yaqmc.android.R

class NativePlaybackModelsTest {
    @Test fun duplicateSongsKeepEntryIdentityAndCoreTraversalOrder() {
        val value = queue().put("currentIndex", 2).put("currentQueueEntryId", "a")
            .put("upcomingQueueEntryIds", JSONArray(listOf("c", "b", "missing", "a", "b")))
            .put("playbackOrder", "shuffle").put("repeat", "one")
        val state = NativePlayback.decode(value)
        assertEquals(0, state.currentIndex)
        assertEquals(listOf("c", "b"), state.upcoming.map { it.id })
        assertEquals(listOf(2, 1), state.upcoming.map { it.index })
        assertEquals("same-song", state.upcoming.last().track.id)
        assertEquals("repeat-one", state.mode)
        assertEquals("shuffle", state.order)
    }

    @Test fun emptyAuthoritativeUpcomingDoesNotInventMoreSongs() {
        val state = NativePlayback.decode(queue().put("upcomingQueueEntryIds", JSONArray()))
        assertTrue(state.authoritative)
        assertTrue(state.upcoming.isEmpty())
    }

    @Test fun legacySnapshotFallsBackToIndexesWithoutInventingCoreEntryIds() {
        val value = queue()
        value.remove("queueEntries")
        val state = NativePlayback.decode(value)
        assertFalse(state.authoritative)
        assertEquals(listOf(1, 2), state.upcoming.map { it.index })
        assertNotEquals(state.entries[0].id, state.entries[1].id)
    }

    @Test fun malformedEntryIdsCannotMakeDuplicateRowsTargetTheSameCoreEntry() {
        val value = queue().put("queueEntries", JSONArray("""[{"id":"a"},{"id":"a"},{"id":"c"}]"""))
        val state = NativePlayback.decode(value)
        assertFalse(state.authoritative)
        assertEquals(3, state.entries.map { it.id }.distinct().size)
    }

    @Test fun qualityKeepsRequestedResolvedAndAllCapabilityDimensions() {
        val state = NativePlayback.decode(queue().put("sourceSelection", JSONObject("""{
            "requestedQuality":"automatic","resolvedQuality":"high","fallbackReason":"account-rights","preview":true,
            "qualityCapabilities":[
                {"quality":"master","entitlement":"denied","resource":"available","client":"supported","playable":false},
                {"quality":"hi-res","entitlement":"allowed","resource":"unavailable","client":"supported"},
                {"quality":"lossless","entitlement":"allowed","resource":"available","client":"unsupported"},
                {"quality":"high","entitlement":"unknown","resource":"unknown","client":"supported","playable":false}
            ]}""")))
        assertEquals("automatic", state.selection.requested)
        assertEquals("high", state.quality)
        assertTrue(state.selection.preview)
        assertEquals(listOf(true, true, true, false), state.selection.capabilities.map { it.unavailable })
        assertEquals(R.string.native_fallback_rights, NativePlayback.fallback(state.selection.fallback))
    }

    @Test fun failureCodesAndBusyPhasesHaveLocalizedFeedback() {
        val state = NativePlayback.decode(queue().put("playbackState", "recoverable-error")
            .put("playbackError", JSONObject("""{"code":"track-unavailable","message":"Provider detail","retryable":true}""")))
        assertEquals(R.string.native_error_track, NativePlayback.status(state)?.resource)
        assertEquals("Provider detail", state.error)
        assertTrue(state.retryable)
        assertTrue(state.copy(phase = "buffering").busy)
        assertEquals(R.string.native_playback_buffering, NativePlayback.status(state.copy(phase = "buffering"))?.resource)
        assertNull(NativePlayback.status(state.copy(phase = "paused")))
        assertEquals(120000L, state.duration)
    }

    @Test fun missingOrClearedSourceSelectionDoesNotClaimAutomaticQuality() {
        for (selection in listOf(null, JSONObject.NULL, JSONObject())) {
            val state = NativePlayback.decode(queue().put("sourceSelection", selection))
            assertEquals("", state.selection.requested)
            assertEquals(R.string.native_select_quality, NativePlayback.qualityLabel(state.selection.requested))
        }
    }

    private fun queue(): JSONObject {
        val tracks = JSONArray("""[{"id":"same-song","durationMs":120000},{"id":"same-song"},{"id":"other-song"}]""")
        return JSONObject().put("queue", tracks).put("queueEntries", JSONArray("""[{"id":"a"},{"id":"b"},{"id":"c"}]"""))
            .put("currentIndex", 0).put("playbackDurationMs", JSONObject.NULL)
    }
}
