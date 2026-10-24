package org.yaqmc.android.nativeui

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import org.json.JSONObject
import org.yaqmc.android.R

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
internal fun NativeApp(model: NativeViewModel, login: (String) -> Unit, share: (Track) -> Unit, reportDrawn: () -> Unit) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, configuration, preferences.language) { context.withNativeLanguage(preferences) }
    CompositionLocalProvider(LocalNativeContext provides localized, LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
        NativeShell(model, login, share, reportDrawn)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun NativeShell(model: NativeViewModel, login: (String) -> Unit, share: (Track) -> Unit, reportDrawn: () -> Unit) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val route by model.route.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = preferences.isDark(systemDark)
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    SideEffect {
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xffd5ee8d), onPrimary = Color(0xff171b0d),
        background = Color(0xff11120f), surface = Color(0xff181a16), onBackground = Color(0xfff0f2e9), onSurface = Color(0xfff0f2e9))
        else lightColorScheme(primary = Color(0xff708f35), onPrimary = Color.White,
            background = Color(0xfff5f5f1), surface = Color(0xffffffff), onBackground = Color(0xff1d201a), onSurface = Color(0xff1d201a))
    val roots = nativeRootDestinations.map { it to titleFor(it).resolve() }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    BackHandler(route.kind != "home") { if (!model.back()) model.navigate(NativeRoute(), root = true) }
    MaterialTheme(colorScheme = colors) {
        val snack = remember { SnackbarHostState() }
        val messageText = message?.resolve()
        LaunchedEffect(messageText) { messageText?.let { snack.showSnackbar(it); model.dismissMessage() } }
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(title = { Text("YAQMC", fontWeight = FontWeight.Bold) }, navigationIcon = {
                    if (route.kind !in roots.map { it.first }) IconButton(onClick = { if (!model.back()) model.navigate(NativeRoute(), true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.native_back))
                    }
                }, actions = {
                    IconButton(onClick = { model.navigate(NativeRoute("search"), true) }) { Icon(Icons.Default.Search, stringResource(R.string.native_search)) }
                    val themeLabel = stringResource(if (dark) R.string.native_use_light else R.string.native_use_dark)
                    IconButton(onClick = { model.setTheme(preferences.toggledTheme(systemDark)) }, modifier = Modifier.semantics { contentDescription = themeLabel }) {
                        Text(if (dark) "☀" else "☾", style = MaterialTheme.typography.titleLarge)
                    }
                    val nickname = account.nickname.ifBlank { stringResource(R.string.native_guest) }
                    val accountLabel = stringResource(R.string.native_open_account, nickname)
                    IconButton(onClick = { model.navigate(NativeRoute("settings")) }, modifier = Modifier.semantics { contentDescription = accountLabel }) {
                        if (account.avatar.isNotBlank()) AsyncImage(account.avatar, null, Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.AccountCircle, null)
                    }
                })
            },
            bottomBar = {
                Column {
                    if (route.kind != "player") MiniPlayer(model)
                    if (!wide) NavigationBar { roots.forEach { (kind, label) ->
                        NavigationBarItem(selected = selectedRoot(route.kind) == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                            icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.testTag("nav-$kind"))
                    } }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail(Modifier.fillMaxHeight()) { roots.forEach { (kind, label) ->
                    NavigationRailItem(selected = selectedRoot(route.kind) == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                        icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.weight(1f, fill = false).testTag("nav-$kind"))
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
        AddToPlaylistDialog(model)
    }
}

@Composable
private fun NavIcon(kind: String, label: String) = Icon(when (kind) {
    "home" -> Icons.Default.Home; "search" -> Icons.Default.Search; "library" -> Icons.Default.Favorite; else -> Icons.Default.Star
}, label)

@Composable
internal fun Artwork(source: Any?, modifier: Modifier = Modifier) {
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
                Text(current.title.ifBlank { stringResource(R.string.native_unknown_song) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.artists, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Transport(if (player.playing) "Ⅱ" else "▶", if (player.playing) stringResource(R.string.native_pause) else stringResource(R.string.native_play)) { model.command("player_toggle") }
            Transport(">|", stringResource(R.string.native_next)) { model.command("player_next") }
            IconButton(onClick = { model.navigate(NativeRoute("queue")) }) { Icon(Icons.Default.Menu, stringResource(R.string.native_queue)) }
        }
    }
}

@Composable
private fun Transport(text: String, label: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.semantics { contentDescription = label }) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

@UnstableApi
@Composable
internal fun TrackRow(track: Track, model: NativeViewModel, share: (Track) -> Unit, play: () -> Unit, remove: (() -> Unit)? = null) {
    var menu by remember(track.key) { mutableStateOf(false) }
    val account by model.account.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val pending by model.pendingFavorites.collectAsStateWithLifecycle()
    val favorite = favorites[track.key] ?: track.favorite
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = play).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title.ifBlank { stringResource(R.string.native_unknown_song) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            EntityLinks(track.artistLinks + listOfNotNull(track.albumLink), model, compact = true)
        }
        Text(formatTime(track.duration), style = MaterialTheme.typography.labelSmall)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.native_more_track, track.title.ifBlank { stringResource(R.string.native_unknown_song) })) }
            NativeDropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.native_add_queue)) }, onClick = { menu = false; model.command("player_add_to_queue", JSONObject().put("track", track.json)) })
                DropdownMenuItem(text = { Text(if (track.key in pending) stringResource(R.string.native_working) else if (favorite) stringResource(R.string.native_unfavorite) else stringResource(R.string.native_favorite)) },
                    enabled = track.key !in pending && (!account.authenticated || (account.canFavorite && track.writable)), onClick = { menu = false; model.favorite(track) })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_add_playlist)) }, enabled = !account.authenticated || account.canWrite(track), onClick = { menu = false; model.openPicker(track) })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_song_details)) }, onClick = { menu = false; model.navigate(NativeRoute("song", track.id)) })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_share)) }, onClick = { menu = false; share(track) })
                remove?.let { DropdownMenuItem(text = { Text(stringResource(R.string.native_remove_queue)) }, onClick = { menu = false; it() }) }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Library(model: NativeViewModel) {
    val account by model.account.collectAsStateWithLifecycle()
    val destinations = listOf("favorites", "account-playlists", "recent", "statistics").map { it to titleFor(it).resolve() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.native_library), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text(account.nickname.ifBlank { stringResource(R.string.native_guest) }, style = MaterialTheme.typography.titleMedium) }
        item { AccountLibraryNotice(account, model) }
        destinations.forEach { (kind, title) ->
            item { ElevatedCard(onClick = { model.navigate(NativeRoute(kind)) }, modifier = Modifier.fillMaxWidth()) { Text(title, Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium) } }
        }
    }
}

