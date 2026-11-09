package org.yaqmc.android.nativeui

import android.app.Activity
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val selectedRoot by model.rootDestination.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val artwork by remember(model) { model.player.map { it.current?.artwork }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = null)
    var shareTrack by remember { mutableStateOf<Track?>(null) }
    val playerPager = rememberPagerState { 2 }
    val pageStates = rememberSaveableStateHolder()
    val savedPages = remember { linkedSetOf<String>() }
    var playerPresetLayout by rememberSaveable { mutableStateOf(false) }
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
        AnimatedContent(targetState = route, modifier = Modifier.fillMaxSize(),
            contentKey = { it.kind == "player" }, label = "player-transition",
            transitionSpec = {
                (when {
                    targetState.kind == "player" -> slideInVertically(tween(320)) { it } togetherWith ExitTransition.KeepUntilTransitionsFinished
                    initialState.kind == "player" -> EnterTransition.None togetherWith slideOutVertically(tween(280)) { it }
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }).apply { targetContentZIndex = if (targetState.kind == "player") 1f else 0f }
            }) { destination ->
        val playingPage = destination.kind == "player"
        val listeningPage = playingPage || destination.kind == "queue"
        val browsingPage = destination.kind !in nativeUtilityDestinations && !listeningPage
        val pageKey = if (destination.kind == "search") "search:${destination.searchKind}" else "${destination.kind}:${destination.id}:${destination.tab}"
        LaunchedEffect(pageKey) {
            savedPages.remove(pageKey)
            savedPages += pageKey
            while (savedPages.size > 24) pageStates.removeState(savedPages.first().also { savedPages.remove(it) })
        }
        Box(Modifier.fillMaxSize().then(if (playingPage) Modifier.testTag("player-surface") else Modifier)) {
        if (listeningPage) PlayerBackdrop(preferences.appearance, artwork, Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                if (!playingPage) {
                TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = if (listeningPage) Color.Transparent else colors.background), title = {
                    if (destination.kind == "search") SearchField(model, destination)
                    else PageTitle(model, destination)
                }, navigationIcon = {
                    if (destination.kind !in roots.map { it.first }) IconButton(onClick = { if (!model.back()) model.navigate(NativeRoute(), true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.native_back))
                    }
                }, actions = {
                    if (destination.kind in nativeRootDestinations && destination.kind != "search") {
                    val nickname = account.nickname.ifBlank { stringResource(R.string.native_guest) }
                    val accountLabel = stringResource(R.string.native_open_account, nickname)
                    IconButton(onClick = { model.navigate(NativeRoute("account")) }, modifier = Modifier.testTag("open-account").semantics { contentDescription = accountLabel }) {
                        if (account.avatar.isNotBlank()) AsyncImage(account.avatar, null, Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.AccountCircle, null)
                    }
                    }
                    if (destination.kind in nativeRootDestinations || destination.kind == "account") IconButton(onClick = { model.navigate(NativeRoute("settings")) }, modifier = Modifier.testTag("open-settings")) {
                        Icon(Icons.Default.Settings, stringResource(R.string.native_settings))
                    }
                })
                }
            },
            bottomBar = {
                if (browsingPage) {
                Column {
                    MiniPlayer(model, openShare)
                    if (!wide) NavigationBar(containerColor = colors.surface) { roots.forEach { (kind, label) ->
                        NavigationBarItem(selected = selectedRoot == kind, onClick = { model.selectRoot(kind) },
                            icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.testTag("nav-$kind"))
                    } }
                }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && browsingPage) NavigationRail(Modifier.fillMaxHeight(), containerColor = colors.surface) { roots.forEach { (kind, label) ->
                    NavigationRailItem(selected = selectedRoot == kind, onClick = { model.selectRoot(kind) },
                        icon = { NavIcon(kind, label) }, label = { Text(label) }, modifier = Modifier.weight(1f, fill = false).testTag("nav-$kind"))
                } }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    pageStates.SaveableStateProvider(pageKey) {
                    when (destination.kind) {
                        "library" -> Library(model)
                        "account" -> Account(model, login)
                        "settings" -> Settings(model)
                        "storage-settings" -> StorageSettings(model)
                        "statistics" -> NativeStatistics(model)
                        "diagnostics" -> NativeDiagnostics(model)
                        "issue-report" -> NativeIssueReport(model)
                        "about" -> NativeAbout(model)
                        "lyric-settings" -> NativeLyricSettings(preferences.lyrics, model::setLyrics) { model.navigate(NativeRoute("lyric-presets")) }
                        "lyric-presets" -> NativeLyricPresets(preferences.lyricPresets, model::setLyricPresets)
                        "appearance-settings" -> NativeAppearanceSettings(preferences.appearance, dark, model::setAppearance,
                            { model.setAppearance(NativeAppearancePreferences()); model.setTheme("system") }, model::notify,
                            preferences.theme, model::setTheme)
                        "player" -> NowPlaying(model, openShare, playerPager, playerPresetLayout, { playerPresetLayout = it })
                        "queue" -> Queue(model, openShare)
                        else -> Catalog(model, destination, openShare, reportDrawn)
                    }
                    }
                }
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

@UnstableApi
@Composable
private fun PageTitle(model: NativeViewModel, route: NativeRoute) {
    val page by model.page.collectAsStateWithLifecycle()
    val queueCount by remember(model) { model.player.map { it.queue.size }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = 0)
    val title = when {
        route.kind == "queue" -> stringResource(R.string.native_queue_count, queueCount)
        route.kind !in nativeRootDestinations && route.kind !in nativeUtilityDestinations && page.loaded -> page.page.title.resolve()
        else -> titleFor(route.kind).resolve()
    }
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("page-title"))
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
            Text(listOf(track.artists, track.album).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.duration), style = MaterialTheme.typography.labelSmall)
        TrackMenu(track, model, share)
    }
}

@UnstableApi
@Composable
private fun Account(model: NativeViewModel, login: (String) -> Unit) {
    val account by model.account.collectAsStateWithLifecycle()
    val busy by model.accountBusy.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("account-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (account.avatar.isBlank()) Icon(Icons.Default.AccountCircle, null, Modifier.size(56.dp))
                else AsyncImage(account.avatar, null, Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column { Text(account.nickname.ifBlank { stringResource(R.string.native_guest) }, style = MaterialTheme.typography.titleLarge); Text(accountLabel(account.state, account.upstreamCode), style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.state != "authenticated") {
                    Button(enabled = !busy && !account.loginPending, onClick = { login("qq") }) { Text(stringResource(R.string.native_qq_login)) }
                    OutlinedButton(enabled = !busy && !account.loginPending, onClick = { login("wechat") }) { Text(stringResource(R.string.native_wechat_login)) }
                }
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
        if (account.authenticated) item { TextButton(enabled = !busy, onClick = { confirmSignOut = true }) { Text(stringResource(R.string.native_sign_out)) } }
    }
    if (confirmSignOut) Confirm(stringResource(R.string.native_confirm_sign_out), { confirmSignOut = false }) { model.signOut() }
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
