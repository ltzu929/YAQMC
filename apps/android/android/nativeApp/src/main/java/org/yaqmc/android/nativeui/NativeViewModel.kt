package org.yaqmc.android.nativeui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.yaqmc.android.BuildConfig
import org.yaqmc.android.update.AndroidUpdateChecker
import org.yaqmc.android.update.AndroidUpdateResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yaqmc.android.core.CoreManager
import org.yaqmc.android.core.MobileLoginOwner
import org.yaqmc.android.core.MobileLoginPolicy
import org.yaqmc.android.R

internal data class NativeRoute(val kind: String = "home", val id: String = "", val query: String = "", val searchKind: String = "song", val playlist: String = "", val tab: String = "top")
internal data class PageState(val page: CatalogPage = CatalogPage(titleFor("home")), val loading: Boolean = false, val error: NativeText? = null, val loadingMore: Boolean = false, val moreError: NativeText? = null, val loaded: Boolean = false)

@UnstableApi
internal class NativeViewModel(application: Application, val core: NativeCoreApi,
    private val checkUpdate: () -> AndroidUpdateResult = {
        AndroidUpdateChecker(BuildConfig.VERSION_NAME.substringBefore("-native-")).check()
    },
) : AndroidViewModel(application), CoreManager.Callback {
    constructor(application: Application) : this(application, NativeCore(application))
    private val _route = MutableStateFlow(NativeRoute())
    val route = _route.asStateFlow()
    private val _page = MutableStateFlow(PageState())
    val page = _page.asStateFlow()
    private val _player = MutableStateFlow(PlayerState())
    val player = _player.asStateFlow()
    private val _lyrics = MutableStateFlow(Lyrics())
    val lyrics = _lyrics.asStateFlow()
    private val _account = MutableStateFlow(AccountUi())
    val account = _account.asStateFlow()
    private val _message = MutableStateFlow<NativeText?>(null)
    val message = _message.asStateFlow()
    private val _quality = MutableStateFlow("automatic")
    val quality = _quality.asStateFlow()
    private val _qualityBusy = MutableStateFlow(false)
    val qualityBusy = _qualityBusy.asStateFlow()
    private val _queueBusy = MutableStateFlow(false)
    val queueBusy = _queueBusy.asStateFlow()
    private var continuationNotificationRevision = 0L
    private val _statistics = MutableStateFlow(NativeStatisticsState())
    val statistics = _statistics.asStateFlow()
    private val _diagnostics = MutableStateFlow(NativeDiagnosticsState())
    val diagnostics = _diagnostics.asStateFlow()
    private val _updateState = MutableStateFlow(NativeUpdateState())
    val updateState = _updateState.asStateFlow()
    private val _issueState = MutableStateFlow(NativeIssueState())
    val issueState = _issueState.asStateFlow()
    private var statisticsJob: Job? = null
    private var statisticsRefreshJob: Job? = null
    private var lastStatisticsRevision = 0L
    private val _cache = MutableStateFlow<NativeCacheStats?>(null)
    val cache = _cache.asStateFlow()
    private val _cacheBusy = MutableStateFlow(false)
    val cacheBusy = _cacheBusy.asStateFlow()
    private val _cacheError = MutableStateFlow(false)
    val cacheError = _cacheError.asStateFlow()
    private val preferenceStore = NativePreferenceStore(application.getSharedPreferences("native-ui", 0))
    val preferences = preferenceStore.state
    private val backStack = mutableListOf<NativeRoute>()
    private var pageJob: Job? = null
    private var pageNumber = 1
    private val pageCache = linkedMapOf<NativeRoute, Pair<CatalogPage, Int>>()
    private val catalog = NativeCatalogRepository { method, params -> core.objectResult(method, params) }
    private val _discoverTab = MutableStateFlow("featured")
    val discoverTab = _discoverTab.asStateFlow()
    private val _favorites = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val favorites = _favorites.asStateFlow()
    private val _pendingFavorites = MutableStateFlow<Set<String>>(emptySet())
    val pendingFavorites = _pendingFavorites.asStateFlow()
    private val _playlistSummaries = MutableStateFlow<Map<String, JSONObject>>(emptyMap())
    val playlistSummaries = _playlistSummaries.asStateFlow()
    private val _pendingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val pendingPlaylists = _pendingPlaylists.asStateFlow()
    private val _playlistErrors = MutableStateFlow<Map<String, NativeText>>(emptyMap())
    val playlistErrors = _playlistErrors.asStateFlow()
    private val _picker = MutableStateFlow<PlaylistPickerState?>(null)
    val picker = _picker.asStateFlow()
    private var pickerJob: Job? = null
    private val _accountBusy = MutableStateFlow(false)
    val accountBusy = _accountBusy.asStateFlow()
    private val snapshots = Channel<String>(Channel.CONFLATED)
    private var observing = false
    private var oauthAttempt: String? = null
    private var accountJob: Job? = null
    private var revalidationJob: Job? = null
    private var favoriteAuthorityJob: Job? = null
    private var favoriteAuthorityRevision: Long? = null
    private var favoriteMutationSerial = 0L
    private val favoriteMutationVersions = mutableMapOf<String, Long>()
    private var lastAccountRefreshAt = -60_000L
    private var lastQueueJson = ""
    private var lastQueue = emptyList<Track>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (json in snapshots) runCatching { acceptPlayer(JSONObject(json)) }
        }
        viewModelScope.launch {
            player.map { it.current?.let { track -> track.providerId to track.id } }.distinctUntilChanged().collectLatest { key ->
                _lyrics.value = Lyrics()
                if (key != null) try {
                    val document = core.invoke("provider_lyrics", provider(key.first).put("id", key.second)) as? JSONObject
                    if (document != null) _lyrics.value = withContext(Dispatchers.Default) { NativeModels.lyrics(document) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Lyrics are optional; keep playback usable on a lyrics failure. */ }
            }
        }
        load()
    }

    fun observe() {
        if (observing) return
        observing = true
        CoreManager.addCallback(this)
        // Events can be missed or their pending refresh cancelled while stopped.
        if (route.value.kind == "statistics") loadStatistics(quiet = true)
        action {
            snapshots.trySend(core.objectResult("player_snapshot").toString())
            refreshAccount()
            startAccountRevalidation()
            if (route.value.kind in setOf("favorites", "recent", "account-playlists", "account-playlist")) load(refresh = true)
            _quality.value = core.objectResult("provider_status", provider()).text("preferredQuality", "automatic")
        }
        action { acceptContinuation(core.objectResult("continuation_snapshot")) }
    }

    fun stopObserving() {
        observing = false
        CoreManager.removeCallback(this)
        revalidationJob?.cancel()
        statisticsRefreshJob?.cancel()
    }

    override fun onCleared() {
        stopObserving()
        snapshots.close()
        super.onCleared()
    }

    override fun onCoreResponse(id: Long, json: String) = Unit
    override fun onCoreEvent(sequence: Long, channel: String, json: String) {
        when (channel) {
            "player://snapshot" -> snapshots.trySend(json)
            "api://event" -> viewModelScope.launch {
                runCatching { JSONObject(json) }.getOrNull()?.let { event ->
                    when (event.text("type")) {
                        "continuation.changed" -> event.optJSONObject("data")?.let(::acceptContinuation)
                        "statistics.changed" -> onStatisticsChanged(event.optJSONObject("data")?.optLong("revision") ?: 0L)
                    }
                }
            }
            "account://changed" -> viewModelScope.launch {
                accountJob?.cancel()
                accountJob = action {
                    val before = account.value.revision to account.value.state
                    refreshAccount()
                    if (before != (account.value.revision to account.value.state)) {
                        pageCache.clear()
                        if (route.value.kind in setOf("home", "favorites", "recent", "account-playlists", "account-playlist")) load(refresh = true)
                    }
                }
            }
        }
    }

    private fun onStatisticsChanged(revision: Long) {
        if (revision <= lastStatisticsRevision) return
        lastStatisticsRevision = revision
        if (_route.value.kind != "statistics" || _statistics.value.clearing) return
        statisticsRefreshJob?.cancel()
        statisticsRefreshJob = viewModelScope.launch {
            // Repeated playback checkpoints should not trigger a Core query per event.
            delay(250)
            if (_route.value.kind == "statistics" && !_statistics.value.clearing) loadStatistics(quiet = true)
        }
    }

    private fun acceptPlayer(value: JSONObject) {
        val revision = value.optLong("snapshotRevision", -1)
        val previous = _player.value
        if (revision >= 0 && previous.revision > revision) return
        if (revision == previous.revision && value.optLong("sampledAtMs") > 0 && value.optLong("sampledAtMs") < previous.sampledAt) return
        val rawQueue = value.optJSONArray("queue")?.toString().orEmpty()
        if (rawQueue != lastQueueJson) {
            lastQueue = NativeModels.tracks(value.optJSONArray("queue"))
            lastQueueJson = rawQueue
        }
        _player.value = NativePlayback.decode(value, lastQueue)
    }

    private fun acceptContinuation(value: JSONObject) {
        val revision = value.optLong("notificationRevision")
        if (revision <= continuationNotificationRevision) return
        continuationNotificationRevision = revision
        if (value.text("terminalReason") == "provider-error") notify(R.string.native_continuation_stopped)
    }

    fun navigate(next: NativeRoute, root: Boolean = false) {
        if (next == _route.value && !root) return
        if (root) backStack.clear() else backStack += _route.value
        _route.value = next
        if (next.kind != "statistics") statisticsRefreshJob?.cancel()
        pageNumber = 1
        load()
    }

    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        _route.value = previous
        pageNumber = 1
        load()
        return true
    }

    fun open(item: CatalogItem) = navigate(NativeRoute(item.kind, item.id,
        playlist = if (item.kind == "account-playlist" ||
            (item.kind == "playlist" && item.json.optJSONArray("tracks").objects().isNotEmpty())) item.json.toString() else ""))

    fun search(query: String, kind: String = _route.value.searchKind) {
        val next = NativeRoute("search", query = query.trim(), searchKind = kind)
        if (next != _route.value) navigate(next, root = true)
    }

    fun artistTab(tab: String) {
        if (_route.value.kind != "artist" || tab !in setOf("top", "song", "album") || tab == _route.value.tab) return
        _route.value = _route.value.copy(tab = tab)
        load()
    }

    fun selectDiscoverTab(tab: String) { _discoverTab.value = tab }

    fun load(refresh: Boolean = false, more: Boolean = false) {
        if (more && (_page.value.loading || _page.value.loadingMore || !_page.value.page.hasMore)) return
        pageJob?.cancel()
        val target = _route.value
        if (target.kind in setOf("library", "settings", "lyric-settings", "lyric-presets", "appearance-settings", "player", "queue", "statistics", "diagnostics", "about", "issue-report")) return
        if (!refresh && !more) pageCache[target]?.let { cached ->
            pageNumber = cached.second
            _page.value = PageState(page = cached.first, loaded = true)
            return
        }
        val old = _page.value
        val oldPage = old.page
        if (!more) pageNumber = 1
        _page.value = when {
            more -> old.copy(loadingMore = true, moreError = null)
            refresh && old.loaded -> old.copy(loading = true, error = null, loadingMore = false, moreError = null)
            else -> PageState(CatalogPage(titleFor(target.kind)), loading = true)
        }
        pageJob = viewModelScope.launch {
            try {
                val accountPage = target.kind in setOf("favorites", "recent", "account-playlists", "account-playlist")
                if (accountPage) {
                    val beforeRevision = account.value.revision
                    refreshAccount()
                    if (more && beforeRevision != account.value.revision) { load(refresh = true); return@launch }
                    if (!account.value.authenticated) {
                        _page.value = PageState(CatalogPage(titleFor(target.kind)), loaded = true)
                        return@launch
                    }
                    if (!account.value.canRead(target.kind)) throw CoreFailure("unsupported-operation", "")
                }
                val revision = account.value.revision
                val favoriteVersion = favoriteMutationSerial
                val number = if (more) pageNumber + 1 else 1
                val result = catalog.load(target, refresh, number, if (more) oldPage.cursor else null)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (_route.value != target) return@launch
                if (accountPage && revision != account.value.revision) {
                    load(refresh = true)
                    return@launch
                }
                val accountEnvelope = if (target.kind == "account-playlist") result.raw.optJSONObject("tracks") else result.raw
                if (accountPage && accountEnvelope != null && accountEnvelope.has("authRevision") && accountEnvelope.optLong("authRevision") != revision) {
                    throw CoreFailure("authentication-expired", "")
                }
                // Stale account cursors cannot be safely continued.
                val safe = if (result.stale) result.copy(hasMore = false, cursor = null) else result
                val merged = if (more) NativeModels.merge(oldPage, safe) else safe
                _page.value = PageState(merged, loaded = true)
                pageNumber = number
                pageCache[target] = merged to number
                if (pageCache.size > 12) pageCache.remove(pageCache.keys.first())
                if (target.kind == "favorites") {
                    val seen = merged.tracks.map { it.key }.toSet()
                    fun preserveFavorite(key: String) = key in _pendingFavorites.value || (favoriteMutationVersions[key] ?: 0) > favoriteVersion
                    _favorites.value = (if (merged.hasMore) _favorites.value else _favorites.value.mapValues { (key, value) -> if (preserveFavorite(key)) value else key in seen }) +
                        result.tracks.filter { !preserveFavorite(it.key) }.associate { it.key to true }
                }
                if (target.kind == "account-playlists") projectPlaylists(result.sections.flatMap { it.cards })
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (_route.value == target) _page.value = if (more) _page.value.copy(loadingMore = false, moreError = errorText(error, R.string.native_load_failed))
                    else _page.value.copy(loading = false, error = errorText(error, R.string.native_load_failed))
            }
        }
    }

    fun command(method: String, params: JSONObject = JSONObject()) = action {
        val result = core.invoke(method, params)
        if (result is JSONObject && result.has("queue")) snapshots.trySend(result.toString())
        if (method == "continuation_start") {
            if (result is JSONObject) acceptContinuation(result)
            snapshots.trySend(core.objectResult("player_snapshot").toString())
        }
    }

    fun playQueueEntry(id: String) {
        val entry = player.value.entries.find { it.id == id } ?: return
        if (entry.track.providerId != "qqmusic") { notify(R.string.native_unsupported_provider); return }
        if (player.value.authoritative) command("player_play_queue_entry", JSONObject().put("entryId", id))
        else command("player_play_from_queue", JSONObject().put("index", entry.index))
    }

    fun playNextQueueEntry(id: String) = editQueue("player_play_next_queue_entry", id)
    fun removeQueueEntry(id: String) = editQueue("player_remove_queue_entry", id)
    fun moveQueueEntry(id: String, targetId: String) {
        if (id != targetId) editQueue("player_reorder_queue_entry", id, targetId)
    }

    private fun editQueue(method: String, id: String, targetId: String? = null) {
        val state = player.value
        if (_queueBusy.value || !state.authoritative || id !in state.entryIds) return
        val params = JSONObject().put("entryId", id)
        if (targetId != null) {
            val target = state.entryIds.indexOf(targetId)
            if (target < 0) return
            params.put("targetIndex", target)
        }
        if (method == "player_play_next_queue_entry" && (state.currentEntry?.id == id || state.current == null)) return
        _queueBusy.value = true
        action {
            try { snapshots.trySend(core.objectResult(method, params).toString()) }
            finally { _queueBusy.value = false }
        }
    }

    fun play(tracks: List<Track>, start: Track? = tracks.firstOrNull(), shuffle: Boolean? = null, continuation: String? = null) {
        if (tracks.isEmpty()) return
        val request = JSONObject().put("tracks", JSONArray(tracks.map { it.json })).put("startAtId", start?.id)
        if (continuation != null) {
            request.put("providerId", "qqmusic").put("kind", continuation)
            if (continuation == "radar" && start != null) request.put("seedTrackIds", JSONArray(listOf(start.id)))
            command("continuation_start", JSONObject().put("request", request))
        } else {
            shuffle?.let { request.put("shuffle", it) }
            command("player_play_tracks", JSONObject().put("request", request))
        }
    }

    fun playCard(item: CatalogItem) = action {
        val page = when {
            item.json.optJSONArray("tracks") != null -> NativeModels.detail(item.kind, item.json)
            item.kind == "account-playlist" -> catalog.load(NativeRoute(item.kind, item.id, playlist = item.json.toString()), false, 1, null)
            else -> catalog.load(NativeRoute(item.kind, item.id), false, 1, null)
        }
        if (page.tracks.isEmpty()) notify(R.string.native_empty)
        else play(page.tracks, continuation = item.continuation)
    }

    fun playTrack(tracks: List<Track>, track: Track, continuation: String? = null) {
        val current = player.value.current
        if (current?.key == track.key) command("player_toggle")
        else play(if (continuation != null) tracks.drop(tracks.indexOf(track).coerceAtLeast(0)) else tracks, track, continuation = continuation)
    }

    fun addTracks(tracks: List<Track>) {
        if (tracks.isNotEmpty()) command("player_add_tracks_to_queue", JSONObject().put("tracks", JSONArray(tracks.map { it.json })))
    }

    fun favorite(track: Track) {
        if (!account.value.authenticated) { navigate(NativeRoute("settings")); return }
        if (!account.value.canFavorite || !track.writable || track.key in _pendingFavorites.value) return
        val desired = !(_favorites.value[track.key] ?: track.favorite)
        val revision = account.value.revision
        favoriteMutationVersions[track.key] = ++favoriteMutationSerial
        _pendingFavorites.value += track.key
        action {
            try {
                val result = core.objectResult("provider_set_favorite", provider(track.providerId).put("request", JSONObject()
                    .put("trackId", track.id).put("favorite", desired).put("clientOperationId", UUID.randomUUID().toString())))
                if (revision != account.value.revision || result.optLong("authRevision", -1) != revision) return@action
                if (result.text("status") !in setOf("applied", "reconciled")) {
                    pageCache.clear()
                    if (route.value.kind == "favorites") load(refresh = true)
                    throw CoreFailure(if (result.text("status") == "outcome-unknown") "native.mutation.unknown" else "native.mutation.rejected", "")
                }
                _favorites.value = _favorites.value + (track.key to result.optBoolean("favorite"))
                pageCache.clear()
                _message.value = NativeText(if (result.text("status") == "reconciled") R.string.native_mutation_reconciled
                    else if (result.optBoolean("favorite")) R.string.native_favorited else R.string.native_unfavorited)
                if (route.value.kind == "favorites") load(refresh = true)
            } finally { _pendingFavorites.value -= track.key }
        }
    }

    private fun projectPlaylists(items: List<CatalogItem>) {
        _playlistSummaries.value = _playlistSummaries.value + items.associate { it.id to it.json }
    }

    fun openPicker(track: Track) {
        if (!account.value.authenticated) { navigate(NativeRoute("settings")); return }
        if (!account.value.canWrite(track)) return
        pickerJob?.cancel()
        _playlistErrors.value = emptyMap()
        _picker.value = PlaylistPickerState(track)
        loadPicker(reset = true)
    }

    fun closePicker() { pickerJob?.cancel(); _picker.value = null }

    fun loadPicker(reset: Boolean = false) {
        val before = _picker.value ?: return
        if (!reset && (before.loading || before.cursor == null)) return
        pickerJob?.cancel()
        _picker.value = before.copy(loading = true, error = null)
        val revision = account.value.revision
        pickerJob = viewModelScope.launch {
            try {
                val result = catalog.load(NativeRoute("account-playlists"), reset, 1, if (reset) null else before.cursor)
                if (_picker.value?.track?.key != before.track.key || revision != account.value.revision) return@launch
                if (result.raw.has("authRevision") && result.raw.optLong("authRevision") != revision) throw CoreFailure("authentication-expired", "")
                val cards = result.sections.flatMap { it.cards }
                projectPlaylists(cards)
                _picker.value = before.copy(playlists = ((if (reset) emptyList() else before.playlists) + cards).distinctBy { it.id }
                    .filter { it.json.text("ownership") == "owned" && it.json.canEdit("canAddTracks") },
                    cursor = if (result.stale) null else result.cursor, loaded = true, loading = false, stale = result.stale)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _picker.value = before.copy(loading = false, error = errorText(error, R.string.native_load_failed)) }
        }
    }

    fun mutatePlaylist(operation: String, summary: JSONObject, track: Track? = null, title: String? = null, collected: Boolean? = null) {
        if (!account.value.authenticated) { navigate(NativeRoute("settings")); return }
        val id = summary.text("id")
        val projected = _playlistSummaries.value[id] ?: summary
        val capability = when (operation) { "rename" -> "canRename"; "delete" -> "canDelete"; "add" -> "canAddTracks"; "remove" -> "canRemoveTracks"; else -> null }
        if (id.isBlank() || id in _pendingPlaylists.value || !account.value.capabilities.optBoolean("playlistWrite")) return
        if (capability != null && !projected.canEdit(capability)) return
        if (operation == "rename" && (title == null || !validPlaylistTitle(title))) return
        if (operation in setOf("add", "remove") && (track == null || !account.value.canWrite(track))) return
        if (operation == "collect" && (!id.startsWith("qqmusic:playlist:") || projected.text("ownership") == "owned")) return
        val method = when (operation) {
            "rename" -> "provider_rename_playlist"; "delete" -> "provider_delete_playlist"
            "add" -> "provider_add_playlist_track"; "remove" -> "provider_remove_playlist_track"
            "collect" -> "provider_set_playlist_collected"; else -> return
        }
        val revision = account.value.revision
        _pendingPlaylists.value += id
        _playlistErrors.value -= id
        action {
            try {
                val request = JSONObject().put("playlistId", id).put("clientOperationId", UUID.randomUUID().toString())
                title?.let { request.put("title", it.trim()) }
                track?.let { request.put("trackId", it.id) }
                collected?.let { request.put("collected", it) }
                val result = core.objectResult(method, provider().put("request", request))
                if (revision != account.value.revision || result.optLong("authRevision", -1) != revision) return@action
                pageCache.clear()
                if (result.text("status") !in setOf("applied", "reconciled")) {
                    if (route.value.id == id) load(refresh = true)
                    throw CoreFailure(if (result.text("status") == "outcome-unknown") "native.mutation.unknown" else "native.mutation.rejected", "")
                }
                result.optJSONObject("playlist")?.let { _playlistSummaries.value = _playlistSummaries.value + (id to it) }
                if (operation == "collect") {
                    val updated = JSONObject((_playlistSummaries.value[id] ?: projected).toString())
                    updated.put("ownership", if (collected == true) "collected" else "")
                    _playlistSummaries.value = _playlistSummaries.value + (id to updated)
                }
                _message.value = NativeText(if (result.text("status") == "reconciled") R.string.native_mutation_reconciled else R.string.native_mutation_applied)
                if (operation == "add" && _picker.value?.track?.key == track?.key) closePicker()
                if (operation == "delete") {
                    _playlistSummaries.value -= id
                    if (route.value.id == id && !back()) navigate(NativeRoute("account-playlists"))
                } else if (route.value.id == id) {
                    result.optJSONObject("playlist")?.let { updated ->
                        if (route.value.kind == "account-playlist") _route.value = route.value.copy(playlist = updated.toString())
                    }
                    load(refresh = true)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (revision == account.value.revision) _playlistErrors.value += id to errorText(error, R.string.native_action_failed)
                throw error
            } finally { _pendingPlaylists.value -= id }
        }
    }

    fun changeQuality(value: String, current: Boolean = false) {
        if (_qualityBusy.value || value !in NativePlayback.qualities) return
        val state = player.value
        if (current && (state.current?.providerId != "qqmusic" || state.selection.capabilities.any { it.quality == value && it.unavailable })) return
        _qualityBusy.value = true
        action {
            try {
                val result = core.objectResult(if (current) "provider_set_current_quality" else "provider_set_preferred_quality", provider().put("quality", value))
                if (current) snapshots.trySend(result.toString()) else _quality.value = result.text("preferredQuality", value)
            } finally { _qualityBusy.value = false }
        }
    }

    fun setTheme(value: String) = preferenceStore.setTheme(value)
    fun setLanguage(value: String) = preferenceStore.setLanguage(value)
    fun setLyrics(value: NativeLyricPreferences) = preferenceStore.setLyrics(value)
    fun setAppearance(value: NativeAppearancePreferences) = preferenceStore.setAppearance(value)
    fun setLyricPresets(value: NativeLyricPresetPreferences) = preferenceStore.setLyricPresets(value)
    fun localizedString(resource: Int): String = getApplication<Application>().withNativeLanguage(preferences.value).getString(resource)
    fun dismissMessage() { _message.value = null }
    fun notify(resource: Int) { _message.value = NativeText(resource) }

    suspend fun refreshAccount() {
        val value = core.objectResult("provider_account_snapshot", provider())
        val next = AccountUi.parse(value)
        if (next.revision != _account.value.revision || next.authenticated != _account.value.authenticated) {
            favoriteAuthorityJob?.cancel()
            favoriteAuthorityRevision = null
            favoriteMutationVersions.clear()
            pageCache.clear()
            _favorites.value = emptyMap()
            _playlistSummaries.value = emptyMap()
            _playlistErrors.value = emptyMap()
            closePicker()
            if (route.value.kind in setOf("favorites", "recent", "account-playlists", "account-playlist")) {
                _page.value = PageState(CatalogPage(titleFor(route.value.kind)), loading = true)
            }
        }
        _account.value = next
        MobileLoginOwner.adopt(value, "qqmusic")
        hydrateFavorites()
    }

    private fun hydrateFavorites() {
        val snapshot = account.value
        if (!snapshot.authenticated || !snapshot.canRead("favorites") || favoriteAuthorityRevision == snapshot.revision || favoriteAuthorityJob?.isActive == true) return
        favoriteAuthorityJob = viewModelScope.launch {
            try {
                val version = favoriteMutationSerial
                val page = catalog.load(NativeRoute("favorites"), false, 1, null)
                if (account.value.revision != snapshot.revision || !account.value.authenticated || page.raw.optLong("authRevision", -1) != snapshot.revision) return@launch
                _favorites.value += page.tracks.filter { it.key !in _pendingFavorites.value && (favoriteMutationVersions[it.key] ?: 0) <= version }.associate { it.key to true }
                favoriteAuthorityRevision = snapshot.revision
                if (route.value.kind != "favorites" && favoriteMutationSerial == version) pageCache.putIfAbsent(NativeRoute("favorites"), page.copy(hasMore = page.hasMore && !page.stale) to 1)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Keep catalog playback available; the library still exposes retry. */ }
        }
    }

    private fun startAccountRevalidation() {
        revalidationJob?.cancel()
        revalidationJob = viewModelScope.launch {
            var restoreRetry = 0
            while (observing) {
                val snapshot = account.value
                val retry = snapshot.state == "secure-store-unavailable" || (snapshot.state == "network-error" && snapshot.attempt.isBlank())
                val now = android.os.SystemClock.elapsedRealtime()
                if (!_accountBusy.value && (snapshot.authenticated || retry) && now - lastAccountRefreshAt >= if (retry) 5_000 else 60_000) {
                    refreshAccountSession()?.join()
                }
                val stillRetry = account.value.state == "secure-store-unavailable" || (account.value.state == "network-error" && account.value.attempt.isBlank())
                if (stillRetry) delay(listOf(5_000L, 15_000L, 30_000L, 60_000L)[restoreRetry.coerceAtMost(3)].also { restoreRetry++ })
                else { restoreRetry = 0; delay(5 * 60_000L) }
            }
        }
    }

    fun startLogin(method: String, smallestWidth: Int, oauth: (JSONObject) -> Unit, mobile: (String) -> Unit) = accountAction {
        if (MobileLoginPolicy.shouldUseQqMusicApp(smallestWidth, "qqmusic", method)) {
            var snapshot = core.objectResult("provider_account_snapshot", provider())
            if (snapshot.text("state") !in MobileLoginLeaseRegistryStates) snapshot = core.objectResult("provider_auth_start", provider().put("mobile", true), host = true)
            MobileLoginOwner.adopt(snapshot, "qqmusic")
            refreshAccount()
            MobileLoginPolicy.validateLaunchUrl(snapshot.text("launchUrl"))?.let { mobile(it.authorizationUrl) }
        } else {
            val prepared = core.objectResult("provider_auth_oauth_prepare", provider().put("methodId", method), host = true)
            oauthAttempt = prepared.text("attemptId").takeIf(String::isNotBlank)
            if (oauthAttempt == null) throw CoreFailure("native.login.invalid", "")
            refreshAccount()
            try { oauth(prepared) } catch (error: Exception) { finishOAuth(null); throw error }
        }
    }

    fun finishOAuth(callback: String?) = action {
        val attempt = oauthAttempt ?: return@action
        oauthAttempt = null
        val params = provider().put("attemptId", attempt)
        if (callback == null) core.invoke("provider_auth_oauth_cancel", params, host = true)
        else core.invoke("provider_auth_oauth_complete", params.put("callbackUrl", callback), host = true)
        refreshAccount()
        pageCache.clear()
        load(refresh = true)
    }

    fun cancelLogin() = accountAction {
        val attempt = _account.value.attempt
        if (attempt.isNotBlank()) core.invoke("provider_auth_cancel", provider().put("attemptId", attempt), host = true)
        refreshAccount()
    }

    fun refreshQr() = accountAction {
        val attempt = account.value.attempt
        if (attempt.isNotBlank()) core.invoke("provider_auth_refresh", provider().put("attemptId", attempt), host = true)
        refreshAccount()
    }

    fun signOut() = accountAction {
        core.invoke("provider_sign_out", provider()); refreshAccount(); pageCache.clear(); load(refresh = true)
    }

    fun previewIssue(category: String, summary: String, description: String) {
        if (category !in setOf("bug", "playback", "provider", "lyrics", "ui", "other") ||
            summary.isBlank() || _issueState.value.busy) return
        val draftKey = listOf(category, summary, description).joinToString("\u0000")
        _issueState.value = NativeIssueState(draftKey = draftKey, busy = true)
        action {
            try {
                val result = core.objectResult("issue_reporter_preview", JSONObject()
                    .put("draft", JSONObject().put("category", category)
                        .put("summary", summary).put("description", description))
                    .put("request", JSONObject()))
                _issueState.value = NativeIssueState(draftKey = draftKey, preview = NativeIssuePreview(
                    result.text("title"), result.text("body"), result.text("url"), result.optBoolean("tooLongForBrowser")))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _issueState.value = NativeIssueState(draftKey = draftKey, error = true) }
        }
    }

    fun openIssuePreview(context: Context) {
        val preview = _issueState.value.preview ?: return
        if (preview.tooLong || _issueState.value.busy) return
        _issueState.value = _issueState.value.copy(busy = true)
        action {
            try {
                core.invoke("issue_reporter_validate_url", JSONObject().put("url", preview.url))
                openProjectUrl(context, preview.url)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = errorText(error, R.string.native_issue_failed) }
            finally { _issueState.value = _issueState.value.copy(busy = false) }
        }
    }

    fun loadDiagnostics() {
        if (_diagnostics.value.busy || _diagnostics.value.loading) return
        _diagnostics.value = _diagnostics.value.copy(loading = true, error = false)
        action {
            try {
                val result = core.objectResult("diagnostics_snapshot", JSONObject().put("request", JSONObject()))
                val errors = result.optJSONArray("recentErrors").objects().takeLast(20).map {
                    listOf(it.text("domain"), it.text("code"), it.text("message")).filter(String::isNotBlank).joinToString(" · ")
                }
                _diagnostics.value = NativeDiagnosticsState(
                    logLevel = result.text("logLevel", "info"), recentErrors = errors,
                )
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _diagnostics.value = _diagnostics.value.copy(loading = false, error = true) }
        }
    }

    fun setDiagnosticsLevel(level: String) {
        if (level !in setOf("info", "debug", "trace") || _diagnostics.value.busy || _diagnostics.value.loading) return
        _diagnostics.value = _diagnostics.value.copy(busy = true)
        action {
            try {
                core.invoke("diagnostics_set_log_level", JSONObject().put("level", level))
                _diagnostics.value = _diagnostics.value.copy(busy = false)
                loadDiagnostics()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _diagnostics.value = _diagnostics.value.copy(busy = false)
                _message.value = errorText(error, R.string.native_action_failed)
            }
        }
    }

    fun clearDiagnosticLogs() {
        if (_diagnostics.value.busy || _diagnostics.value.loading) return
        _diagnostics.value = _diagnostics.value.copy(busy = true)
        action {
            try {
                val result = core.invoke("diagnostics_clear_logs")
                val deleted = (result as? Number)?.toLong()
                _message.value = if (deleted == null) NativeText(R.string.native_diag_logs_cleared)
                    else NativeText(R.string.native_diag_logs_cleared_count, deleted)
                _diagnostics.value = _diagnostics.value.copy(busy = false)
                loadDiagnostics()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _diagnostics.value = _diagnostics.value.copy(busy = false)
                _message.value = errorText(error, R.string.native_action_failed)
            }
        }
    }

    fun shareDiagnosticBundle(context: Context) {
        if (_diagnostics.value.busy || _diagnostics.value.loading) return
        _diagnostics.value = _diagnostics.value.copy(busy = true)
        action {
            try {
                val directory = File(getApplication<Application>().cacheDir, "diagnostics").apply { mkdirs() }
                val file = File(directory, "YAQMC-diagnostics-" + System.currentTimeMillis() + ".zip")
                val result = core.objectResult("diagnostics_export_bundle_to", JSONObject()
                    .put("path", file.absolutePath)
                    .put("request", JSONObject().put("includeLogs", true).put("overrideUnresolved", false)))
                val target = File(result.text("path")).canonicalFile
                if (target.parentFile != directory.canonicalFile || !target.isFile || target.length() == 0L) {
                    throw CoreFailure("core.protocol", "")
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".diagnostics", target)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, localizedString(R.string.native_diag_export))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = errorText(error, R.string.native_diag_export_failed) }
            finally { _diagnostics.value = _diagnostics.value.copy(busy = false) }
        }
    }

    fun checkForUpdates() {
        if (_updateState.value.loading) return
        _updateState.value = NativeUpdateState(loading = true)
        action {
            val result = withContext(Dispatchers.IO) { checkUpdate() }
            _updateState.value = when (result) {
                is AndroidUpdateResult.Available -> NativeUpdateState(
                    checked = true, availableVersion = result.release.versionName,
                    releaseUrl = result.release.releaseUrl, notes = result.release.notes)
                AndroidUpdateResult.NotAvailable -> NativeUpdateState(checked = true)
                is AndroidUpdateResult.Error -> NativeUpdateState(checked = true, error = true)
            }
        }
    }

    fun openProjectLink(context: Context, url: String) = action {
        try { openProjectUrl(context, url) }
        catch (_: Exception) { _message.value = NativeText(R.string.native_action_failed) }
    }

    fun loadStatistics(range: String = _statistics.value.range, quiet: Boolean = false) {
        if (range !in setOf("7-days", "30-days", "365-days", "all-time") || _statistics.value.clearing) return
        statisticsJob?.cancel()
        // Different ranges must not display stale numbers under the newly selected label.
        val previous = _statistics.value
        val snapshot = previous.snapshot.takeIf { previous.range == range }
        _statistics.value = NativeStatisticsState(range, snapshot, loading = !quiet || snapshot == null)
        statisticsJob = viewModelScope.launch {
            try {
                val result = core.objectResult("statistics_snapshot", JSONObject().put("range", range))
                _statistics.value = NativeStatisticsState(range, NativeStatisticsSnapshot.parse(result))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _statistics.value = _statistics.value.copy(loading = false, error = true)
            }
        }
    }

    fun clearStatistics() {
        if (_statistics.value.clearing) return
        statisticsRefreshJob?.cancel()
        statisticsJob?.cancel()
        _statistics.value = _statistics.value.copy(clearing = true, loading = false)
        viewModelScope.launch {
            try {
                val result = core.objectResult("statistics_clear")
                lastStatisticsRevision = maxOf(lastStatisticsRevision, result.optLong("revision"))
                _statistics.value = _statistics.value.copy(clearing = false, snapshot = null)
                _message.value = NativeText(R.string.native_stats_cleared_count, result.optLong("deletedSessions"))
                loadStatistics()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _statistics.value = _statistics.value.copy(clearing = false, error = true)
                _message.value = errorText(error, R.string.native_action_failed)
            }
        }
    }

    fun cacheStats(clear: Boolean = false) {
        if (_cacheBusy.value) return
        _cacheBusy.value = true
        _cacheError.value = false
        viewModelScope.launch {
            try {
                if (clear) core.invoke("provider_clear_cache", provider())
                _cache.value = NativeCacheStats.parse(core.objectResult("provider_cache_stats", provider()))
                if (clear) _message.value = NativeText(R.string.native_cache_cleared)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _cacheError.value = true
                _message.value = errorText(error, R.string.native_action_failed)
            }
            finally { _cacheBusy.value = false }
        }
    }

    fun refreshAccountSession() = accountAction {
        lastAccountRefreshAt = android.os.SystemClock.elapsedRealtime()
        core.invoke("provider_account_refresh", provider())
        refreshAccount()
        pageCache.clear()
        if (route.value.kind !in setOf("settings", "library")) load(refresh = true)
    }

    private fun accountAction(block: suspend () -> Unit): Job? {
        if (_accountBusy.value) return null
        _accountBusy.value = true
        return action { try { block() } finally { _accountBusy.value = false } }
    }

    fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { _message.value = errorText(error, R.string.native_action_failed) }
    }

    private fun errorText(error: Exception, fallback: Int): NativeText {
        val resource = when ((error as? CoreFailure)?.code) {
            "favorite.failed" -> R.string.native_favorite_unconfirmed
            "timeout" -> R.string.native_timeout
            "unsupported-operation" -> R.string.native_not_supported
            "authentication-expired" -> R.string.native_session_expired
            "native.mutation.unknown" -> R.string.native_mutation_unknown
            "native.mutation.rejected" -> R.string.native_mutation_rejected
            "native.login.invalid" -> R.string.native_login_invalid
            "native.login.config" -> R.string.native_login_config_invalid
            "native.create.unconfirmed" -> R.string.native_create_unconfirmed
            "core.protocol" -> R.string.native_request_failed
            "audio.focus" -> R.string.native_audio_focus
            "native.share.invalid" -> R.string.native_share_unavailable
            "native.share.public" -> R.string.native_share_public_unavailable
            else -> null
        }
        return if (resource != null) NativeText(resource) else error.message?.takeIf(String::isNotBlank)?.let(::NativeText) ?: NativeText(fallback)
    }

    companion object {
        private val MobileLoginLeaseRegistryStates = setOf("starting-login", "waiting-for-scan", "waiting-for-confirmation")
        fun provider(id: String = "qqmusic") = JSONObject().put("providerId", id)
    }
}
