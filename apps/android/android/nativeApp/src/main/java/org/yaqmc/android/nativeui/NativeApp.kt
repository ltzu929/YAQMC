package org.yaqmc.android.nativeui

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
internal fun NativeApp(model: NativeViewModel, login: (String) -> Unit, share: (Track) -> Unit, reportDrawn: () -> Unit) {
    val theme by model.theme.collectAsStateWithLifecycle()
    val route by model.route.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val dark = theme == "dark" || theme == "system" && isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xffd5ee8d), onPrimary = Color(0xff171b0d),
        background = Color(0xff11120f), surface = Color(0xff181a16), onBackground = Color(0xfff0f2e9), onSurface = Color(0xfff0f2e9))
        else lightColorScheme(primary = Color(0xff708f35), onPrimary = Color.White,
            background = Color(0xfff5f5f1), surface = Color(0xffffffff), onBackground = Color(0xff1d201a), onSurface = Color(0xff1d201a))
    val roots = listOf("home" to "首页", "discover" to "发现", "search" to "搜索", "library" to "媒体库")
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    BackHandler(route.kind != "home") { if (!model.back()) model.navigate(NativeRoute(), root = true) }
    MaterialTheme(colorScheme = colors) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(message) { message?.let { snack.showSnackbar(it); model.dismissMessage() } }
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(title = { Text("YAQMC", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    if (route.kind !in roots.map { it.first }) IconButton(onClick = { if (!model.back()) model.navigate(NativeRoute(), true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }, actions = { IconButton(onClick = { model.navigate(NativeRoute("settings")) }) { Icon(Icons.Default.Settings, "设置") } })
            },
            bottomBar = {
                Column {
                    if (route.kind != "player") MiniPlayer(model)
                    if (!wide) NavigationBar { roots.forEach { (kind, label) ->
                        NavigationBarItem(selected = route.kind == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                            icon = { NavIcon(kind, label) }, label = { Text(label) })
                    } }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail { roots.forEach { (kind, label) ->
                    NavigationRailItem(selected = route.kind == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                        icon = { NavIcon(kind, label) }, label = { Text(label) })
                } }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (route.kind) {
                        "library" -> Library(model)
                        "settings" -> Settings(model, login)
                        "player" -> NowPlaying(model)
                        "queue" -> Queue(model, share)
                        else -> Catalog(model, route, share, reportDrawn)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIcon(kind: String, label: String) = Icon(when (kind) {
    "home" -> Icons.Default.Home; "search" -> Icons.Default.Search; "library" -> Icons.Default.Favorite; else -> Icons.Default.Star
}, label)

@Composable
private fun Artwork(source: Any?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("♫", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        AsyncImage(model = source, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@UnstableApi
@Composable
private fun MiniPlayer(model: NativeViewModel) {
    val player by model.player.collectAsStateWithLifecycle()
    val current = player.current ?: return
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().clickable { model.navigate(NativeRoute("player")) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(current.artwork, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.artists, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Transport(if (player.playing) "Ⅱ" else "▶", if (player.playing) "暂停" else "播放") { model.command("player_toggle") }
            Transport(">|", "下一首") { model.command("player_next") }
            IconButton(onClick = { model.navigate(NativeRoute("queue")) }) { Icon(Icons.Default.Menu, "播放队列") }
        }
    }
}

@Composable
private fun Transport(text: String, label: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.semantics { contentDescription = label }) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

@UnstableApi
@Composable
private fun Catalog(model: NativeViewModel, route: NativeRoute, share: (Track) -> Unit, reportDrawn: () -> Unit) {
    val state by model.page.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(route.query) }
    LaunchedEffect(state.loading, state.error) { if (!state.loading) reportDrawn() }
    val page = state.page
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (route.kind == "search") item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索音乐") }, singleLine = true,
                    trailingIcon = { IconButton(onClick = { model.navigate(NativeRoute("search", query = query.trim(), searchKind = route.searchKind), true) }) { Icon(Icons.Default.Search, "搜索") } })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { listOf("song" to "歌曲", "artist" to "歌手", "album" to "专辑", "playlist" to "歌单").forEach { (kind, title) ->
                        FilterChip(route.searchKind == kind, { model.navigate(NativeRoute("search", query = query.trim(), searchKind = kind), true) }, label = { Text(title) }, modifier = Modifier.padding(end = 8.dp))
                    } }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (page.subtitle.isNotBlank()) Text(page.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { model.load(refresh = true) }) { Icon(Icons.Default.Refresh, "刷新") }
            }
        }
        if (page.artwork.isNotBlank()) item { Artwork(page.artwork, Modifier.size(160.dp)) }
        if (page.tracks.isNotEmpty()) item { Button(onClick = { model.play(page.tracks) }) { Text("播放全部") } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { model.load(refresh = true) }) { Text("重试") } } }
        page.sections.forEachIndexed { sectionIndex, section ->
            item("heading-$sectionIndex") { Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (section.cards.isNotEmpty()) item("cards-$sectionIndex") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(section.cards) { _, card ->
                        Column(Modifier.width(148.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = card.kind != "statistic") { model.open(card) }) {
                            if (card.kind != "statistic") Artwork(card.artwork, Modifier.size(148.dp))
                            Text(card.title, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(card.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            itemsIndexed(section.tracks, key = { index, track -> "$sectionIndex-$index-${track.id}" }) { _, track ->
                TrackRow(track, model, share, play = { model.play(section.tracks, track) })
            }
        }
        if (!state.loading && state.error == null && page.sections.isEmpty()) item { Text(if (route.kind == "search" && route.query.isBlank()) "输入关键词开始搜索" else "暂无内容") }
        if (page.hasMore) item { OutlinedButton(enabled = !state.loading, onClick = { model.load(more = true) }) { Text("加载更多") } }
    }
}

@UnstableApi
@Composable
private fun TrackRow(track: Track, model: NativeViewModel, share: (Track) -> Unit, play: () -> Unit, remove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = play).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artists} · ${track.album}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.duration), style = MaterialTheme.typography.labelSmall)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "${track.title}的更多操作") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("加入队列") }, onClick = { menu = false; model.command("player_add_to_queue", JSONObject().put("track", track.json)) })
                DropdownMenuItem(text = { Text(if (track.favorite) "取消收藏" else "收藏") }, onClick = { menu = false; model.favorite(track) })
                DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; share(track) })
                remove?.let { DropdownMenuItem(text = { Text("移出队列") }, onClick = { menu = false; it() }) }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Library(model: NativeViewModel) {
    val account by model.account.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("媒体库", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text(account.nickname, style = MaterialTheme.typography.titleMedium) }
        listOf("favorites" to "我喜欢的音乐", "account-playlists" to "我的歌单", "recent" to "最近播放", "statistics" to "收听统计").forEach { (kind, title) ->
            item { ElevatedCard(onClick = { model.navigate(NativeRoute(kind)) }, modifier = Modifier.fillMaxWidth()) { Text(title, Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium) } }
        }
    }
}

