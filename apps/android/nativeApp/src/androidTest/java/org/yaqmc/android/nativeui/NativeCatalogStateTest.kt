package org.yaqmc.android.nativeui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.yaqmc.android.R

/** Fake Core responses exercise cancellation and write policies without touching a real account. */
@UnstableApi
class NativeCatalogStateTest {
    @Test fun startupAccountSnapshotLoadsAccountHomeEvenWhenRestoreEventWasMissed() = runBlocking {
        val signedIn = java.util.concurrent.atomic.AtomicBoolean(false)
        val releaseRevalidation = CompletableDeferred<Unit>()
        val fake = FakeCore { method, _ -> when (method) {
            "provider_account_snapshot" -> if (signedIn.get()) authenticated() else JSONObject()
            "provider_account_refresh" -> { releaseRevalidation.await(); JSONObject() }
            "provider_home" -> {
                val id = if (signedIn.get()) "account-home" else "guest-home"
                JSONObject("""{"madeForYou":[{"id":"$id","title":"$id"}]}""")
            }
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            signedIn.set(true)
            main { model.observe() }
            await { model.page.value.page.sections.flatMap { it.cards }.any { it.id == "account-home" } }
            assertFalse(fake.calls.last { it.first == "provider_home" }.second.getBoolean("refresh"))
        }
    }

    @Test fun restoredAccountUsesCachedHomeWhileManualRefreshStillForcesNetwork() = runBlocking {
        val signedIn = java.util.concurrent.atomic.AtomicBoolean(false)
        val accountHomeStarted = CompletableDeferred<Unit>()
        val releaseAccountHome = CompletableDeferred<Unit>()
        val fake = FakeCore { method, _ -> when (method) {
            "provider_account_snapshot" -> if (signedIn.get()) authenticated() else JSONObject()
            "provider_home" -> {
                val id = if (signedIn.get()) "account-home" else "guest-home"
                if (signedIn.get() && !releaseAccountHome.isCompleted) {
                    accountHomeStarted.complete(Unit)
                    releaseAccountHome.await()
                }
                JSONObject("""{"madeForYou":[{"id":"$id","title":"$id"}]}""")
            }
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            signedIn.set(true)
            main { model.onCoreEvent(1, "account://changed", "{}") }
            accountHomeStarted.await()
            assertTrue(model.page.value.loading)
            assertEquals("guest-home", model.page.value.page.sections.single().cards.single().id)
            releaseAccountHome.complete(Unit)
            await { model.page.value.page.sections.flatMap { it.cards }.any { it.id == "account-home" } }
            val homes = fake.calls.filter { it.first == "provider_home" }
            assertEquals(2, homes.size)
            assertFalse(homes.last().second.getBoolean("refresh"))
            main { model.load(refresh = true) }
            await { fake.calls.count { it.first == "provider_home" } == 3 && model.page.value.loaded && !model.page.value.loading }
            assertTrue(fake.calls.last { it.first == "provider_home" }.second.getBoolean("refresh"))
            main { model.refreshAccountSession() }?.join()
            await { fake.calls.count { it.first == "provider_home" } == 4 && model.page.value.loaded && !model.page.value.loading }
            assertFalse(fake.calls.last { it.first == "provider_home" }.second.getBoolean("refresh"))
        }
    }

    @Test fun accountRevalidationDoesNotCancelAnInFlightHomeRefresh() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fake = FakeCore { method, params -> when (method) {
            "provider_account_snapshot" -> authenticated()
            "provider_home" -> {
                val id = if (params.getBoolean("refresh")) {
                    started.complete(Unit)
                    release.await()
                    "fresh-home"
                } else "cached-home"
                JSONObject("""{"madeForYou":[{"id":"$id","title":"$id"}]}""")
            }
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            main { model.refreshAccount(); model.load(refresh = true) }
            started.await()
            main { model.refreshAccountSession() }?.join()
            assertEquals(2, fake.calls.count { it.first == "provider_home" })
            release.complete(Unit)
            await { model.page.value.page.sections.flatMap { it.cards }.any { it.id == "fresh-home" } }
        }
    }

    @Test fun rootSwitchRestoresSearchCategoryAndChildBackStackWithoutAnotherRequest() = runBlocking {
        val fake = FakeCore { method, _ -> if (method == "provider_search") JSONObject("""{"items":[],"hasMore":false}""") else JSONObject() }
        withModel(fake) { model ->
            main { model.search("music", "album") }
            await { model.page.value.loaded }
            main { model.navigate(NativeRoute("album", "selected")) }
            await { model.page.value.loaded }
            main { model.selectRoot("library"); model.selectRoot("search") }
            assertEquals(NativeRoute("album", "selected"), model.route.value)
            assertEquals("search", model.rootDestination.value)
            main { assertTrue(model.back()) }
            assertEquals("music", model.route.value.query)
            assertEquals("album", model.route.value.searchKind)
            main { model.navigate(NativeRoute("album", "selected")); model.selectRoot("search") }
            assertEquals(NativeRoute("search", query = "music", searchKind = "album"), model.route.value)
            main { assertFalse(model.back()) }
            main { model.selectRoot("search") }
            assertEquals(1, fake.calls.count { it.first == "provider_search" })
        }
    }

    @Test fun cancelledCardCannotClearTheNewPreparationOrPlayItsOldResponse() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        val fake = FakeCore { method, params ->
            if (method == "provider_playlist") {
                val id = params.text("id")
                if (id == "old") { oldStarted.complete(Unit); withContext(NonCancellable) { withTimeout(5000) { oldRelease.await() } } }
                else newRelease.await()
                JSONObject("""{"id":"$id","tracks":[{"id":"$id","title":"$id"}]}""")
            } else JSONObject()
        }
        withModel(fake) { model ->
            main { model.playCard(CatalogItem("playlist", JSONObject().put("id", "old"))) }
            oldStarted.await()
            main { model.playCard(CatalogItem("playlist", JSONObject().put("id", "new"))) }
            await { fake.calls.count { it.first == "provider_playlist" } == 2 }
            oldRelease.complete(Unit)
            delay(100)
            assertEquals("playlist:new", model.preparingCard.value)
            assertNull(model.failedCard.value)
            assertFalse(fake.calls.any { it.first == "player_play_tracks" })
            newRelease.complete(Unit)
            await { model.preparingCard.value == null }
            val play = fake.calls.single { it.first == "player_play_tracks" }
            assertEquals("new", play.second.getJSONObject("request").text("startAtId"))
        }
    }

    @Test fun cancelledSearchCannotOverwriteNewerResults() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val fake = FakeCore { method, params ->
            if (method == "provider_search") {
                val query = params.getString("query")
                if (query == "old") {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { delay(200) }
                }
                JSONObject("""{"items":[{"id":"$query"}],"hasMore":false}""")
            } else JSONObject()
        }
        withModel(fake) { model ->
            main { model.search("old") }
            oldStarted.await()
            main { model.search("new") }
            await { model.page.value.page.tracks.firstOrNull()?.id == "new" }
            delay(300)
            assertEquals("new", model.page.value.page.tracks.single().id)
            assertFalse(model.page.value.loading)
        }
    }