@UnstableApi
@Composable
private fun Settings(model: NativeViewModel, login: (String) -> Unit) {
    val account by model.account.collectAsStateWithLifecycle()
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val busy by model.accountBusy.collectAsStateWithLifecycle()
    val quality by model.quality.collectAsStateWithLifecycle()
    val cache by model.cache.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.cacheStats() }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.native_settings), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(account.avatar, Modifier.size(56.dp)); Spacer(Modifier.width(16.dp))
                Column { Text(account.nickname.ifBlank { stringResource(R.string.native_guest) }, style = MaterialTheme.typography.titleLarge); Text(accountLabel(account.state, account.upstreamCode), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.state != "authenticated") {
                    Button(enabled = !busy && !account.loginPending, onClick = { login("qq") }) { Text(stringResource(R.string.native_qq_login)) }
                    OutlinedButton(enabled = !busy && !account.loginPending, onClick = { login("wechat") }) { Text(stringResource(R.string.native_wechat_login)) }
                } else OutlinedButton(enabled = !busy, onClick = { confirmSignOut = true }) { Text(stringResource(R.string.native_sign_out)) }
            }
        }
        item { AccountMembership(account) }
        item { TextButton(enabled = !busy, onClick = { model.refreshAccountSession() }) { Text(stringResource(R.string.native_refresh_account)) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (account.qr.isNotBlank()) item {
            val qr = remember(account.qr) { runCatching { Base64.decode(account.qr.substringAfter(','), Base64.DEFAULT) }.getOrNull() }
            AsyncImage(qr, stringResource(R.string.native_login_qr), Modifier.size(220.dp).background(Color.White))
            Text(stringResource(R.string.native_scan_hint))
            account.expiresAt?.let { Text(stringResource(R.string.native_qr_expires, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, LocalConfiguration.current.locales[0]).format(java.util.Date(it)))) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { login("qq") }) { Text(stringResource(R.string.native_open_official)) }
                TextButton(enabled = !busy, onClick = { model.refreshQr() }) { Text(stringResource(R.string.native_refresh_qr)) }
            }
        }
        if (account.loginPending) item { TextButton(enabled = !busy, onClick = { model.cancelLogin() }) { Text(stringResource(R.string.native_cancel_login)) } }
        item { Text(stringResource(R.string.native_appearance), style = MaterialTheme.typography.titleLarge) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to stringResource(R.string.native_system), "light" to stringResource(R.string.native_light), "dark" to stringResource(R.string.native_dark)).forEach { (value, label) -> FilterChip(preferences.theme == value, { model.setTheme(value) }, label = { Text(label) }, modifier = Modifier.testTag("theme-$value")) } } }
        item { Text(stringResource(R.string.native_language), style = MaterialTheme.typography.titleLarge) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to stringResource(R.string.native_system), "zh-CN" to stringResource(R.string.native_chinese), "en-US" to stringResource(R.string.native_english)).forEach { (value, label) -> FilterChip(preferences.language == value, { model.setLanguage(value) }, label = { Text(label) }, modifier = Modifier.testTag("language-$value")) } } }
        item { Text(stringResource(R.string.native_default_quality), style = MaterialTheme.typography.titleLarge) }
        item { QualityPicker(quality) { model.changeQuality(it) } }
        item { Text(cache?.let { stringResource(R.string.native_cache_mb, it) } ?: stringResource(R.string.native_cache_loading)); OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.native_clear_cache)) } }
        item { Text("YAQMC Native · ${org.yaqmc.android.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmSignOut) Confirm(stringResource(R.string.native_confirm_sign_out), { confirmSignOut = false }) { model.signOut() }
    if (confirmClear) Confirm(stringResource(R.string.native_confirm_clear_cache), { confirmClear = false }) { model.cacheStats(clear = true) }
}

@Composable
internal fun Confirm(title: String, close: () -> Unit, confirm: () -> Unit) {
    NativeAlertDialog(onDismissRequest = close, title = { Text(title) }, confirmButton = { TextButton(onClick = { close(); confirm() }) { Text(stringResource(R.string.native_confirm)) } }, dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.native_cancel)) } })
}

