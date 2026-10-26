package org.yaqmc.android.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.yaqmc.android.R

@UnstableApi
@Composable
internal fun MiniPlayer(model: NativeViewModel, share: (Track) -> Unit) {
    val player by model.player.collectAsStateWithLifecycle()
    val current = player.current ?: return
    Surface(tonalElevation = 3.dp) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { model.navigate(NativeRoute("player")) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(current.artwork, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(current.title.ifBlank { stringResource(R.string.native_unknown_song) }, Modifier.clickable(enabled = current.providerId == "qqmusic") { model.navigate(NativeRoute("song", current.id)) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    EntityLinks(current.artistLinks.take(1).filter { current.providerId == "qqmusic" }, model, compact = true)
                    PlaybackStatus(player, compact = true)
                }
                Transport("|<", stringResource(R.string.native_previous)) { model.command("player_previous") }
                Transport(if (player.playing) "Ⅱ" else "▶", stringResource(if (player.playing) R.string.native_pause else R.string.native_play)) { model.command("player_toggle") }
                Transport(">|", stringResource(R.string.native_next)) { model.command("player_next") }
                TrackMenu(current, model, share, playbackOptions = true)
            }
            if (player.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { (player.position.toFloat() / player.duration.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
internal fun Transport(text: String, label: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.semantics { contentDescription = label }) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun PlaybackStatus(player: PlayerState, compact: Boolean = false) {
    val status = NativePlayback.status(player)
    status?.let { Text(it.resolve(), Modifier.testTag("playback-status"), color = if (player.phase.endsWith("error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis) }
    NativePlayback.fallback(player.selection.fallback)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis) }
    if (player.selection.preview && player.selection.fallback != "preview-only") Text(stringResource(R.string.native_fallback_preview), style = MaterialTheme.typography.bodySmall)
    if (!compact && player.error != null) Text(player.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun capabilityText(capability: QualityCapability): String = stringResource(R.string.native_quality_capabilities,
    stringResource(when (capability.entitlement) { "allowed" -> R.string.native_rights_allowed; "denied" -> R.string.native_rights_denied; else -> R.string.native_unknown }),
    stringResource(when (capability.resource) { "available" -> R.string.native_resource_available; "unavailable" -> R.string.native_resource_unavailable; else -> R.string.native_unknown }),
    stringResource(when (capability.client) { "supported" -> R.string.native_client_supported; "unsupported" -> R.string.native_client_unsupported; else -> R.string.native_unknown }))

@Composable
private fun QualityChoices(current: String, selection: SourceSelection?, select: (String) -> Unit) {
    NativePlayback.qualities.forEach { value ->
        val capability = selection?.capabilities?.find { it.quality == value }
        DropdownMenuItem(text = {
            Column {
                Text((if (current == value) "✓ " else "") + stringResource(NativePlayback.qualityLabel(value)))
                if (value == "automatic" && selection?.resolved?.isNotBlank() == true) Text(stringResource(R.string.native_quality_resolved, stringResource(NativePlayback.qualityLabel(selection.resolved))), style = MaterialTheme.typography.bodySmall)
                capability?.let { Text(capabilityText(it), style = MaterialTheme.typography.bodySmall) }
            }
        }, enabled = capability?.unavailable != true, onClick = { select(value) }, modifier = Modifier.testTag("quality-$value"))
    }
}

@Composable
internal fun QualityPicker(current: String, selection: SourceSelection? = null, enabled: Boolean = true, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.testTag("quality-picker")) { Text(stringResource(NativePlayback.qualityLabel(current))) }
        NativeDropdownMenu(open && enabled, { open = false }) { QualityChoices(current, selection) { open = false; select(it) } }
    }
}

@UnstableApi
@Composable
internal fun TrackMenu(track: Track, model: NativeViewModel, share: (Track) -> Unit, queueEntry: QueueItem? = null, playbackOptions: Boolean = false) {
    var menu by remember(track.key, queueEntry?.id) { mutableStateOf(false) }
    var qualityDialog by remember(track.key) { mutableStateOf(false) }
    val account by model.account.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val pending by model.pendingFavorites.collectAsStateWithLifecycle()
    val player by model.player.collectAsStateWithLifecycle()
    val queueBusy by model.queueBusy.collectAsStateWithLifecycle()
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
    val favorite = favorites[track.key] ?: track.favorite
    val available = track.providerId == "qqmusic"
    val isCurrent = queueEntry != null && queueEntry.id == player.currentEntry?.id
    Box {
        IconButton(onClick = { menu = true }, modifier = if (queueEntry != null) Modifier.testTag("queue-menu-${queueEntry.id}") else Modifier) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.native_more_track, track.title.ifBlank { stringResource(R.string.native_unknown_song) }))
        }
        NativeDropdownMenu(menu, { menu = false }) {
            if (playbackOptions) {
                DropdownMenuItem(text = { Text(stringResource(R.string.native_queue)) }, onClick = { menu = false; model.navigate(NativeRoute("queue")) })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_select_quality)) }, enabled = available && !qualityBusy, onClick = { menu = false; qualityDialog = true })
            }
            if (queueEntry != null && !isCurrent) {
                DropdownMenuItem(text = { Text(stringResource(R.string.native_play_immediately)) }, enabled = available, onClick = { menu = false; model.playQueueEntry(queueEntry.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.native_play_next)) }, enabled = available && player.authoritative && !queueBusy, onClick = { menu = false; model.playNextQueueEntry(queueEntry.id) })
                for ((direction, label) in listOf(-1 to R.string.native_move_up, 1 to R.string.native_move_down)) {
                    val target = player.entries.getOrNull(queueEntry.index + direction)
                    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = target != null && player.authoritative && !queueBusy,
                        onClick = { menu = false; target?.let { model.moveQueueEntry(queueEntry.id, it.id) } })
                }
            }
            if (queueEntry == null && !playbackOptions) DropdownMenuItem(text = { Text(stringResource(R.string.native_add_queue)) }, enabled = available, onClick = { menu = false; model.command("player_add_to_queue", JSONObject().put("track", track.json)) })
            DropdownMenuItem(text = { Text(stringResource(if (track.key in pending) R.string.native_working else if (favorite) R.string.native_unfavorite else R.string.native_favorite)) },
                enabled = track.key !in pending && (!account.authenticated || (account.canFavorite && track.writable)), onClick = { menu = false; model.favorite(track) })
            DropdownMenuItem(text = { Text(stringResource(R.string.native_add_playlist)) }, enabled = !account.authenticated || account.canWrite(track), onClick = { menu = false; model.openPicker(track) })
            DropdownMenuItem(text = { Text(stringResource(R.string.native_song_details)) }, enabled = available, onClick = { menu = false; model.navigate(NativeRoute("song", track.id)) })
            DropdownMenuItem(text = { Text(stringResource(R.string.native_share)) }, enabled = available, onClick = { menu = false; share(track) })
            if (queueEntry != null && !isCurrent) DropdownMenuItem(text = { Text(stringResource(R.string.native_remove_queue)) }, enabled = player.authoritative && !queueBusy,
                onClick = { menu = false; model.removeQueueEntry(queueEntry.id) })
        }
    }
    if (qualityDialog) NativeAlertDialog(onDismissRequest = { qualityDialog = false }, title = { Text(stringResource(R.string.native_select_quality)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { QualityChoices(player.selection.requested, player.selection) { qualityDialog = false; model.changeQuality(it, current = true) } } },
        confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text(stringResource(R.string.native_cancel)) } })
}