@UnstableApi
@Composable
private fun Settings(model: NativeViewModel, login: (String) -> Unit) {
    val account by model.account.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val quality by model.quality.collectAsStateWithLifecycle()
    val cache by model.cache.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.cacheStats() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(account.avatar, Modifier.size(56.dp)); Spacer(Modifier.width(16.dp))
                Column { Text(account.nickname, style = MaterialTheme.typography.titleLarge); Text(accountLabel(account.state), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.state != "authenticated") {
                    Button(onClick = { login("qq") }) { Text("QQ 登录") }
                    OutlinedButton(onClick = { login("wechat") }) { Text("微信登录") }
                } else OutlinedButton(onClick = { confirmSignOut = true }) { Text("退出登录") }
            }
        }
        if (account.qr.isNotBlank()) item {
            val qr = remember(account.qr) { runCatching { Base64.decode(account.qr.substringAfter(','), Base64.DEFAULT) }.getOrNull() }
            AsyncImage(qr, "登录二维码", Modifier.size(220.dp).background(Color.White))
            Text("请使用官方客户端扫码并确认授权")
        }
        if (account.attempt.isNotBlank()) item { TextButton(onClick = { model.cancelLogin() }) { Text("取消本次登录") } }
        item { Text("外观", style = MaterialTheme.typography.titleLarge) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) -> FilterChip(theme == value, { model.setTheme(value) }, label = { Text(label) }) } } }
        item { Text("默认音质", style = MaterialTheme.typography.titleLarge) }
        item { QualityPicker(quality) { model.changeQuality(it) } }
        item { Text(cache); OutlinedButton(onClick = { confirmClear = true }) { Text("清理缓存") } }
        item { Text("YAQMC Native · ${org.yaqmc.android.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmSignOut) Confirm("退出当前账号？", { confirmSignOut = false }) { model.signOut() }
    if (confirmClear) Confirm("清理本地音乐和封面缓存？", { confirmClear = false }) { model.cacheStats(clear = true) }
}

@Composable
private fun Confirm(title: String, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, confirmButton = { TextButton(onClick = { close(); confirm() }) { Text("确认") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun QualityPicker(current: String, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choices = listOf("automatic" to "自动", "standard" to "标准", "high" to "高品质", "lossless" to "无损", "hi-res" to "Hi-Res", "master" to "臻品")
    Box {
        OutlinedButton(onClick = { open = true }) { Text(choices.find { it.first == current }?.second ?: "选择音质") }
        DropdownMenu(open, { open = false }) { choices.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; select(value) }) } }
    }
}

@UnstableApi
@Composable
private fun Queue(model: NativeViewModel, share: (Track) -> Unit) {
    val player by model.player.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("播放队列 · ${player.queue.size}", style = MaterialTheme.typography.headlineMedium) }
        itemsIndexed(player.queue) { index, track ->
            TrackRow(track, model, share, play = { model.command("player_play_from_queue", JSONObject().put("index", index)) }, remove = {
                player.entryIds.getOrNull(index)?.let { model.command("player_remove_queue_entry", JSONObject().put("entryId", it)) }
            })
        }
    }
}

