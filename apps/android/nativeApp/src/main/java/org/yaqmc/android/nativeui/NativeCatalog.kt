package org.yaqmc.android.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import org.yaqmc.android.R

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
internal fun Catalog(model: NativeViewModel, route: NativeRoute, share: (Track) -> Unit, reportDrawn: () -> Unit) {
    val state by model.page.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val rememberedTab by model.discoverTab.collectAsStateWithLifecycle()
    val accountPage = route.kind in setOf("favorites", "recent", "account-playlists", "account-playlist")
    val page = if (accountPage && !account.authenticated) CatalogPage(titleFor(route.kind)) else state.page
    val activeTab = when (route.kind) {
        "discover" -> page.tabs.find { it.id == rememberedTab }?.id ?: page.tabs.firstOrNull()?.id.orEmpty()
        "artist" -> route.tab
        else -> ""
    }
    val sections = page.sections.filter { it.tab == activeTab || it.tab.isBlank() }
    val subtitle = page.subtitle.resolve()
    val list = key(route, activeTab) { rememberLazyListState() }
    LaunchedEffect(state.loading, state.error) { if (!state.loading) reportDrawn() }
    LaunchedEffect(activeTab, page.tabs) { if (route.kind == "discover" && activeTab.isNotBlank() && rememberedTab != activeTab) model.selectDiscoverTab(activeTab) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = (maxWidth / 180.dp).toInt().coerceIn(2, 5)
    Column(Modifier.fillMaxSize()) {
        if (route.kind == "search" && route.query.isNotBlank()) LazyRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("song", "artist", "album", "playlist")) { kind ->
                FilterChip(route.searchKind == kind, { model.search(route.query, kind) }, label = { Text(titleFor(kind).resolve()) }, modifier = Modifier.testTag("search-kind-$kind"))
            }
        }
        if (state.loading && !state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { model.load(refresh = true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().testTag("catalog-list"), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (route.kind !in nativeRootDestinations && subtitle.isNotBlank()) item { Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
            if (accountPage && !account.authenticated) item { AccountLibraryNotice(account, model) }
            if (accountPage && account.authenticated && !account.canRead(route.kind)) item { Text(stringResource(R.string.native_not_supported)) }
            if (page.artwork.isNotBlank()) item { Artwork(page.artwork, Modifier.size(180.dp)) }
            if (page.links.isNotEmpty()) item { EntityLinks(page.links, model) }
            if (page.metadata.isNotEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { page.metadata.forEach { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) } } }
            if (route.kind in setOf("playlist", "account-playlist")) item { PlaylistActions(model, route, page) }
            if (page.tabs.isNotEmpty()) item {
                LazyRow(Modifier.testTag("catalog-tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(page.tabs, key = { it.id }) { tab ->
                    FilterChip(activeTab == tab.id, { if (route.kind == "discover") model.selectDiscoverTab(tab.id) else model.artistTab(tab.id) },
                        label = { Text(tab.title.resolve()) }, modifier = Modifier.testTag("catalog-tab-${tab.id}"))
                } }
            }
            val visibleTracks = sections.flatMap { it.tracks }
            if (visibleTracks.isNotEmpty() && route.kind != "home") item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.play(visibleTracks, shuffle = false) }) { Text(stringResource(R.string.native_play_all)) }
                    OutlinedButton(onClick = { model.play(visibleTracks, shuffle = true) }) { Text(stringResource(R.string.native_shuffle)) }
                    OutlinedButton(onClick = { model.addTracks(visibleTracks) }) { Text(stringResource(R.string.native_add_queue)) }
                }
            }
            state.error?.let { error -> item { ErrorRetry(error, { model.load(refresh = true) }) } }
            if (page.stale) item {
                Text(stringResource(R.string.native_stale), color = MaterialTheme.colorScheme.tertiary)
                TextButton(onClick = { model.load(refresh = true) }) { Text(stringResource(R.string.native_refresh)) }
            }
            page.total?.let { total -> item { Text(stringResource(R.string.native_loaded_total, visibleTracks.size.takeIf { it > 0 } ?: sections.sumOf { it.cards.size }, total)) } }
            sections.forEachIndexed { sectionIndex, section ->
                if (route.kind != "search" && (activeTab.isBlank() || section.tab != activeTab || section.continuation != null)) item("heading-$sectionIndex") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title.resolve(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (section.continuation != null && section.tracks.isNotEmpty()) TextButton(onClick = { model.play(section.tracks, continuation = section.continuation) }) { Text(stringResource(R.string.native_play)) }
                    }
                }
                // The first home row is the three dedicated recommendation entry points.
                if (route.kind == "home" && sectionIndex == 0 && section.cards.isNotEmpty()) item("hero") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(section.cards, key = { it.id }) { card -> CatalogCard(card, model, Modifier.width(220.dp)) } }
                } else {
                    section.cards.chunked(columns).forEachIndexed { rowIndex, row ->
                        item("cards-$sectionIndex-$rowIndex") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { card -> CatalogCard(card, model, Modifier.weight(1f)) }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                itemsIndexed(section.tracks, key = { index, track -> "$sectionIndex-$index-${track.key}" }) { _, track ->
                    TrackRow(track, model, share, play = { model.playTrack(section.tracks, track, section.continuation) })
                }
            }
            if (!state.loading && state.error == null && sections.all { it.cards.isEmpty() && it.tracks.isEmpty() } && (!accountPage || account.authenticated)) item { Text(stringResource(if (route.kind == "search" && route.query.isBlank()) R.string.native_enter_query else R.string.native_empty)) }
            if (page.hasMore && state.loaded && state.error == null) item("pagination") {
                if (state.moreError != null) ErrorRetry(state.moreError!!, { model.load(more = true) })
                else {
                    if (state.loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        LaunchedEffect(route, page.cursor, sections.sumOf { it.cards.size + it.tracks.size }) { model.load(more = true) }
                        TextButton(onClick = { model.load(more = true) }) { Text(stringResource(R.string.native_load_more)) }
                    }
                }
            }
        }
        }
    }
    }
}

