package org.yaqmc.android.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.yaqmc.android.R

/** The listening surface has its own chrome; playback and lyric state remain owned by the model. */
@UnstableApi
@Composable
internal fun NowPlaying(
    model: NativeViewModel, share: (Track) -> Unit, pager: PagerState,
    presetLayout: Boolean, selectPresetLayout: (Boolean) -> Unit,
) {
    val player by model.player.collectAsStateWithLifecycle()
    val lyrics by model.lyrics.collectAsStateWithLifecycle()
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val clock = rememberLyricClock(player)
    val track = player.current
    val scope = rememberCoroutineScope()
    val selectPage: (Int) -> Unit = { index ->
        selectPresetLayout(false)
        scope.launch { pager.animateScrollToPage(index) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!model.back()) model.navigate(NativeRoute(), true) }, modifier = Modifier.testTag("player-collapse")) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.native_player_collapse), Modifier.size(30.dp))
            }
            Row(Modifier.weight(1f).selectableGroup(), horizontalArrangement = Arrangement.Center) {
                listOf("cover" to R.string.native_player_cover, "lyrics" to R.string.native_player_lyrics).forEachIndexed { index, (value, label) ->
                    val selected = if (presetLayout) index == 1 else pager.targetPage == index
                    Column(Modifier.selectable(selected, role = Role.Tab, onClick = { selectPage(index) })
                        .testTag("player-tab-$value").padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.55f))
                        }
                        Box(Modifier.width(16.dp).height(2.dp).background(
                            if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                    }
                }
            }
            if (track != null) TrackMenu(track, model, share, playbackOptions = true, presetLayout = { selectPresetLayout(true) })
            else Spacer(Modifier.size(48.dp))
        }
        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.native_select_song)) }
        } else if (presetLayout) {
            PresetNowPlaying(model, share)
        } else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("player-pages")) { page ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val availableHeight = maxHeight
                val landscape = maxWidth >= 600.dp && maxWidth > maxHeight
                if (page == 1) {
                    val header: @Composable (Modifier) -> Unit = { modifier ->
                        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                            Artwork(track.artwork, Modifier.size(48.dp).clickable { selectPage(0) })
                            Box(Modifier.weight(1f).padding(start = 14.dp)) { PlayerMetadata(track, model, compact = true) }
                            PlayerFavorite(track, model)
                        }
                    }
                    val lyricPane: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier.clipToBounds()) {
                            LyricList(lyrics, clock, preferences.lyrics,
                                { model.command("player_seek", JSONObject().put("positionMs", it)) },
                                Modifier.fillMaxSize(), baseSize = 30f, fadeEdges = true)
                        }
                    }
                    if (landscape) {
                        Row(Modifier.widthIn(max = 1100.dp).fillMaxSize().align(Alignment.Center).padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(0.48f).fillMaxHeight()) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                    header(Modifier.fillMaxWidth())
                                }
                                PlayerControls(player, model, compact = true)
                            }
                            lyricPane(Modifier.weight(0.52f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.widthIn(max = 960.dp).fillMaxSize().align(Alignment.Center).padding(top = 12.dp)) {
                            header(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                            lyricPane(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp))
                            PlayerControls(player, model, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
                        }
                    }
                } else if (landscape) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        PlayerCover(track, Modifier.weight(1f).fillMaxHeight()) { selectPage(1) }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                                PlayerSongInfo(track, player, model)
                            }
                            PlayerControls(player, model, compact = true)
                        }
                    }
                } else {
                    val horizontalPadding = if (maxWidth < 360.dp) 20.dp else 28.dp
                    Column(Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.Center).padding(horizontal = horizontalPadding).padding(bottom = 20.dp)) {
                        PlayerCover(track, Modifier.weight(1f).fillMaxWidth()) { selectPage(1) }
                        // Metadata can scroll at large font sizes; playback controls keep their position.
                        Column(Modifier.heightIn(max = availableHeight * 0.38f).verticalScroll(rememberScrollState()).testTag("player-main-controls")) {
                            PlayerSongInfo(track, player, model)
                            PlayerLyricTeaser(lyrics, clock, preferences.lyrics) { selectPage(1) }
                        }
                        Spacer(Modifier.height(16.dp))
                        PlayerControls(player, model)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerBackdrop(settings: NativeAppearancePreferences, artwork: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        NativeBackdrop(if (settings.background == "default") settings.copy(background = "artwork", influence = 65) else settings,
            artwork, Modifier.matchParentSize())
        if (settings.background == "default") Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.background.copy(alpha = 0.05f), MaterialTheme.colorScheme.background.copy(alpha = 0.9f)))))
    }
}

@Composable
private fun PlayerCover(track: Track, modifier: Modifier, showLyrics: () -> Unit) {
    val label = stringResource(R.string.native_player_show_lyrics)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, (maxHeight - 40.dp).coerceAtLeast(0.dp), 380.dp)
        Artwork(track.artwork, Modifier.size(side).shadow(18.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = label, onClick = showLyrics)
            .semantics { contentDescription = label }.testTag("player-cover"))
    }
}

