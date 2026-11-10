package org.yaqmc.android.nativeui

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeCatalogTest {
    @Test fun discoverPreservesAllSevenCategoriesAndDisplayOnlyContent() {
        val page = NativeModels.discover(JSONObject("""{"featured":[{"id":"f","title":"Feature"}],"popularSonglists":[{"id":"p"}],"charts":[{"id":"c"}],"newSongs":{"tracks":[{"id":"s"}]},"newAlbums":[{"id":"a"}],"categories":[{"encArea":"x"}],"newMvs":[{"id":"m","artist":"Singer"}],"podcasts":[{"id":"pod"}]}"""))
        assertEquals(listOf("featured", "charts", "newSongs", "newAlbums", "categories", "newMvs", "podcasts"), page.tabs.map { it.id })
        val mv = page.sections.single { it.tab == "newMvs" }.cards.single()
        assertFalse(mv.navigable)
        assertFalse(mv.playable)
        assertEquals("Singer", mv.subtitle)
        assertTrue(page.sections.single { it.tab == "categories" }.cards.single().navigable)
        assertEquals("s", page.sections.single { it.tab == "newSongs" }.tracks.single().id)
    }

    @Test fun recommendationEntriesKeepContinuationAndProviderPayload() {
        val page = NativeModels.home(JSONObject("""{"guessSonglist":{"id":"g","tracks":[{"id":"s","provider":{"trackId":"7"}}]},"radarBasedOnSong":"Seed","radarSongs":[{"id":"r"}]}"""))
        assertEquals("guess", page.sections.first().cards.single().continuation)
        assertEquals("7", page.sections.first().cards.single().json.getJSONArray("tracks").getJSONObject(0).getJSONObject("provider").getString("trackId"))
        assertEquals("radar", page.sections[1].continuation)
        assertEquals(listOf("Seed"), page.sections[1].title.arguments)
    }

    @Test fun paginationMergeRetainsUnequalSectionsAndDeduplicatesBoundaryRows() {
        val first = NativeModels.detail("artist", JSONObject("""{"id":"a","name":"Artist","topSongs":[{"id":"1"},{"id":"2"}],"albums":[{"id":"album"}]}"""))
        val next = first.copy(sections = listOf(first.sections.first().copy(tracks = listOf(Track(JSONObject("""{"id":"2"}""")), Track(JSONObject("""{"id":"3"}"""))))), hasMore = false)
        val merged = NativeModels.merge(first, next)
        assertEquals(listOf("1", "2", "3"), merged.tracks.map { it.id })
        assertEquals("album", merged.sections.last().cards.single().id)
        assertFalse(merged.hasMore)
    }

    @Test fun albumAndAccountDetailsKeepMetadataLinksAndCursor() {
        val album = NativeModels.detail("album", JSONObject("""{"id":"a","title":"Album","artist":{"id":"artist","name":"Name"},"releaseYear":2026,"genre":"Pop","tracks":[{"id":"s","durationMs":120000}]}"""))
        assertEquals(EntityLink("artist", "artist", "Name"), album.links.single())
        assertEquals(listOf(1, "2:00"), album.metadata.first().arguments)
        assertTrue(album.metadata.any { it.value == "2026" })
        val detail = NativeModels.detail("account-playlist", JSONObject("""{"summary":{"id":"p","ownership":"owned","capabilities":{"canRename":true}},"tracks":{"items":[],"nextCursor":"cursor","total":100,"stale":true,"authRevision":9}}"""))
        assertTrue(detail.stale)
        assertEquals(100, detail.total)
        assertEquals("cursor", detail.cursor)
        assertTrue(detail.raw.getJSONObject("summary").canEdit("canRename"))
    }

    @Test fun searchesUseFourKindsAndPreservePageNumbers() = runBlocking {
        val seen = mutableListOf<Pair<String, JSONObject>>()
        val repository = NativeCatalogRepository { method, params ->
            seen += method to params
            JSONObject("""{"items":[{"id":"result"}],"hasMore":true}""")
        }
        for (kind in listOf("song", "artist", "album", "playlist")) {
            val page = repository.load(NativeRoute("search", query = "keyword", searchKind = kind), false, 3, null)
            assertTrue(page.hasMore)
            assertEquals("result", if (kind == "song") page.tracks.single().id else page.sections.single().cards.single().id)
        }
        assertEquals(listOf("song", "artist", "album", "playlist"), seen.map { it.second.getString("kind") })
        assertTrue(seen.all { it.first == "provider_search" && it.second.getInt("page") == 3 && it.second.getInt("limit") == 30 })
    }

    @Test fun blankSearchUsesHomeRecommendationsAndArtistCatalogUsesItsOwnEndpoint() = runBlocking {
        val seen = mutableListOf<Pair<String, JSONObject>>()
        val repository = NativeCatalogRepository { method, params ->
            seen += method to params
            when (method) {
                "provider_home" -> JSONObject("""{"madeForYou":[{"id":"playlist","tracks":[{"id":"song"}]}]}""")
                "provider_artist" -> JSONObject("""{"id":"artist","name":"Artist"}""")
                else -> JSONObject("""{"items":[{"id":"album"}],"hasMore":true}""")
            }
        }
        val blank = repository.load(NativeRoute("search"), false, 1, null)
        assertEquals("playlist", blank.sections.single().cards.single().id)
        val albums = repository.load(NativeRoute("artist", "artist", tab = "album"), false, 2, null)
        assertEquals("album", albums.sections.single().cards.single().kind)
        assertEquals("provider_artist_catalog", seen.last().first)
        assertEquals(2, seen.last().second.getInt("page"))
        assertEquals("album", seen.last().second.getString("kind"))
    }

    @Test fun accountCursorIsOpaqueAndStaleFlagIsNotLost() = runBlocking {
        var captured = JSONObject()
        val repository = NativeCatalogRepository { _, params -> captured = params; JSONObject("""{"items":[],"nextCursor":"next","stale":true,"total":250,"authRevision":4}""") }
        val page = repository.load(NativeRoute("account-playlists"), false, 2, "opaque cursor")
        assertEquals("opaque cursor", captured.getString("cursor"))
        assertTrue(page.stale)
        assertEquals(250, page.total)
        assertEquals(4L, page.raw.getLong("authRevision"))
    }

    @Test fun deadlineIsRecoverableAndReleasesWorkWhileNullRemainsAValidResult() = runBlocking {
        var released = false
        try {
            withCoreDeadline(10) { try { delay(1000) } finally { released = true } }
            fail("deadline must fail")
        } catch (error: CoreFailure) { assertEquals("timeout", error.code) }
        assertTrue(released)
        assertNull(withCoreDeadline<Any?> { null })
    }

    @Test fun parentCancellationIsNotConvertedToARequestFailure() = runBlocking {
        var released = false
        val job = launch { withCoreDeadline(1000) { try { delay(10_000) } finally { released = true } } }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(released)
    }

    @Test fun accountProjectionPreservesEntitlementsAndGatesForeignAndExpiredWrites() {
        val account = AccountUi.parse(JSONObject("""{"state":"authenticated","revision":3,"profile":{"nickname":"N","maskedIdentity":"1***9"},"capabilities":{"playlistWrite":true,"favoriteRead":true},"entitlement":{"tier":"super-vip","membership":"expired","expiresAtMs":null,"secondaryEntitlements":["trial"]}}"""))
        val local = Track(JSONObject("""{"id":"qqmusic:track:1","provider":{"providerId":"qqmusic","trackId":"1"}}"""))
        assertEquals("1***9", account.identity)
        assertEquals("expired", account.entitlement!!.getString("membership"))
        assertTrue(account.canWrite(local)) // Membership expiry does not revoke provider-declared playlist rights.
        assertFalse(account.copy(state = "session-expired").canWrite(local))
        assertFalse(account.canWrite(Track(JSONObject("""{"id":"other","provider":{"providerId":"other","trackId":"1"}}"""))))
        assertFalse(AccountUi.parse(JSONObject("""{"state":"expired","attemptId":"old"}""")).loginPending)
        assertTrue(account.canRead("favorites"))
        assertFalse(account.canRead("recent"))
    }

    @Test fun playlistTitleValidationCountsCodePointsAndRejectsInvisibleControls() {
        assertTrue(validPlaylistTitle(" 😀音乐 "))
        assertTrue(validPlaylistTitle("😀".repeat(80)))
        assertFalse(validPlaylistTitle("😀".repeat(81)))
        assertFalse(validPlaylistTitle(" "))
        assertFalse(validPlaylistTitle("name\u200Bhidden"))
        assertFalse(validPlaylistTitle("name\nline"))
    }

    @Test fun loginBusinessCodeSurvivesProjectionWithoutChangingAuthentication() {
        val failed = AccountUi.parse(JSONObject("""{"state":"protocol-error","upstreamCode":20279}"""))
        assertEquals(20279, failed.upstreamCode)
        assertFalse(failed.authenticated)
        assertFalse(failed.loginPending)
        assertNull(AccountUi.parse(JSONObject("""{"state":"protocol-error"}""")).upstreamCode)
        assertNull(AccountUi.parse(JSONObject("""{"state":"protocol-error","upstreamCode":null}""")).upstreamCode)
    }
}