@UnstableApi
@Composable
internal fun SearchField(model: NativeViewModel, route: NativeRoute) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(route.query)) }
    val focus = remember { FocusRequester() }
    val manager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (route.query.isBlank()) focus.requestFocus() }
    LaunchedEffect(input.text, input.composition) {
        if (input.composition == null) {
            delay(if (input.text.isBlank()) 0 else 280)
            model.search(input.text)
        }
    }
        TextField(input, { input = it }, Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus).testTag("search-input"),
            placeholder = { Text(stringResource(R.string.native_search_music)) }, singleLine = true, shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.search(input.text); manager.clearFocus() }),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (input.text.isNotEmpty()) IconButton(onClick = { input = TextFieldValue("") }) { Icon(Icons.Default.Close, stringResource(R.string.native_clear_search)) } })
}

@UnstableApi
@Composable
private fun CatalogCard(card: CatalogItem, model: NativeViewModel, modifier: Modifier = Modifier) {
    val preparingCard by model.preparingCard.collectAsStateWithLifecycle()
    val failedCard by model.failedCard.collectAsStateWithLifecycle()
    val preparing = preparingCard == card.playbackKey
    val failure = failedCard?.takeIf { it.first == card.playbackKey }?.second
    Column(modifier) {
        Box {
            Artwork(card.artwork, Modifier.fillMaxWidth().aspectRatio(1f).clickable(enabled = card.navigable && !preparing) { if (card.continuation != null) model.playCard(card) else model.open(card) })
            if (failure != null) Text(failure.resolve(), Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer).padding(start = 8.dp, end = 56.dp, top = 8.dp, bottom = 8.dp)
                .testTag("card-error-${card.playbackKey}"), color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (card.playable) FilledIconButton(onClick = { model.playCard(card) }, enabled = !preparing,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).testTag("card-play-${card.playbackKey}")) {
                if (preparing) CircularProgressIndicator(Modifier.size(22.dp).testTag("card-preparing-${card.playbackKey}"), strokeWidth = 2.dp)
                else if (failure != null) Icon(Icons.Default.Refresh, stringResource(R.string.native_retry))
                else Icon(Icons.Default.PlayArrow, stringResource(R.string.native_play_card, card.titleText?.resolve() ?: card.title))
            }
        }
        Text(card.titleText?.resolve() ?: card.title, Modifier.fillMaxWidth().clickable(enabled = card.navigable && !preparing) { if (card.continuation != null) model.playCard(card) else model.open(card) }.padding(top = 8.dp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text(card.subtitleText?.resolve() ?: card.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@UnstableApi
@Composable
internal fun EntityLinks(links: List<EntityLink>, model: NativeViewModel, compact: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { links.filter { it.title.isNotBlank() }.forEach { link ->
        Text(link.title, Modifier.clickable(enabled = link.id.isNotBlank()) { model.navigate(NativeRoute(link.kind, link.id)) }.padding(vertical = if (compact) 4.dp else 8.dp),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
    } }
}

@Composable
internal fun ErrorRetry(error: NativeText, retry: () -> Unit) {
    Column { Text(error.resolve(), color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text(stringResource(R.string.native_retry)) } }
}
