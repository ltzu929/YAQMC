package org.yaqmc.android.nativeui

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.yaqmc.android.R

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
internal fun NativeApp(model: NativeViewModel, login: (String) -> Unit, share: (Track, SongShareKind) -> Unit, reportDrawn: () -> Unit) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, configuration, preferences.language) { context.withNativeLanguage(preferences) }
    val density = LocalDensity.current
    // A configuration Context no longer points back to the Activity. Retain its picker registry.
    val activityResults = checkNotNull(LocalActivityResultRegistryOwner.current)
    CompositionLocalProvider(LocalNativeContext provides localized, LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
        LocalActivityResultRegistryOwner provides activityResults,
        LocalDensity provides Density(density.density, density.fontScale * preferences.appearance.fontScale / 100f)) {
        NativeShell(model, login, share, reportDrawn)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun NativeShell(model: NativeViewModel, login: (String) -> Unit, share: (Track, SongShareKind) -> Unit, reportDrawn: () -> Unit) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val route by model.route.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val artwork by remember(model) { model.player.map { it.current?.artwork }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = null)
    var shareTrack by remember { mutableStateOf<Track?>(null) }
    val openShare: (Track) -> Unit = { shareTrack = it }
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
    val colors = remember(preferences.appearance, dark) { nativeColorScheme(preferences.appearance, dark) }
    val typography = remember(preferences.appearance.fontFamily) { nativeTypography(preferences.appearance.fontFamily) }
    val roots = nativeRootDestinations.map { it to titleFor(it).resolve() }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    BackHandler(route.kind != "home") { if (!model.back()) model.navigate(NativeRoute(), root = true) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        val snack = remember { SnackbarHostState() }
        val messageText = message?.resolve()
        LaunchedEffect(messageText) { messageText?.let { snack.showSnackbar(it); model.dismissMessage() } }
        Box(Modifier.fillMaxSize()) {
        NativeBackdrop(preferences.appearance, artwork, Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface, scrolledContainerColor = colors.surface), title = { Text("YAQMC", fontWeight = FontWeight.Bold) }, navigationIcon = {
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
                    if (route.kind != "player") MiniPlayer(model, openShare)
                    if (!wide) NavigationBar(containerColor = colors.surface) { roots.forEach { (kind, label) ->
                        NavigationBarItem(selected = selectedRoot(route.kind) == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                            icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.testTag("nav-$kind"))
                    } }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail(Modifier.fillMaxHeight(), containerColor = colors.surface) { roots.forEach { (kind, label) ->
                    NavigationRailItem(selected = selectedRoot(route.kind) == kind, onClick = { model.navigate(NativeRoute(kind), true) },
                        icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.weight(1f, fill = false).testTag("nav-$kind"))
                } }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (route.kind) {
                        "library" -> Library(model)
                        "settings" -> Settings(model, login)
                        "statistics" -> NativeStatistics(model)
                        "lyric-settings" -> NativeLyricSettings(preferences.lyrics, model::setLyrics) { model.navigate(NativeRoute("lyric-presets")) }
                        "lyric-presets" -> NativeLyricPresets(preferences.lyricPresets, model::setLyricPresets)
                        "appearance-settings" -> NativeAppearanceSettings(preferences.appearance, dark, model::setAppearance,
                            { model.setAppearance(NativeAppearancePreferences()); model.setTheme("system") }, model::notify)
                        "player" -> NowPlaying(model, openShare)
                        "queue" -> Queue(model, openShare)
                        else -> Catalog(model, route, openShare, reportDrawn)
                    }
                }
            }
        }
        }
        AddToPlaylistDialog(model)
        shareTrack?.let { SongShareDialog(it, { shareTrack = null }, share) }
        }
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
internal fun TrackRow(track: Track, model: NativeViewModel, share: (Track) -> Unit, play: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = play).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title.ifBlank { stringResource(R.string.native_unknown_song) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            EntityLinks(track.artistLinks + listOfNotNull(track.albumLink), model, compact = true)
        }
        Text(formatTime(track.duration), style = MaterialTheme.typography.labelSmall)
        TrackMenu(track, model, share)
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
    val cacheBusy by model.cacheBusy.collectAsStateWithLifecycle()
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
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
        item { OutlinedButton(onClick = { model.navigate(NativeRoute("appearance-settings")) }) { Text(stringResource(R.string.native_appearance_customize)) } }
        item { Text(stringResource(R.string.native_language), style = MaterialTheme.typography.titleLarge) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to stringResource(R.string.native_system), "zh-CN" to stringResource(R.string.native_chinese), "en-US" to stringResource(R.string.native_english)).forEach { (value, label) -> FilterChip(preferences.language == value, { model.setLanguage(value) }, label = { Text(label) }, modifier = Modifier.testTag("language-$value")) } } }
        item { Text(stringResource(R.string.native_default_quality), style = MaterialTheme.typography.titleLarge) }
        item { QualityPicker(quality, enabled = !qualityBusy) { model.changeQuality(it) } }
        item { OutlinedButton(onClick = { model.navigate(NativeRoute("lyric-settings")) }) { Text(stringResource(R.string.native_lyric_settings)) } }
        item {
            Text(stringResource(R.string.native_cache_breakdown), style = MaterialTheme.typography.titleLarge)
            val details = cache
            if (details == null) Text(stringResource(R.string.native_cache_loading))
            else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.native_cache_total, readableBytes(details.totalBytes)))
                Text(stringResource(R.string.native_cache_media, details.mediaEntries, readableBytes(details.mediaBytes)))
                Text(stringResource(R.string.native_cache_artwork, details.artworkEntries, readableBytes(details.artworkBytes)))
                Text(stringResource(R.string.native_cache_metadata, details.metadataEntries))
                Text(stringResource(R.string.native_cache_lyrics, details.lyricEntries))
                Text(stringResource(R.string.native_cache_limits, readableBytes(details.mediaLimitBytes), readableBytes(details.artworkLimitBytes)),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (cacheBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.cacheStats() }, enabled = !cacheBusy) { Text(stringResource(R.string.native_refresh)) }
                OutlinedButton(onClick = { confirmClear = true }, enabled = !cacheBusy) { Text(stringResource(R.string.native_clear_cache)) }
            }
        }
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
internal fun accountLabel(state: String, upstreamCode: Int? = null) = when (state) {
    "authenticated" -> stringResource(R.string.native_authenticated); "restoring-session" -> stringResource(R.string.native_restoring); "starting-login" -> stringResource(R.string.native_starting_login)
    "waiting-for-scan" -> stringResource(R.string.native_waiting_scan); "waiting-for-confirmation" -> stringResource(R.string.native_waiting_confirmation); "guest" -> stringResource(R.string.native_guest_mode)
    "session-expired", "reauthentication-required" -> stringResource(R.string.native_session_expired)
    "expired" -> stringResource(R.string.native_login_expired); "cancelled" -> stringResource(R.string.native_login_cancelled)
    "rejected" -> stringResource(R.string.native_login_rejected); "protocol-error" -> stringResource(if (upstreamCode == 20279) R.string.native_login_device_limit else R.string.native_login_protocol)
    "network-error" -> stringResource(R.string.native_login_network); "secure-store-unavailable" -> stringResource(R.string.native_secure_store); else -> stringResource(R.string.native_sign_in_again)
}
