package org.yaqmc.android.nativeui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.yaqmc.android.R
import kotlin.math.abs
import kotlin.math.sin

/** Only interpolates a recent authoritative Core sample, never drives playback or seeks. */
internal fun lyricFramePosition(position: Long, duration: Long, running: Boolean, elapsedMs: Long): Long =
    (position + if (running) elapsedMs.coerceIn(0, 1000) else 0).coerceIn(0, duration.coerceAtLeast(position).coerceAtLeast(0))

@Composable
internal fun rememberLyricClock(player: PlayerState): State<Long> {
    val result = remember { mutableLongStateOf(player.position) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player.currentEntry?.id, player.current?.key, player.position, player.sampledAt, player.playing, player.phase, lifecycle) {
        val observedAt = SystemClock.elapsedRealtime()
        result.longValue = player.position
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (player.playing && player.phase == "playing") while (isActive) {
                withFrameNanos { result.longValue = lyricFramePosition(player.position, player.duration, true, SystemClock.elapsedRealtime() - observedAt) }
            }
        }
    }
    return result
}

@Composable
internal fun LyricList(
    lyrics: Lyrics, clock: State<Long>, settings: NativeLyricPreferences,
    seek: (Long) -> Unit, modifier: Modifier = Modifier,
) = key(lyrics.songId) {
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var following by remember { mutableStateOf(true) }
    val active by remember(lyrics, settings.timingOffsetMs) { derivedStateOf { lyrics.activeIndex(clock.value, settings.timingOffsetMs) } }
    LaunchedEffect(dragged) {
        if (dragged) following = false
        else if (!following) { snapshotFlow { list.isScrollInProgress }.first { !it }; delay(4000); following = true }
    }
    LaunchedEffect(lyrics) { following = true }
    BoxWithConstraints(modifier.fillMaxHeight().testTag("lyrics-viewport")) {
        val anchor = with(LocalDensity.current) { (maxHeight * 0.35f).roundToPx() }
        // Song-keyed list state starts at zero; keep a single coroutine responsible for scrolling.
        LaunchedEffect(lyrics, active, following, anchor) {
            if (following && active >= 0) {
                if (settings.spring) list.animateScrollToItem(active, -anchor)
                else list.scrollToItem(active, -anchor)
            }
        }
        val size = (maxHeight.value * 0.085f).coerceIn(22f, 48f) * settings.fontScale
        LazyColumn(Modifier.fillMaxSize(), state = list,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = maxHeight * 0.6f),
            verticalArrangement = Arrangement.spacedBy((size * 0.55f).dp)) {
            if (lyrics.lines.isEmpty()) item { Text(stringResource(R.string.native_no_lyrics), style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
                val selected = index == active
                val passed by remember(lyrics, index, following, settings.timingOffsetMs) { derivedStateOf {
                    following && lyrics.synchronized && line.start != null && lyrics.end(index) <= lyrics.time(clock.value, settings.timingOffsetMs)
                } }
                val focus by animateFloatAsState(if (selected) 1f else 0f,
                    animationSpec = if (settings.spring) spring(stiffness = 220f, dampingRatio = 0.8f) else tween(180), label = "lyric-focus")
                val blur = if (settings.blur && active >= 0 && !selected && following) (abs(index - active).coerceAtMost(3) * 0.7f).dp else 0.dp
                // Keep list indices stable when hiding sung lines; manual browsing reveals them.
                val hidden = settings.hidePassed && passed
                Column(Modifier.fillMaxWidth().testTag("lyric-line-$index")
                    .graphicsLayer { alpha = if (hidden) 0f else 1f; scaleX = if (settings.scale) 0.94f + focus * 0.06f else 1f; scaleY = scaleX }
                    .blur(blur)
                    .clickable(enabled = !hidden && lyrics.seekPosition(index, settings.timingOffsetMs) != null) {
                        lyrics.seekPosition(index, settings.timingOffsetMs)?.let(seek); following = true
                    }.then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier.semantics { this.selected = selected })
                    .padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimedLyricText(line, selected, lyrics, clock, settings, size)
                    if (showLyricSecondary(settings.translation, line.translation, line.text, false))
                        Text(line.translation, fontSize = (size * 0.55f).sp, lineHeight = (size * 0.55f * settings.lineHeight).sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                    if (showLyricSecondary(settings.romanization, line.romanization, line.text, true))
                        Text(line.romanization, fontSize = (size * 0.5f).sp, lineHeight = (size * 0.5f * settings.lineHeight).sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                }
            }
        }
        if (!following && lyrics.synchronized) FilledTonalButton(onClick = { following = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag("lyrics-follow")) { Text(stringResource(R.string.native_lyrics_follow)) }
    }
}

private data class WordRun(val span: LyricWordSpan, val rects: List<Rect>, val width: Float, val rtl: Boolean) {
    val path = Path().apply { rects.forEach(::addRect) }
}

/** Text stays a single layout: wrapping, spaces, CJK and accessibility match the unpainted text. */
@Composable
private fun TimedLyricText(line: LyricLine, selected: Boolean, lyrics: Lyrics, clock: State<Long>, settings: NativeLyricPreferences, size: Float) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val spans = remember(line, lyrics.wordSynchronized) { if (lyrics.wordSynchronized) line.wordSpans() else emptyList() }
    val runs = remember(layout, spans) { layout?.let { measured -> spans.map { span ->
        val rects = (span.start until span.end).groupBy { measured.getLineForOffset(it) }.values.map { offsets ->
            offsets.map { measured.getBoundingBox(it) }.reduce { a, b ->
                Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }
        }
        WordRun(span, rects, rects.sumOf { it.width.toDouble() }.toFloat(), measured.getBidiRunDirection(span.start) == ResolvedTextDirection.Rtl)
    } }.orEmpty() }
    val foreground = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 0.65f else 0.55f)
    val jumpHeight = with(LocalDensity.current) { 4.dp.toPx() }
    Box {
        Text(line.text, Modifier.fillMaxWidth(), fontSize = size.sp, lineHeight = (size * settings.lineHeight).sp,
            fontWeight = FontWeight(settings.fontWeight), color = if (selected && runs.isNotEmpty()) Color.Transparent else if (selected) foreground else base,
            onTextLayout = { layout = it })
        if (selected && runs.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
            val measured = layout ?: return@Canvas
            val time = lyrics.time(clock.value, settings.timingOffsetMs)
            val jumping = if (settings.wordEffect == "jump") runs.filter { time > it.span.word.start && time < it.span.word.end } else emptyList()
            val cutouts = Path().apply { jumping.forEach { run -> run.rects.forEach(::addRect) } }
            clipPath(cutouts, ClipOp.Difference) { drawText(measured, color = base) }
            for (run in runs) {
                val progress = run.span.word.progress(time)
                if (progress <= 0f) continue
                val lift = if (run in jumping) sin(progress * Math.PI).toFloat() * jumpHeight else 0f
                translate(top = -lift) {
                    if (lift > 0f) clipPath(run.path) { drawText(measured, color = base) }
                    var consumed = 0f
                    for (rect in run.rects) {
                        val fade = (run.width * settings.fadeWidth * 0.25f).coerceAtLeast(1f)
                        val front = progress * (run.width + fade) - consumed
                        val edge = if (run.rtl) rect.right - front else rect.left + front
                        val brush = Brush.linearGradient(
                            colors = listOf(foreground, foreground.copy(alpha = 0f)),
                            start = Offset(edge + if (run.rtl) fade else -fade, 0f), end = Offset(edge, 0f),
                        )
                        // The accessible Text uses transparent ink. Override its inherited alpha.
                        clipPath(Path().apply { addRect(rect) }) { drawText(measured, brush = brush, alpha = 1f) }
                        consumed += rect.width
                    }
                }
            }
        }
    }
}
