package org.yaqmc.android.nativeui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yaqmc.android.core.CoreManager
import org.yaqmc.android.core.MobileLoginOwner
import org.yaqmc.android.core.MobileLoginPolicy

internal data class NativeRoute(val kind: String = "home", val id: String = "", val query: String = "", val searchKind: String = "song", val playlist: String = "")
internal data class PageState(val page: CatalogPage = CatalogPage("首页"), val loading: Boolean = false, val error: String? = null)
internal data class AccountUi(val state: String = "guest", val nickname: String = "访客", val avatar: String = "", val qr: String = "", val attempt: String = "", val canFavorite: Boolean = false)

@UnstableApi
internal class NativeViewModel(application: Application) : AndroidViewModel(application), CoreManager.Callback {
    val core = NativeCore(application)
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
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _quality = MutableStateFlow("automatic")
    val quality = _quality.asStateFlow()
    private val _cache = MutableStateFlow("")
    val cache = _cache.asStateFlow()
    private val preferences = application.getSharedPreferences("native-ui", 0)
    private val _theme = MutableStateFlow(preferences.getString("theme", "system") ?: "system")
    val theme = _theme.asStateFlow()
    private val backStack = mutableListOf<NativeRoute>()
    private var pageJob: Job? = null
    private var pageNumber = 1
    private val pageCache = linkedMapOf<NativeRoute, CatalogPage>()
    private val snapshots = Channel<String>(Channel.CONFLATED)
    private var observing = false
    private var oauthAttempt: String? = null
    private var accountJob: Job? = null
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
        action {
            snapshots.trySend(core.objectResult("player_snapshot").toString())
            refreshAccount()
            _quality.value = core.objectResult("provider_status", provider()).text("preferredQuality", "automatic")
        }
    }

    fun stopObserving() {
        observing = false
        CoreManager.removeCallback(this)
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
            "account://changed" -> viewModelScope.launch {
                accountJob?.cancel()
                accountJob = action { refreshAccount(); pageCache.clear() }
            }
        }
    }

    private fun acceptPlayer(value: JSONObject) {
        val rawQueue = value.optJSONArray("queue")?.toString().orEmpty()
        if (rawQueue != lastQueueJson) {
            lastQueue = NativeModels.tracks(value.optJSONArray("queue"))
            lastQueueJson = rawQueue
        }
        val index = if (value.isNull("currentIndex")) -1 else value.optInt("currentIndex", -1)
        val state = PlayerState(lastQueue, value.optJSONArray("queueEntries").objects().map { it.text("id") },
            index, value.optLong("positionMs"), if (value.isNull("playbackDurationMs")) lastQueue.getOrNull(index)?.duration ?: 0 else value.optLong("playbackDurationMs"),
            value.optBoolean("isPlaying"), value.text("playbackState", "idle"), value.text("primaryPlaybackMode", "sequential"),
            value.optJSONObject("sourceSelection")?.text("resolvedQuality").orEmpty(), value.optJSONObject("playbackError")?.text("message"))
        _player.value = state
    }

    fun navigate(next: NativeRoute, root: Boolean = false) {
        if (next == _route.value && !root) return
        if (root) backStack.clear() else backStack += _route.value
        _route.value = next
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

    fun load(refresh: Boolean = false, more: Boolean = false) {
        pageJob?.cancel()
        val target = _route.value
        if (target.kind in setOf("library", "settings", "player", "queue")) return
        if (target.kind == "search" && target.query.isBlank()) {
            _page.value = PageState(CatalogPage("搜索", "搜索歌曲、歌手、专辑和歌单")); return
        }
        if (!refresh && !more) pageCache[target]?.let { _page.value = PageState(it); return }
        val oldPage = _page.value.page
        _page.value = if (more || refresh) _page.value.copy(loading = true, error = null)
            else PageState(CatalogPage(titleFor(target.kind)), loading = true)
        pageJob = viewModelScope.launch {
            try {
                val number = if (more) pageNumber + 1 else 1
                val cursor = if (more) oldPage.cursor else null
                val result = when (target.kind) {
                    "home" -> NativeModels.home(core.objectResult("provider_home", provider().put("refresh", refresh)))
                    "discover" -> NativeModels.discover(core.objectResult("provider_discover", provider().put("refresh", refresh)))
                    "search" -> {
                        val json = core.objectResult("provider_search", provider().put("query", target.query).put("kind", target.searchKind).put("page", number).put("limit", 30))
                        val section = if (target.searchKind == "song") FeedSection("歌曲", tracks = NativeModels.tracks(json.optJSONArray("items")))
                            else FeedSection(titleFor(target.searchKind), NativeModels.cards(json.optJSONArray("items"), target.searchKind))
                        CatalogPage("搜索", target.query, sections = listOf(section), hasMore = json.optBoolean("hasMore"))
                    }
                    "favorites", "recent", "account-playlists" -> {
                        val method = when (target.kind) { "favorites" -> "provider_favorite_songs"; "recent" -> "provider_account_recently_played"; else -> "provider_account_playlists" }
                        val json = core.objectResult(method, provider().put("cursor", cursor ?: JSONObject.NULL).put("limit", 50))
                        val section = when (target.kind) {
                            "account-playlists" -> FeedSection("歌单", NativeModels.cards(json.optJSONArray("items"), "account-playlist"))
                            "recent" -> FeedSection("歌曲", tracks = json.optJSONArray("items").objects().mapNotNull { it.optJSONObject("song")?.let(::Track) })
                            else -> FeedSection("歌曲", tracks = NativeModels.tracks(json.optJSONArray("items")))
                        }
                        CatalogPage(titleFor(target.kind), sections = listOf(section), hasMore = json.text("nextCursor").isNotEmpty(), cursor = json.text("nextCursor").takeIf(String::isNotEmpty))
                    }
                    "account-playlist" -> NativeModels.detail(target.kind, core.objectResult("provider_account_playlist_tracks",
                        provider().put("playlist", JSONObject(target.playlist)).put("cursor", cursor ?: JSONObject.NULL).put("limit", 50)))
                    "playlist" -> NativeModels.detail(target.kind, if (target.playlist.isNotEmpty()) JSONObject(target.playlist)
                        else core.objectResult("provider_playlist", provider().put("id", target.id)))
                    "statistics" -> {
                        val json = core.objectResult("statistics_snapshot", JSONObject().put("range", "30-days"))
                        CatalogPage("收听统计", "最近 30 天 · ${json.optLong("qualifiedListeningMs") / 60000} 分钟 · ${json.optInt("qualifiedPlayCount")} 次播放",
                            sections = listOf(FeedSection("最常听的歌曲", json.optJSONArray("topSongs").objects().map { row ->
                                CatalogItem("statistic", JSONObject().put("id", row.text("id")).put("title", row.text("title"))
                                    .put("creator", "${row.text("subtitle")} · ${row.optLong("listenedMs") / 60000} 分钟"))
                            })))
                    }
                    "song" -> {
                        val track = Track(core.objectResult("provider_song", provider().put("id", target.id)))
                        CatalogPage(track.title, track.artists, track.artwork, listOf(FeedSection("歌曲", tracks = listOf(track))))
                    }
                    else -> NativeModels.detail(target.kind, core.objectResult("provider_${target.kind}", provider().put(if (target.kind == "area") "encArea" else "id", target.id)))
                }
                val merged = if (more) result.copy(sections = oldPage.sections.zip(result.sections) { a, b ->
                    a.copy(cards = a.cards + b.cards, tracks = a.tracks + b.tracks)
                }) else result
                _page.value = PageState(merged)
                pageNumber = number
                pageCache[target] = merged
                if (pageCache.size > 12) pageCache.remove(pageCache.keys.first())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _page.value = _page.value.copy(loading = false, error = error.message ?: "加载失败，请重试") }
        }
    }

    fun command(method: String, params: JSONObject = JSONObject()) = action {
        val result = core.invoke(method, params)
        if (result is JSONObject && result.has("queue")) snapshots.trySend(result.toString())
    }

    fun play(tracks: List<Track>, start: Track? = tracks.firstOrNull()) {
        if (tracks.isEmpty()) return
        command("player_play_tracks", JSONObject().put("request", JSONObject().put("tracks", JSONArray(tracks.map { it.json })).put("startAtId", start?.id)))
    }

    fun favorite(track: Track) = action {
        val result = core.objectResult("provider_set_favorite", provider(track.providerId).put("request", JSONObject()
            .put("trackId", track.id).put("favorite", !track.favorite).put("clientOperationId", UUID.randomUUID().toString())))
        if (result.text("status") !in setOf("applied", "reconciled")) throw CoreFailure("favorite.failed", "收藏未确认成功，请刷新后检查")
        pageCache.clear()
        _message.value = if (result.optBoolean("favorite")) "已收藏" else "已取消收藏"
        load(refresh = true)
    }

    fun changeQuality(value: String, current: Boolean = false) = action {
        val result = core.objectResult(if (current) "provider_set_current_quality" else "provider_set_preferred_quality", provider().put("quality", value))
        if (current) snapshots.trySend(result.toString()) else _quality.value = result.text("preferredQuality", value)
    }

    fun setTheme(value: String) { _theme.value = value; preferences.edit().putString("theme", value).apply() }
    fun dismissMessage() { _message.value = null }
    fun notify(message: String) { _message.value = message }

    suspend fun refreshAccount() {
        val value = core.objectResult("provider_account_snapshot", provider())
        val profile = value.optJSONObject("profile")
        _account.value = AccountUi(value.text("state", "guest"), profile?.text("nickname", "访客") ?: "访客",
            profile?.text("avatarUrl").orEmpty(), value.text("qrImageDataUri"), value.text("attemptId"), value.optJSONObject("capabilities")?.optBoolean("favoriteWrite") == true)
        MobileLoginOwner.adopt(value, "qqmusic")
    }

    fun startLogin(method: String, smallestWidth: Int, oauth: (JSONObject) -> Unit, mobile: (String) -> Unit) = action {
        if (MobileLoginPolicy.shouldUseQqMusicApp(smallestWidth, "qqmusic", method)) {
            var snapshot = core.objectResult("provider_account_snapshot", provider())
            if (snapshot.text("state") !in MobileLoginLeaseRegistryStates) snapshot = core.objectResult("provider_auth_start", provider().put("mobile", true), host = true)
            MobileLoginOwner.adopt(snapshot, "qqmusic")
            refreshAccount()
            MobileLoginPolicy.validateLaunchUrl(snapshot.text("launchUrl"))?.let { mobile(it.authorizationUrl) }
        } else {
            val prepared = core.objectResult("provider_auth_oauth_prepare", provider().put("methodId", method), host = true)
            oauthAttempt = prepared.text("attemptId").takeIf(String::isNotBlank)
            check(oauthAttempt != null) { "登录状态无效" }
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

    fun cancelLogin() = action {
        val attempt = _account.value.attempt
        if (attempt.isNotBlank()) core.invoke("provider_auth_cancel", provider().put("attemptId", attempt), host = true)
        refreshAccount()
    }

    fun signOut() = action {
        core.invoke("provider_sign_out", provider()); refreshAccount(); pageCache.clear(); load(refresh = true)
    }

    fun cacheStats(clear: Boolean = false) = action {
        if (clear) core.invoke("provider_clear_cache", provider())
        val stats = core.objectResult("provider_cache_stats", provider())
        _cache.value = "缓存：${stats.optLong("totalBytes") / 1024 / 1024} MB"
        if (clear) _message.value = "缓存已清理"
    }

    fun createPlaylist(title: String) = action {
        val result = core.objectResult("provider_create_playlist", provider().put("request", JSONObject().put("title", title).put("clientOperationId", UUID.randomUUID().toString())))
        check(result.text("status") in setOf("applied", "reconciled")) { "创建结果未确认，请刷新歌单检查" }
        pageCache.clear(); load(refresh = true)
    }

    fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { _message.value = error.message ?: "操作失败，请重试" }
    }

    companion object {
        private val MobileLoginLeaseRegistryStates = setOf("starting-login", "waiting-for-scan", "waiting-for-confirmation")
        fun provider(id: String = "qqmusic") = JSONObject().put("providerId", id)
        fun titleFor(kind: String) = when (kind) {
            "home" -> "首页"; "discover" -> "发现"; "search" -> "搜索"; "library" -> "媒体库"
            "favorites" -> "我喜欢的音乐"; "account-playlists" -> "我的歌单"; "recent" -> "最近播放"; "statistics" -> "收听统计"
            "settings" -> "设置"; "album" -> "专辑"; "artist" -> "歌手"; "playlist", "account-playlist" -> "歌单"; "player" -> "正在播放"; "queue" -> "播放队列"; else -> "音乐"
        }
    }
}