@UnstableApi
@Composable
private fun PlayerMetadata(track: Track, model: NativeViewModel, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        Text(track.title.ifBlank { stringResource(R.string.native_unknown_song) },
            Modifier.clickable(enabled = track.providerId == "qqmusic") { model.navigate(NativeRoute("song", track.id)) },
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            track.artistLinks.forEach { artist ->
                Text(artist.title, Modifier.clickable(enabled = track.providerId == "qqmusic" && artist.id.isNotBlank()) {
                    model.navigate(NativeRoute("artist", artist.id))
                }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlayerSongInfo(track: Track, player: PlayerState, model: NativeViewModel) {
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayerMetadata(track, model) }
            PlayerFavorite(track, model)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QualityPicker(player.selection.requested, player.selection, enabled = track.providerId == "qqmusic" && !qualityBusy, compact = true) { model.changeQuality(it, current = true) }
            track.albumLink?.let { album ->
                Text(album.title, Modifier.weight(1f).clickable(enabled = track.providerId == "qqmusic" && album.id.isNotBlank()) {
                    model.navigate(NativeRoute("album", album.id))
                }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlayerFavorite(track: Track, model: NativeViewModel) {
    val account by model.account.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val pending by model.pendingFavorites.collectAsStateWithLifecycle()
    val favorite = favorites[track.key] ?: track.favorite
    IconButton(enabled = track.key !in pending && (!account.authenticated || (account.canFavorite && track.writable)),
        onClick = { model.favorite(track) }, modifier = Modifier.testTag("player-favorite")) {
        if (track.key in pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            stringResource(if (favorite) R.string.native_unfavorite else R.string.native_favorite), Modifier.size(26.dp),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
    }
}

@Composable
private fun PlayerLyricTeaser(lyrics: Lyrics, clock: State<Long>, settings: NativeLyricPreferences, showLyrics: () -> Unit) {
    val line by remember(lyrics, clock, settings.timingOffsetMs) { derivedStateOf {
        lyrics.lines.getOrNull(lyrics.activeIndex(clock.value, settings.timingOffsetMs))?.text
            ?: lyrics.lines.firstOrNull()?.text
    } }
    TextButton(onClick = showLyrics, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxWidth().testTag("player-lyric-teaser")) {
        Text(line ?: stringResource(R.string.native_player_show_lyrics), Modifier.fillMaxWidth(), maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun PlayerControls(player: PlayerState, model: NativeViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    var seeking by remember(player.currentEntry?.id, player.current?.key) { mutableStateOf<Float?>(null) }
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
    val progressLabel = stringResource(R.string.native_progress)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(value = seeking ?: player.position.coerceIn(0, player.duration).toFloat(), onValueChange = { seeking = it },
            valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), enabled = player.duration > 0,
            onValueChangeFinished = { seeking?.let { model.command("player_seek", JSONObject().put("positionMs", it.toLong())) }; seeking = null },
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = progressLabel }.testTag("player-progress"),
            thumb = { Box(Modifier.size(12.dp).background(if (player.duration > 0) accent else muted, CircleShape)) },
            track = { state ->
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(muted)) {
                    Box(Modifier.fillMaxWidth((state.value / player.duration.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(accent))
                }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(seeking?.toLong() ?: player.position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            Text(formatTime(player.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        }
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val mode = when (player.mode) { "sequential" -> "shuffle"; "shuffle" -> "repeat-one"; else -> "sequential" }
                model.command("player_set_primary_playback_mode", JSONObject().put("mode", mode))
            }, modifier = Modifier.testTag("playback-mode")) {
                Icon(painterResource(when (player.mode) { "shuffle" -> R.drawable.ic_player_shuffle; "repeat-one" -> R.drawable.ic_player_repeat_one; else -> R.drawable.ic_player_repeat }),
                    stringResource(when (player.mode) { "shuffle" -> R.string.native_shuffle; "repeat-one" -> R.string.native_repeat_one; else -> R.string.native_sequential }),
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            IconButton(onClick = { model.command("player_previous") }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_player_skip_previous), stringResource(R.string.native_previous), Modifier.size(32.dp))
            }
            FilledIconButton(onClick = { model.command("player_toggle") }, modifier = Modifier.size(if (compact) 60.dp else 76.dp).testTag("player-toggle"),
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Icon(painterResource(if (player.playing) R.drawable.ic_player_pause else R.drawable.ic_player_play_arrow),
                    stringResource(if (player.playing) R.string.native_pause else R.string.native_play), Modifier.size(if (compact) 32.dp else 40.dp))
            }
            IconButton(onClick = { model.command("player_next") }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_player_skip_next), stringResource(R.string.native_next), Modifier.size(32.dp))
            }
            IconButton(onClick = { model.navigate(NativeRoute("queue")) }) {
                Icon(painterResource(R.drawable.ic_player_queue_music), stringResource(R.string.native_queue), Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
        if (qualityBusy || player.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        PlaybackStatus(player)
        if (player.phase.endsWith("error") && player.retryable) TextButton(onClick = { model.command("player_play") }) { Text(stringResource(R.string.native_retry)) }
    }
}