@Composable
internal fun SongShareDialog(track: Track, close: () -> Unit, share: (Track, SongShareKind) -> Unit) {
    NativeAlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.native_share_song)) }, text = {
        Column { SongShareKind.entries.forEach { kind ->
            TextButton(onClick = { close(); share(track, kind) }, modifier = Modifier.fillMaxWidth().testTag("share-${kind.name}")) { Text(stringResource(kind.label)) }
        } }
    }, confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.native_cancel)) } })
}

@UnstableApi
@Composable
internal fun Queue(model: NativeViewModel, share: (Track) -> Unit) {
    val player by model.player.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(player)
    val busy by model.queueBusy.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var targetId by remember { mutableStateOf<String?>(null) }
    fun updateTarget() {
        val visible = list.layoutInfo.visibleItemsInfo
        targetId = visible.firstOrNull { item -> item.key in latest.upcomingIds && dragCenter >= item.offset && dragCenter <= item.offset + item.size }?.key as? String
    }
    fun finishDrag(commit: Boolean) {
        if (commit && draggedId != null && targetId != null) model.moveQueueEntry(draggedId!!, targetId!!)
        draggedId = null
        targetId = null
    }
    LaunchedEffect(draggedId) {
        while (isActive && draggedId != null) {
            val layout = list.layoutInfo
            val edge = 100f
            val scroll = when {
                dragCenter < layout.viewportStartOffset + edge -> -18f
                dragCenter > layout.viewportEndOffset - edge -> 18f
                else -> 0f
            }
            if (scroll != 0f) { list.scrollBy(scroll); updateTarget() }
            delay(16)
        }
    }
    LaunchedEffect(player.entryIds, player.currentIndex, busy) {
        if (draggedId != null && (busy || draggedId !in player.upcomingIds)) finishDrag(false)
    }
    LazyColumn(Modifier.fillMaxSize().testTag("queue-list"), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("heading") { Text(stringResource(R.string.native_queue_count, player.queue.size), style = MaterialTheme.typography.headlineMedium) }
        player.currentEntry?.let { entry ->
            item("current-heading") { Text(stringResource(R.string.native_now_playing), fontWeight = FontWeight.SemiBold) }
            item("current") { QueueRow(entry, model, share, true) }
        }
        item("next-heading") {
            Text(stringResource(R.string.native_up_next, player.upcoming.size), style = MaterialTheme.typography.titleLarge)
            if (player.order == "shuffle") Text(stringResource(R.string.native_shuffle_traversal), style = MaterialTheme.typography.bodySmall)
            if (player.repeat == "one") Text(stringResource(R.string.native_repeat_one_active), style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (player.upcoming.isEmpty()) Text(stringResource(R.string.native_queue_empty), Modifier.padding(vertical = 16.dp))
        }
        items(player.upcoming, key = { it.id }) { entry ->
            val dragging = draggedId == entry.id
            Row(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f).graphicsLayer {
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }
                translationY = if (dragging && item != null) dragCenter - item.offset - item.size / 2f else 0f
                alpha = if (dragging) 0.85f else 1f
            }
                .background(if (targetId == entry.id || dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface), verticalAlignment = Alignment.CenterVertically) {
                val moveLabel = stringResource(R.string.native_move_track, entry.track.title)
                Box(Modifier.size(48.dp).testTag("queue-drag-${entry.id}").semantics { contentDescription = moveLabel }
                    .pointerInput(entry.id, busy, player.authoritative) {
                        if (!busy && player.authoritative) detectDragGestures(onDragStart = {
                            list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }?.let { visible ->
                                draggedId = entry.id; dragCenter = visible.offset + visible.size / 2f; targetId = entry.id
                            }
                        }, onDragCancel = { finishDrag(false) }, onDragEnd = { finishDrag(true) }) { change, delta ->
                            change.consume(); dragCenter += delta.y; updateTarget()
                        }
                    }, contentAlignment = Alignment.Center) { Text("☰", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(Modifier.weight(1f)) { QueueRow(entry, model, share, false) }
            }
        }
    }
}

@UnstableApi
@Composable
private fun QueueRow(entry: QueueItem, model: NativeViewModel, share: (Track) -> Unit, current: Boolean) {
    val track = entry.track
    Row(Modifier.fillMaxWidth().testTag("queue-row-${entry.id}").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (current) Artwork(track.artwork, Modifier.size(44.dp))
        else IconButton(enabled = track.providerId == "qqmusic", onClick = { model.playQueueEntry(entry.id) }, modifier = Modifier.testTag("queue-play-${entry.id}")) { Icon(Icons.Default.PlayArrow, stringResource(R.string.native_play_track, track.title)) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(track.title, Modifier.clickable(enabled = track.providerId == "qqmusic") { model.navigate(NativeRoute("song", track.id)) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (track.providerId == "qqmusic") EntityLinks(track.artistLinks + listOfNotNull(track.albumLink), model, compact = true)
            else Text(stringResource(R.string.native_unsupported_provider), style = MaterialTheme.typography.bodySmall)
        }
        Text(formatTime(track.duration), style = MaterialTheme.typography.labelSmall)
        TrackMenu(track, model, share, queueEntry = entry)
    }
}

@UnstableApi
@Composable
internal fun NowPlaying(model: NativeViewModel, share: (Track) -> Unit) {
    val player by model.player.collectAsStateWithLifecycle()
    val lyrics by model.lyrics.collectAsStateWithLifecycle()
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val pending by model.pendingFavorites.collectAsStateWithLifecycle()
    val track = player.current
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val preset = preferences.lyricPresets.current()
    val lyricClock = rememberLyricClock(player)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (track == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.native_select_song)) }; return }
    val progressLabel = stringResource(R.string.native_progress)
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(track.artwork, Modifier.size(((if (wide) 128f else 150f) * preset.artworkScale).dp)
                .then(if (preset.layout == "vinyl") Modifier.clip(CircleShape) else Modifier))
            Text(track.title.ifBlank { stringResource(R.string.native_unknown_song) }, Modifier.clickable(enabled = track.providerId == "qqmusic") { model.navigate(NativeRoute("song", track.id)) },
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.providerId == "qqmusic") EntityLinks(track.artistLinks + listOfNotNull(track.albumLink), model, compact = true)
            else Text(stringResource(R.string.native_unsupported_provider))
            var seeking by remember(player.currentEntry?.id, track.key) { mutableStateOf<Float?>(null) }
            Slider(value = seeking ?: player.position.coerceIn(0, player.duration).toFloat(), onValueChange = { seeking = it }, enabled = player.duration > 0,
                valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), onValueChangeFinished = {
                    seeking?.let { model.command("player_seek", JSONObject().put("positionMs", it.toLong())) }; seeking = null
                }, modifier = Modifier.semantics { contentDescription = progressLabel })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(seeking?.toLong() ?: player.position)); Text(formatTime(player.duration)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Transport("|<", stringResource(R.string.native_previous)) { model.command("player_previous") }
                FilledTonalButton(onClick = { model.command("player_toggle") }) { Text(stringResource(if (player.playing) R.string.native_pause else R.string.native_play)) }
                Transport(">|", stringResource(R.string.native_next)) { model.command("player_next") }
                IconButton(onClick = { model.navigate(NativeRoute("queue")) }) { Icon(Icons.Default.Menu, stringResource(R.string.native_queue)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val mode = when (player.mode) { "sequential" -> "shuffle"; "shuffle" -> "repeat-one"; else -> "sequential" }
                    model.command("player_set_primary_playback_mode", JSONObject().put("mode", mode))
                }, modifier = Modifier.testTag("playback-mode")) { Text(stringResource(when (player.mode) { "shuffle" -> R.string.native_shuffle; "repeat-one" -> R.string.native_repeat_one; else -> R.string.native_sequential })) }
                QualityPicker(player.selection.requested, player.selection, enabled = track.providerId == "qqmusic" && !qualityBusy) { model.changeQuality(it, current = true) }
                val favorite = favorites[track.key] ?: track.favorite
                IconButton(enabled = track.key !in pending && (!account.authenticated || (account.canFavorite && track.writable)), onClick = { model.favorite(track) }) {
                    if (track.key in pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(if (favorite) R.string.native_unfavorite else R.string.native_favorite))
                }
                TrackMenu(track, model, share)
                TextButton(onClick = { model.navigate(NativeRoute("lyric-settings")) }) { Text(stringResource(R.string.native_lyric_settings)) }
            }
            if (qualityBusy || player.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (player.quality.isNotBlank()) Text(stringResource(R.string.native_current_quality, stringResource(NativePlayback.qualityLabel(player.quality))), style = MaterialTheme.typography.bodySmall)
            PlaybackStatus(player)
            if (player.phase.endsWith("error") && player.retryable) TextButton(onClick = { model.command("player_play") }) { Text(stringResource(R.string.native_retry)) }
        }
    }
    val lyricSeek: (Long) -> Unit = { model.command("player_seek", JSONObject().put("positionMs", it)) }
    if (preset.layout == "immersive") {
        // Keep the actual player and Core clock: the editor preview is never mounted here.
        Column(Modifier.fillMaxSize().testTag("player-layout-immersive")) {
            LyricList(lyrics, lyricClock, preferences.lyrics, lyricSeek, Modifier.weight(preset.lyricsFraction))
            Column(Modifier.weight(1f - preset.lyricsFraction).verticalScroll(rememberScrollState())
                .testTag("player-controls")) { controls() }
        }
    } else if (wide) Row(Modifier.fillMaxSize().testTag("player-layout-${preset.layout}")) {
        Column(Modifier.weight(1f - preset.lyricsFraction).fillMaxHeight()
            .verticalScroll(rememberScrollState()).testTag("player-controls")) { controls() }
        LyricList(lyrics, lyricClock, preferences.lyrics, lyricSeek, Modifier.weight(preset.lyricsFraction))
    } else BoxWithConstraints(Modifier.fillMaxSize().testTag("player-layout-${preset.layout}")) {
        // Classic preserves the existing 72%-height controls cap by default.
        val maxControlsHeight = maxHeight * (0.72f - (preset.lyricsFraction - 0.55f) * 0.5f)
        Column {
            Column(Modifier.heightIn(max = maxControlsHeight).verticalScroll(rememberScrollState())
                .testTag("player-controls")) { controls() }
            LyricList(lyrics, lyricClock, preferences.lyrics, lyricSeek, Modifier.weight(1f))
        }
    }
}
