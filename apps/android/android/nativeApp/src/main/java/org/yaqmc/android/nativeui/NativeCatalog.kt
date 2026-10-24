package org.yaqmc.android.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val list = key(route, activeTab) { rememberLazyListState() }
    LaunchedEffect(state.loading, state.error) { if (!state.loading) reportDrawn() }
    LaunchedEffect(activeTab, page.tabs) { if (route.kind == "discover" && activeTab.isNotBlank() && rememberedTab != activeTab) model.selectDiscoverTab(activeTab) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = (maxWidth / 180.dp).toInt().coerceIn(2, 5)
    Column(Modifier.fillMaxSize()) {
        // Keep input outside the lazy list so typing and result replacement cannot steal focus.
        if (route.kind == "search") SearchField(model, route)
        LazyColumn(Modifier.fillMaxSize().testTag("catalog-list"), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(page.title.resolve(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        if (page.subtitle.resolve().isNotBlank()) Text(page.subtitle.resolve(), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { model.load(refresh = true) }, enabled = !state.loading && !state.loadingMore) { Icon(Icons.Default.Refresh, stringResource(R.string.native_refresh)) }
                }
            }
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
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { ErrorRetry(error, { model.load(refresh = true) }) } }
            if (page.stale) item {
                Text(stringResource(R.string.native_stale), color = MaterialTheme.colorScheme.tertiary)
                TextButton(onClick = { model.load(refresh = true) }) { Text(stringResource(R.string.native_refresh)) }
            }
            page.total?.let { total -> item { Text(stringResource(R.string.native_loaded_total, visibleTracks.size.takeIf { it > 0 } ?: sections.sumOf { it.cards.size }, total)) } }
            sections.forEachIndexed { sectionIndex, section ->
                item("heading-$sectionIndex") {
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

@UnstableApi
@Composable
private fun SearchField(model: NativeViewModel, route: NativeRoute) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(route.query)) }
    val focus = remember { FocusRequester() }
    val manager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(input.text, input.composition) {
        if (input.composition == null) {
            delay(if (input.text.isBlank()) 0 else 280)
            model.search(input.text)
        }
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("search-input"),
            label = { Text(stringResource(R.string.native_search_music)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.search(input.text); manager.clearFocus() }),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (input.text.isNotEmpty()) IconButton(onClick = { input = TextFieldValue("") }) { Icon(Icons.Default.Close, stringResource(R.string.native_clear_search)) } })
        if (input.text.isNotBlank()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("song", "artist", "album", "playlist")) { kind ->
                FilterChip(route.searchKind == kind, { model.search(input.text, kind) }, label = { Text(titleFor(kind).resolve()) }, modifier = Modifier.testTag("search-kind-$kind"))
            }
        }
    }
}

@UnstableApi
@Composable
private fun CatalogCard(card: CatalogItem, model: NativeViewModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box {
            Artwork(card.artwork, Modifier.fillMaxWidth().aspectRatio(1f).clickable(enabled = card.navigable) { if (card.continuation != null) model.playCard(card) else model.open(card) })
            if (card.playable) FilledIconButton(onClick = { model.playCard(card) }, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                Icon(Icons.Default.PlayArrow, stringResource(R.string.native_play_card, card.titleText?.resolve() ?: card.title))
            }
        }
        Text(card.titleText?.resolve() ?: card.title, Modifier.fillMaxWidth().clickable(enabled = card.navigable) { if (card.continuation != null) model.playCard(card) else model.open(card) }.padding(top = 8.dp),
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