    @Test fun pagingFailureKeepsRowsAndRetryAndCachedReturnUseCorrectPage() = runBlocking {
        var failSecond = true
        val pages = mutableListOf<Int>()
        val fake = FakeCore { method, params ->
            if (method != "provider_search") JSONObject() else {
                val number = params.getInt("page")
                pages += number
                if (number == 2 && failSecond) { failSecond = false; throw CoreFailure("timeout", "") }
                JSONObject("""{"items":[{"id":"$number"}],"hasMore":${number < 3}}""")
            }
        }
        withModel(fake) { model ->
            main { model.search("music") }
            await { model.page.value.loaded }
            main { model.load(more = true) }
            await { model.page.value.moreError != null }
            assertEquals(listOf("1"), model.page.value.page.tracks.map { it.id })
            assertNull(model.page.value.error)
            assertFalse(model.page.value.loadingMore)
            main { model.load(more = true) }
            await { model.page.value.page.tracks.size == 2 }
            main { model.navigate(NativeRoute("library")); assertTrue(model.back()); model.load(more = true) }
            await { model.page.value.page.tracks.size == 3 }
            assertEquals(listOf(1, 2, 2, 3), pages)
            assertFalse(model.page.value.page.hasMore)
        }
    }

    @Test fun timeoutEndsInitialLoadingAndRefreshFailureKeepsContent() = runBlocking {
        var fail = true
        val fake = FakeCore { method, _ ->
            if (method == "provider_discover" && fail) throw CoreFailure("timeout", "")
            if (method == "provider_discover") JSONObject("""{"charts":[{"id":"chart","title":"Chart"}]}""") else JSONObject()
        }
        withModel(fake) { model ->
            main { model.navigate(NativeRoute("discover"), true) }
            await { model.page.value.error != null }
            assertFalse(model.page.value.loading)
            assertEquals(R.string.native_timeout, model.page.value.error!!.resource)
            fail = false
            main { model.load(refresh = true) }
            await { model.page.value.loaded }
            fail = true
            main { model.load(refresh = true) }
            await { model.page.value.error != null }
            assertEquals("chart", model.page.value.page.sections.single().cards.single().id)
        }
    }