@Composable
private fun QualityPicker(current: String, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choices = listOf("automatic" to stringResource(R.string.native_quality_automatic), "standard" to stringResource(R.string.native_quality_standard), "high" to stringResource(R.string.native_quality_high), "lossless" to stringResource(R.string.native_quality_lossless), "hi-res" to stringResource(R.string.native_quality_hires), "master" to stringResource(R.string.native_quality_master))
    Box {
        OutlinedButton(onClick = { open = true }) { Text(choices.find { it.first == current }?.second ?: stringResource(R.string.native_select_quality)) }
        NativeDropdownMenu(open, { open = false }) { choices.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; select(value) }) } }
    }
}

@UnstableApi
@Composable
private fun Queue(model: NativeViewModel, share: (Track) -> Unit) {
    val player by model.player.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.native_queue_count, player.queue.size), style = MaterialTheme.typography.headlineMedium) }
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
    if (track == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.native_select_song)) }; return }
    val progressLabel = stringResource(R.string.native_progress)
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(track.artwork, Modifier.size(if (wide) 128.dp else 150.dp))
            Text(track.title.ifBlank { stringResource(R.string.native_unknown_song) }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artists, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
            Slider(value = seeking ?: player.position.coerceIn(0, player.duration).toFloat(), onValueChange = { seeking = it },
                valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), onValueChangeFinished = {
                    seeking?.let { model.command("player_seek", JSONObject().put("positionMs", it.toLong())) }; seeking = null
                }, modifier = Modifier.semantics { contentDescription = progressLabel })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(player.position)); Text(formatTime(player.duration)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Transport("|<", stringResource(R.string.native_previous)) { model.command("player_previous") }
                FilledTonalButton(onClick = { model.command("player_toggle") }) { Text(if (player.playing) stringResource(R.string.native_pause) else stringResource(R.string.native_play)) }
                Transport(">|", stringResource(R.string.native_next)) { model.command("player_next") }
                IconButton(onClick = { model.navigate(NativeRoute("queue")) }) { Icon(Icons.Default.Menu, stringResource(R.string.native_queue)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val mode = when (player.mode) { "sequential" -> "shuffle"; "shuffle" -> "repeat-one"; else -> "sequential" }
                    model.command("player_set_primary_playback_mode", JSONObject().put("mode", mode))
                }) { Text(when (player.mode) { "shuffle" -> stringResource(R.string.native_shuffle); "repeat-one" -> stringResource(R.string.native_repeat_one); else -> stringResource(R.string.native_sequential) }) }
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
        if (lyrics.lines.isEmpty()) item { Text(stringResource(R.string.native_no_lyrics), style = MaterialTheme.typography.titleLarge) }
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

@Composable
internal fun accountLabel(state: String, upstreamCode: Int? = null) = when (state) {
    "authenticated" -> stringResource(R.string.native_authenticated); "restoring-session" -> stringResource(R.string.native_restoring); "starting-login" -> stringResource(R.string.native_starting_login)
    "waiting-for-scan" -> stringResource(R.string.native_waiting_scan); "waiting-for-confirmation" -> stringResource(R.string.native_waiting_confirmation); "guest" -> stringResource(R.string.native_guest_mode)
    "session-expired", "reauthentication-required" -> stringResource(R.string.native_session_expired)
    "expired" -> stringResource(R.string.native_login_expired); "cancelled" -> stringResource(R.string.native_login_cancelled)
    "rejected" -> stringResource(R.string.native_login_rejected); "protocol-error" -> stringResource(if (upstreamCode == 20279) R.string.native_login_device_limit else R.string.native_login_protocol)
    "network-error" -> stringResource(R.string.native_login_network); "secure-store-unavailable" -> stringResource(R.string.native_secure_store); else -> stringResource(R.string.native_sign_in_again)
}
