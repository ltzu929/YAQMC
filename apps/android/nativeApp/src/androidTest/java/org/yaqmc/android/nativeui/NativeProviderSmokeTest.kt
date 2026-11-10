package org.yaqmc.android.nativeui

import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only, live-provider check. Run separately from deterministic fixture tests. */
@UnstableApi
class NativeProviderSmokeTest {
    @Test fun liveHomeDiscoverSearchAndEntityDetails() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val core = NativeCore(context)
        val repository = NativeCatalogRepository { method, params -> core.objectResult(method, params) }
        val receipt = JSONObject().put("source", "live-provider").put("accountWrites", false)
        try {
            val home = repository.load(NativeRoute(), false, 1, null)
            receipt.put("homeSections", home.sections.size)
            assertTrue(home.sections.isNotEmpty())
            val discover = repository.load(NativeRoute("discover"), false, 1, null)
            receipt.put("discoverTabs", org.json.JSONArray(discover.tabs.map { it.id }))
            assertTrue(discover.tabs.isNotEmpty())
            val results = JSONObject()
            for (kind in listOf("song", "artist", "album", "playlist")) {
                delay(2000)
                val search = repository.load(NativeRoute("search", query = "周杰伦", searchKind = kind), false, 1, null)
                val count = search.tracks.size + search.sections.sumOf { it.cards.size }
                results.put(kind, count)
                receipt.put("searchResults", results)
                assertTrue("No $kind results", count > 0)
                if (kind == "song") {
                    val song = repository.load(NativeRoute("song", search.tracks.first().id), false, 1, null)
                    assertTrue(song.links.any { it.kind == "artist" && it.id.isNotBlank() })
                    assertTrue(song.links.any { it.kind == "album" && it.id.isNotBlank() })
                    val album = repository.load(NativeRoute("album", song.links.first { it.kind == "album" }.id), false, 1, null)
                    receipt.put("albumTracks", album.tracks.size)
                    assertTrue(album.tracks.isNotEmpty())
                    val artistId = song.links.first { it.kind == "artist" }.id
                    for (tab in listOf("top", "song", "album")) {
                        val artist = repository.load(NativeRoute("artist", artistId, tab = tab), false, 1, null)
                        receipt.put("artist-$tab", artist.tracks.size + artist.sections.sumOf { it.cards.size })
                        assertTrue("Empty artist $tab", artist.sections.any { it.tracks.isNotEmpty() || it.cards.isNotEmpty() })
                    }
                }
            }
            receipt.put("passed", true)
        } catch (error: Throwable) {
            receipt.put("passed", false).put("errorCode", (error as? CoreFailure)?.code ?: error.javaClass.simpleName)
            throw error
        } finally {
            File(context.getExternalFilesDir(null), "batch2-provider-smoke.json").writeText(receipt.toString(2))
        }
    }
}