    @Test fun favoriteWaitStatePreventsDuplicateWritesAndUnknownOutcomeDoesNotApply() = runBlocking {
        val response = CompletableDeferred<Unit>()
        val fake = FakeCore { method, params ->
            when (method) {
                "provider_account_snapshot" -> authenticated()
                "provider_set_favorite" -> {
                    response.await()
                    JSONObject().put("authRevision", 1).put("status", "outcome-unknown").put("favorite", true)
                        .put("clientOperationId", params.getJSONObject("request").getString("clientOperationId"))
                }
                else -> JSONObject()
            }
        }
        withModel(fake) { model ->
            val track = writableTrack()
            main { model.refreshAccount(); model.favorite(track); model.favorite(track) }
            await { fake.calls.count { it.first == "provider_set_favorite" } == 1 }
            assertTrue(track.key in model.pendingFavorites.value)
            response.complete(Unit)
            await { model.pendingFavorites.value.isEmpty() }
            assertNull(model.favorites.value[track.key])
            assertEquals(R.string.native_mutation_unknown, model.message.value!!.resource)
            assertEquals(1, fake.calls.count { it.first == "provider_set_favorite" })
        }
    }

    @Test fun accountRevisionChangeDropsOldPageAndRejectsLateMutationReceipt() = runBlocking {
        var revision = 1L
        val response = CompletableDeferred<Unit>()
        val fake = FakeCore { method, _ -> when (method) {
            "provider_account_snapshot" -> authenticated(revision)
            "provider_favorite_songs" -> JSONObject("""{"items":[{"id":"private-$revision"}],"authRevision":$revision}""")
            "provider_set_favorite" -> { response.await(); JSONObject("""{"authRevision":1,"status":"applied","favorite":true}""") }
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            main { model.refreshAccount(); model.navigate(NativeRoute("favorites")) }
            await { model.page.value.page.tracks.isNotEmpty() }
            main { model.favorite(writableTrack()) }
            await { model.pendingFavorites.value.isNotEmpty() }
            revision = 2
            main { model.refreshAccount() }
            assertTrue(model.page.value.page.tracks.isEmpty())
            response.complete(Unit)
            await { model.pendingFavorites.value.isEmpty() }
            assertFalse(model.favorites.value.keys.any { it.contains("private-1") || it == writableTrack().key })
            main { model.load(refresh = true) }
            await { model.page.value.page.tracks.firstOrNull()?.id == "private-2" }
        }
    }

    @Test fun lateFavoriteHydrationCannotUndoConfirmedUnfavorite() = runBlocking {
        val oldFavorites = CompletableDeferred<Unit>()
        val fake = FakeCore { method, _ -> when (method) {
            "provider_account_snapshot" -> authenticated()
            "provider_favorite_songs" -> {
                oldFavorites.await()
                JSONObject("""{"items":[{"id":"qqmusic:track:1","isFavorite":true}],"authRevision":1}""")
            }
            "provider_set_favorite" -> JSONObject("""{"authRevision":1,"status":"applied","favorite":false}""")
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            main { model.refreshAccount() }
            await { fake.calls.any { it.first == "provider_favorite_songs" } }
            val track = Track(JSONObject(writableTrack().json.toString()).put("isFavorite", true))
            main { model.favorite(track) }
            await { model.favorites.value[track.key] == false }
            oldFavorites.complete(Unit)
            delay(100)
            assertEquals(false, model.favorites.value[track.key])
            val request = fake.calls.single { it.first == "provider_set_favorite" }.second.getJSONObject("request")
            assertFalse(request.getBoolean("favorite"))
        }
    }

    @Test fun playlistPickerFiltersPermissionsAndPaginatesWithoutWriting() = runBlocking {
        val fake = FakeCore { method, params -> when (method) {
            "provider_account_snapshot" -> authenticated()
            "provider_account_playlists" -> if (params.isNull("cursor")) JSONObject("""{"items":[{"id":"read","ownership":"owned","capabilities":{"canAddTracks":false}},{"id":"collected","ownership":"collected","capabilities":{"canAddTracks":true}}],"nextCursor":"next","authRevision":1}""")
                else JSONObject("""{"items":[{"id":"writable","ownership":"owned","capabilities":{"canAddTracks":true}}],"authRevision":1}""")
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            main { model.refreshAccount(); model.openPicker(writableTrack()) }
            await { model.picker.value?.loading == false }
            assertTrue(model.picker.value!!.playlists.isEmpty())
            assertEquals("next", model.picker.value!!.cursor)
            main { model.loadPicker() }
            await { model.picker.value?.playlists?.size == 1 }
            assertEquals("writable", model.picker.value!!.playlists.single().id)
            assertFalse(fake.calls.any { it.first == "provider_add_playlist_track" })
        }
    }

    @Test fun playlistWriteGuardsAndPendingStateUseCapabilities() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val fake = FakeCore { method, params -> when (method) {
            "provider_account_snapshot" -> authenticated()
            "provider_rename_playlist" -> { release.await(); JSONObject().put("status", "applied").put("authRevision", 1).put("playlist", JSONObject().put("id", "p").put("title", params.getJSONObject("request").getString("title"))) }
            else -> JSONObject()
        } }
        withModel(fake) { model ->
            main {
                model.refreshAccount()
                val readOnly = JSONObject("""{"id":"p","capabilities":{"canRename":false}}""")
                model.mutatePlaylist("rename", readOnly, title = "Name")
                model.mutatePlaylist("delete", readOnly)
                val writable = JSONObject("""{"id":"p","capabilities":{"canRename":true}}""")
                model.mutatePlaylist("rename", writable, title = "\n")
                model.mutatePlaylist("rename", writable, title = " New name ")
                model.mutatePlaylist("rename", writable, title = "Second")
            }
            await { fake.calls.any { it.first == "provider_rename_playlist" } }
            assertEquals(setOf("p"), model.pendingPlaylists.value)
            release.complete(Unit)
            await { model.pendingPlaylists.value.isEmpty() }
            assertEquals("New name", model.playlistSummaries.value["p"]!!.getString("title"))
            assertEquals(1, fake.calls.count { it.first == "provider_rename_playlist" })
            assertFalse(fake.calls.any { it.first == "provider_delete_playlist" })
        }
    }

    private suspend fun withModel(fake: FakeCore, block: suspend (NativeViewModel) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model = withContext(Dispatchers.Main) { NativeViewModel(app, fake) }
        try { await { model.page.value.loaded }; block(model) }
        finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel(); model.closePicker(); model.stopObserving() } }
    }
    private suspend fun <T> main(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }
    private suspend fun await(condition: () -> Boolean) { withTimeout(5000) { while (!condition()) delay(10) } }
    private class FakeCore(val respond: suspend (String, JSONObject) -> JSONObject) : NativeCoreApi {
        val calls = java.util.concurrent.CopyOnWriteArrayList<Pair<String, JSONObject>>()
        override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
            calls += method to JSONObject(params.toString())
            return respond(method, params)
        }
    }
    private fun writableTrack() = Track(JSONObject("""{"id":"qqmusic:track:1","title":"Test song","provider":{"providerId":"qqmusic","trackId":"1"}}"""))
    private fun authenticated(revision: Long = 1) = JSONObject("""{"state":"authenticated","revision":$revision,"profile":{"nickname":"Test","maskedIdentity":"test"},"capabilities":{"favoriteRead":true,"favoriteWrite":true,"playlistRead":true,"playlistWrite":true}}""")
}
