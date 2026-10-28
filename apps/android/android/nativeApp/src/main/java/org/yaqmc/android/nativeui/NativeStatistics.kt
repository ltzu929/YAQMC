package org.yaqmc.android.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import androidx.media3.common.util.UnstableApi
import org.yaqmc.android.R

internal data class StatisticsEntity(val id: String, val providerId: String, val title: String, val subtitle: String, val listenedMs: Long)
internal data class StatisticsDimension(val label: String, val listenedMs: Long)
internal data class StatisticsDay(val startMs: Long, val listenedMs: Long)
internal data class NativeStatisticsSnapshot(
    val recordCount: Long, val listeningMs: Long, val playCount: Long, val completed: Long,
    val skipRate: Double, val databaseBytes: Long, val daily: List<StatisticsDay>,
    val songs: List<StatisticsEntity>, val artists: List<StatisticsEntity>, val albums: List<StatisticsEntity>,
    val qualities: List<StatisticsDimension>, val providers: List<StatisticsDimension>,
) {
    companion object {
        fun parse(json: JSONObject): NativeStatisticsSnapshot {
            fun entities(key: String) = json.optJSONArray(key).objects().map { row ->
                StatisticsEntity(row.text("id"), row.text("providerId"), row.text("title"),
                    row.text("subtitle"), row.optLong("listenedMs").coerceAtLeast(0))
            }
            fun dimensions(key: String) = json.optJSONArray(key).objects().map { row ->
                StatisticsDimension(row.text("key"), row.optLong("listenedMs").coerceAtLeast(0))
            }
            return NativeStatisticsSnapshot(
                json.optLong("recordCount").coerceAtLeast(0),
                json.optLong("qualifiedListeningMs").coerceAtLeast(0),
                json.optLong("qualifiedPlayCount").coerceAtLeast(0),
                json.optLong("completedCount").coerceAtLeast(0),
                json.optDouble("skipRate", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0,
                json.optLong("databaseBytes").coerceAtLeast(0),
                json.optJSONArray("daily").objects().map { day ->
                    StatisticsDay(day.optLong("dayStartMs"), day.optLong("listenedMs").coerceAtLeast(0))
                },
                entities("topSongs"), entities("topArtists"), entities("topAlbums"),
                dimensions("qualities"), dimensions("providers"),
            )
        }
    }
}
internal data class NativeStatisticsState(
    val range: String = "30-days", val snapshot: NativeStatisticsSnapshot? = null,
    val loading: Boolean = false, val clearing: Boolean = false, val error: Boolean = false,
)

@UnstableApi
@Composable
internal fun NativeStatistics(model: NativeViewModel) {
    val state by model.statistics.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.loadStatistics() }
    val ranges = listOf("7-days" to R.string.native_stats_7_days,
        "30-days" to R.string.native_stats_30_days, "365-days" to R.string.native_stats_365_days,
        "all-time" to R.string.native_stats_all_time)
    LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.native_statistics), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranges.forEach { (range, label) ->
                FilterChip(state.range == range, { model.loadStatistics(range) },
                    label = { Text(stringResource(label)) }, enabled = !state.clearing,
                    modifier = Modifier.testTag("statistics-" + range))
            }
        } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error) item { Column {
            Text(stringResource(R.string.native_load_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { model.loadStatistics() }) { Text(stringResource(R.string.native_refresh)) }
        } }
        val snapshot = state.snapshot
        if (snapshot != null) {
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticMetric(R.string.native_stats_listening, formatTime(snapshot.listeningMs))
                StatisticMetric(R.string.native_stats_plays, snapshot.playCount.toString())
                StatisticMetric(R.string.native_stats_completed, snapshot.completed.toString())
                StatisticMetric(R.string.native_stats_skip_rate,
                    String.format(Locale.ROOT, "%.1f%%", snapshot.skipRate * 100))
            } }
            if (snapshot.recordCount == 0L) item {
                Text(stringResource(R.string.native_stats_empty), modifier = Modifier.testTag("statistics-empty"))
            } else {
                if (snapshot.daily.isNotEmpty()) item {
                    Text(stringResource(R.string.native_stats_trend), style = MaterialTheme.typography.titleLarge)
                    val max = snapshot.daily.maxOf { it.listenedMs }.coerceAtLeast(1)
                    snapshot.daily.takeLast(31).forEach { day ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(day.startMs)),
                                modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress = { (day.listenedMs.toFloat() / max.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f))
                            Text(formatTime(day.listenedMs), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item { Text(stringResource(R.string.native_top_songs), style = MaterialTheme.typography.titleLarge) }
                items(snapshot.songs) { StatisticEntry(it, "song", model) }
                item { Text(stringResource(R.string.native_stats_artists), style = MaterialTheme.typography.titleLarge) }
                items(snapshot.artists) { StatisticEntry(it, "artist", model) }
                item { Text(stringResource(R.string.native_stats_albums), style = MaterialTheme.typography.titleLarge) }
                items(snapshot.albums) { StatisticEntry(it, "album", model) }
                item { StatisticDimensions(R.string.native_stats_qualities, snapshot.qualities) }
                item { StatisticDimensions(R.string.native_stats_providers, snapshot.providers) }
            }
            item { Text(stringResource(R.string.native_stats_storage, readableBytes(snapshot.databaseBytes))) }
            item { OutlinedButton(enabled = !state.clearing, onClick = { confirmClear = true }) {
                Text(stringResource(R.string.native_stats_clear))
            } }
        }
    }
    if (confirmClear) Confirm(stringResource(R.string.native_stats_confirm_clear),
        close = { confirmClear = false }) { model.clearStatistics() }
}

@Composable
private fun StatisticMetric(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label)); Text(value, fontWeight = FontWeight.SemiBold)
    }
}
@UnstableApi
@Composable
private fun StatisticEntry(item: StatisticsEntity, kind: String, model: NativeViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { model.navigate(NativeRoute(kind, item.id)) },
            enabled = item.providerId == "qqmusic" && item.id.isNotBlank(), modifier = Modifier.weight(1f)) {
            Column {
                Text(item.title.ifBlank { item.id })
                if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(formatTime(item.listenedMs), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun StatisticDimensions(title: Int, items: List<StatisticsDimension>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.label.ifBlank { stringResource(R.string.native_unknown) })
                Text(formatTime(item.listenedMs))
            }
        }
    }
}
internal fun readableBytes(value: Long): String {
    val safe = value.coerceAtLeast(0).toDouble()
    return when {
        safe >= 1024 * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", safe / (1024 * 1024 * 1024))
        safe >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", safe / (1024 * 1024))
        safe >= 1024 -> String.format(Locale.ROOT, "%.1f KB", safe / 1024)
        else -> safe.toLong().toString() + " B"
    }
}