@UnstableApi
@Composable
private fun NowPlaying(model: NativeViewModel) {
    val player by model.player.collectAsStateWithLifecycle()
    val lyrics by model.lyrics.collectAsStateWithLifecycle()
    val track = player.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (track == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("选择一首歌曲开始播放") }; return }
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(track.artwork, Modifier.size(if (wide) 128.dp else 150.dp))
            Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artists, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
            Slider(value = seeking ?: player.position.coerceIn(0, player.duration).toFloat(), onValueChange = { seeking = it },
                valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), onValueChangeFinished = {
                    seeking?.let { model.command("player_seek", JSONObject().put("positionMs", it.toLong())) }; seeking = null
                }, modifier = Modifier.semantics { contentDescription = "播放进度" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(player.position)); Text(formatTime(player.duration)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Transport("|<", "上一首") { model.command("player_previous") }
                FilledTonalButton(onClick = { model.command("player_toggle") }) { Text(if (player.playing) "暂停" else "播放") }
                Transport(">|", "下一首") { model.command("player_next") }
                IconButton(onClick = { model.navigate(NativeRoute("queue")) }) { Icon(Icons.Default.Menu, "播放队列") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val mode = when (player.mode) { "sequential" -> "shuffle"; "shuffle" -> "repeat-one"; else -> "sequential" }
                    model.command("player_set_primary_playback_mode", JSONObject().put("mode", mode))
                }) { Text(when (player.mode) { "shuffle" -> "随机播放"; "repeat-one" -> "单曲循环"; else -> "顺序播放" }) }
                QualityPicker(player.quality) { model.changeQuality(it, current = true) }
            }
            player.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (wide) Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(0.45f).fillMaxHeight()) { item { controls() } }
        LyricList(lyrics, player.position, model, Modifier.weight(0.55f))
    } else Column(Modifier.fillMaxSize()) { controls(); LyricList(lyrics, player.position, model, Modifier.weight(1f)) }
}

@UnstableApi
@Composable
private fun LyricList(lyrics: Lyrics, position: Long, model: NativeViewModel, modifier: Modifier) {
    val list = rememberLazyListState()
    val active = lyrics.activeIndex(position)
    LaunchedEffect(active, lyrics.songId) { if (active >= 0 && !list.isScrollInProgress) list.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxHeight(), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (lyrics.lines.isEmpty()) item { Text("暂无歌词", style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(lyrics.lines) { index, line ->
            Column(Modifier.fillMaxWidth().clickable(enabled = line.start != null) { model.command("player_seek", JSONObject().put("positionMs", ((line.start ?: 0) - lyrics.offset).coerceAtLeast(0))) }.padding(vertical = 4.dp)) {
                Text(line.text, style = MaterialTheme.typography.titleLarge, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (line.translation.isNotBlank()) Text(line.translation, style = MaterialTheme.typography.bodySmall)
                if (line.romanization.isNotBlank()) Text(line.romanization, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun accountLabel(state: String) = when (state) {
    "authenticated" -> "已登录"; "restoring-session" -> "正在恢复登录"; "starting-login" -> "正在准备授权"
    "waiting-for-scan" -> "等待扫码"; "waiting-for-confirmation" -> "等待确认"; "guest" -> "访客模式"
    "session-expired", "reauthentication-required", "expired" -> "登录已过期，请重新登录"
    "network-error" -> "登录网络异常，请重试"; "secure-store-unavailable" -> "安全存储不可用"; else -> "请重新登录"
}
