package org.yaqmc.android.nativeui

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

@UnstableApi
class NativeBrowseTest {
    private val compose = createAndroidComposeRule<NativeActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeDevicePreferences()).around(compose)
    private var fakeModel: NativeViewModel? = null
    private val calls = java.util.concurrent.CopyOnWriteArrayList<Pair<String, JSONObject>>()
    @After fun clearModel() { compose.runOnIdle { fakeModel?.viewModelScope?.cancel() } }

    @Test fun searchDebounceClearFourKindsAndEntityNavigation() {
        val model = installFake()
        compose.onNodeWithTag("nav-search").performClick()
        compose.onNodeWithTag("search-input").performTextInput("Music")
        compose.waitUntil(5000) { model.page.value.page.tracks.isNotEmpty() }
        assertEquals(1, calls.count { it.first == "provider_search" })
        compose.onNodeWithText("Artist name").performClick()
        compose.waitUntil(5000) { model.route.value.kind == "artist" && model.page.value.loaded }
        compose.onNodeWithTag("catalog-tab-song").performClick()
        compose.waitUntil(5000) { calls.any { it.first == "provider_artist_catalog" && it.second.text("kind") == "song" } }
        compose.onNodeWithTag("catalog-tab-album").performClick()
        compose.waitUntil(5000) { calls.any { it.first == "provider_artist_catalog" && it.second.text("kind") == "album" } }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        for (kind in listOf("artist", "album", "playlist")) {
            compose.onNodeWithTag("search-kind-$kind").performClick()
            compose.waitUntil(5000) { model.page.value.loaded && model.route.value.searchKind == kind }
            assertTrue(calls.any { it.first == "provider_search" && it.second.text("kind") == kind })
        }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitUntil(5000) { model.route.value.query.isBlank() && model.page.value.loaded }
        compose.onNodeWithText("Browse playlist").assertIsDisplayed()
        capture("batch2-search-empty")
    }

    @Test fun discoverTabsAreRememberedAndDisplayOnlyCardsDoNotNavigate() {
        val model = installFake()
        compose.onNodeWithTag("nav-discover").performClick()
        compose.waitUntil(5000) { model.page.value.page.tabs.size == 7 }
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasTestTag("catalog-tabs"))
        // Tabs scroll horizontally on phones.
        compose.onNodeWithTag("catalog-tabs").performScrollToNode(hasTestTag("catalog-tab-newMvs"))
        compose.onNodeWithTag("catalog-tab-newMvs").performClick()
        compose.onNodeWithText("Display MV").performClick()
        compose.runOnIdle { assertEquals("discover", model.route.value.kind) }
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithTag("nav-discover").performClick()
        compose.onNodeWithTag("catalog-tabs").performScrollToNode(hasTestTag("catalog-tab-newMvs"))
        compose.onNodeWithTag("catalog-tab-newMvs").assertIsSelected()
        capture("batch2-discover-mv")
        assertEquals(1, calls.count { it.first == "provider_discover" })
    }

    @Test fun imeCompositionWaitsForCommitBeforeSearching() {
        val model = installFake()
        compose.onNodeWithTag("nav-search").performClick()
        compose.onNodeWithTag("search-input").performClick()
        lateinit var connection: android.view.inputmethod.InputConnection
        compose.runOnIdle {
            connection = requireNotNull(compose.activity.currentFocus?.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
            assertTrue(connection.setComposingText("Composing", 1))
        }
        Thread.sleep(400)
        assertFalse(calls.any { it.first == "provider_search" })
        compose.runOnIdle { assertTrue(connection.finishComposingText()) }
        compose.waitUntil(5000) { model.route.value.query == "Composing" && model.page.value.loaded }
        assertEquals(1, calls.count { it.first == "provider_search" })
    }

    @Test fun membershipReadOnlyPlaylistAndWritablePickerAreVisibleWithoutAccountWrites() {
        val model = installFake()
        compose.runOnIdle { model.navigate(NativeRoute("account-playlist", "p", playlist = summary.toString())) }
        compose.waitUntil(5000) { model.page.value.loaded && model.page.value.page.tracks.isNotEmpty() }
        compose.onNodeWithText("Read only").assertIsDisplayed()
        compose.onNodeWithText("Rename playlist").assertDoesNotExist()
        compose.onNodeWithTag("catalog-list").performScrollToNode(hasContentDescription("More actions for Song title"))
        compose.onNodeWithContentDescription("More actions for Song title").assertIsDisplayed()
        compose.onNodeWithContentDescription("More actions for Song title").performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Add to playlist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add to playlist").performClick()
        compose.waitUntil(5000) { model.picker.value?.loading == false }
        compose.onNodeWithText("Writable playlist").assertIsDisplayed()
        compose.onNodeWithText("Read-only playlist").assertDoesNotExist()
        compose.onNodeWithText("Writable playlist").performClick()
        compose.waitUntil(5000) { model.playlistErrors.value["write"] != null }
        compose.onNode(hasText("The server rejected the change.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Open account settings:", substring = true).performClick()
        compose.onNodeWithText("Super VIP · Active").assertIsDisplayed()
        compose.onNodeWithText("Additional benefits: Trial · Ad free").assertIsDisplayed()
        capture("batch2-membership-fixture")
        assertEquals(1, calls.count { it.first == "provider_add_playlist_track" })
        assertFalse(calls.any { it.first in setOf("provider_set_favorite", "provider_rename_playlist", "provider_delete_playlist") })
    }

    @Test fun loginDeviceLimitShowsActionableFeedbackWithoutAutomaticRetry() {
        val model = installFake(JSONObject("""{"state":"protocol-error","upstreamCode":20279,"capabilities":{}}"""))
        compose.runOnIdle { model.navigate(NativeRoute("settings")) }
        val message = "QQ Music has reached its login device limit (20279). Remove unused login devices in the official QQ Music app, then authorize again."
        compose.onAllNodesWithText(message).assertCountEquals(2)
        compose.onNodeWithText("Unexpected authorization response. Please retry.").assertDoesNotExist()
        capture("login-device-limit")
        assertFalse(calls.any { it.first in setOf("provider_auth_start", "provider_auth_oauth_prepare", "provider_account_refresh") })
    }

    private fun installFake(accountOverride: JSONObject? = null): NativeViewModel {
        lateinit var model: NativeViewModel
        compose.runOnIdle {
            val api = object : NativeCoreApi {
                override suspend fun invoke(method: String, params: JSONObject, host: Boolean): Any? {
                    calls += method to JSONObject(params.toString())
                    return when (method) {
                        "provider_account_snapshot" -> accountOverride ?: JSONObject("""{"state":"authenticated","revision":1,"profile":{"nickname":"Fixture user","maskedIdentity":"test***"},"capabilities":{"playlistRead":true,"playlistWrite":true,"favoriteWrite":true},"entitlement":{"tier":"super-vip","membership":"active","expiresAtMs":null,"secondaryEntitlements":["trial","ad-free"]}}""")
                        "provider_home" -> JSONObject("""{"madeForYou":[{"id":"browse","title":"Browse playlist","tracks":[]}]}""")
                        "provider_discover" -> JSONObject("""{"featured":[{"id":"f","title":"Feature"}],"charts":[{"id":"c","title":"Chart"}],"newSongs":{"tracks":[$song]},"newAlbums":[{"id":"album","title":"Album name"}],"categories":[{"encArea":"area","title":"Area"}],"newMvs":[{"id":"mv","title":"Display MV","artist":"Artist name"}],"podcasts":[{"id":"pod","title":"Display podcast"}]}""")
                        "provider_search" -> JSONObject().put("items", org.json.JSONArray().put(when (params.text("kind")) { "song" -> song; "artist" -> JSONObject("""{"id":"artist","name":"Artist result"}"""); else -> JSONObject("""{"id":"result","title":"Collection result"}""") })).put("hasMore", false)
                        "provider_artist" -> JSONObject("""{"id":"artist","name":"Artist name","topSongs":[$song],"albums":[]}""")
                        "provider_add_playlist_track" -> JSONObject("""{"status":"rejected","authRevision":1}""")
                        "provider_artist_catalog" -> JSONObject("""{"items":[],"hasMore":false}""")
                        "provider_account_playlist_tracks" -> JSONObject().put("summary", summary).put("tracks", JSONObject().put("items", org.json.JSONArray().put(song)).put("authRevision", 1))
                        "provider_account_playlists" -> JSONObject("""{"items":[{"id":"read","title":"Read-only playlist","ownership":"owned","capabilities":{"canAddTracks":false}},{"id":"write","title":"Writable playlist","ownership":"owned","capabilities":{"canAddTracks":true}}],"authRevision":1}""")
                        else -> JSONObject()
                    }
                }
            }
            model = NativeViewModel(compose.activity.application as Application, api)
            fakeModel = model
            compose.activity.setContent { NativeApp(model, login = {}, share = { _, _ -> }, reportDrawn = {}) }
            model.action { model.refreshAccount() }
        }
        compose.waitUntil(5000) { model.page.value.loaded && model.account.value.state == (accountOverride?.getString("state") ?: "authenticated") }
        return model
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val song = JSONObject("""{"id":"qqmusic:track:1","title":"Song title","artists":[{"id":"artist","name":"Artist name"}],"album":{"id":"album","title":"Album name"},"durationMs":120000,"provider":{"providerId":"qqmusic","trackId":"1"}}""")
    private val summary = JSONObject("""{"id":"p","title":"Read-only collection","ownership":"collected","capabilities":{"canAddTracks":false,"canRename":false},"owner":{"displayName":"Owner"}}""")
}
