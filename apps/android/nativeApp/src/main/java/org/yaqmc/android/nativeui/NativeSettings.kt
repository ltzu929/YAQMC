package org.yaqmc.android.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import org.yaqmc.android.R

@UnstableApi
@Composable
internal fun Settings(model: NativeViewModel) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val quality by model.quality.collectAsStateWithLifecycle()
    val qualityBusy by model.qualityBusy.collectAsStateWithLifecycle()
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var qualityDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { SettingsSection(stringResource(R.string.native_settings_playback)) }
        item { NativeSettingRow(stringResource(R.string.native_default_quality), stringResource(NativePlayback.qualityLabel(quality)), "settings-quality", !qualityBusy) { qualityDialog = true } }
        item { NativeSettingRow(stringResource(R.string.native_lyric_settings), tag = "settings-lyrics") { model.navigate(NativeRoute("lyric-settings")) } }
        item { SettingsSection(stringResource(R.string.native_settings_personalization)) }
        item { NativeSettingRow(stringResource(R.string.native_appearance), nativeThemeLabel(preferences.theme), "settings-appearance") { model.navigate(NativeRoute("appearance-settings")) } }
        item { NativeSettingRow(stringResource(R.string.native_language), stringResource(when (preferences.language) {
            "zh-CN" -> R.string.native_chinese; "en-US" -> R.string.native_english; else -> R.string.native_system
        }), "settings-language") { languageDialog = true } }
        item { SettingsSection(stringResource(R.string.native_settings_maintenance)) }
        item { NativeSettingRow(stringResource(R.string.native_cache_breakdown), tag = "settings-storage") { model.navigate(NativeRoute("storage-settings")) } }
        item { NativeSettingRow(stringResource(R.string.native_diag_title), tag = "settings-diagnostics") { model.navigate(NativeRoute("diagnostics")) } }
        item { NativeSettingRow(stringResource(R.string.native_about_title), tag = "settings-about") { model.navigate(NativeRoute("about")) } }
    }
    if (languageDialog) NativeChoiceDialog(stringResource(R.string.native_language), preferences.language,
        listOf("system" to stringResource(R.string.native_system), "zh-CN" to stringResource(R.string.native_chinese), "en-US" to stringResource(R.string.native_english)),
        "language", { languageDialog = false }, model::setLanguage)
    if (qualityDialog) NativeAlertDialog(onDismissRequest = { qualityDialog = false }, title = { Text(stringResource(R.string.native_default_quality)) },
        text = { Column { QualityChoices(quality, null) { qualityDialog = false; model.changeQuality(it) } } },
        confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text(stringResource(R.string.native_cancel)) } })
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun NativeSettingRow(title: String, summary: String = "", tag: String = "", enabled: Boolean = true, click: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = if (summary.isBlank()) null else ({ Text(summary) }),
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(enabled = enabled, onClick = click))
}

@Composable
internal fun nativeThemeLabel(theme: String): String = stringResource(when (theme) {
    "light" -> R.string.native_light; "dark" -> R.string.native_dark; else -> R.string.native_system
})

@Composable
internal fun NativeChoiceDialog(title: String, current: String, choices: List<Pair<String, String>>, tag: String, close: () -> Unit, select: (String) -> Unit) {
    NativeAlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup()) {
            choices.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(current == value, role = Role.RadioButton, onClick = { select(value); close() })
                    .testTag("$tag-$value"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = current == value, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.native_cancel)) } })
}

@UnstableApi
@Composable
internal fun StorageSettings(model: NativeViewModel) {
    val cache by model.cache.collectAsStateWithLifecycle()
    val busy by model.cacheBusy.collectAsStateWithLifecycle()
    val error by model.cacheError.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.cacheStats() }
    LazyColumn(Modifier.fillMaxSize().testTag("storage-settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error) item { Text(stringResource(R.string.native_load_failed), color = MaterialTheme.colorScheme.error) }
        if (cache == null && !error) item { Text(stringResource(R.string.native_cache_loading)) }
        cache?.let { details ->
            item { Text(stringResource(R.string.native_cache_total, readableBytes(details.totalBytes)), style = MaterialTheme.typography.titleLarge) }
            item { Text(stringResource(R.string.native_cache_media, details.mediaEntries, readableBytes(details.mediaBytes))) }
            item { Text(stringResource(R.string.native_cache_artwork, details.artworkEntries, readableBytes(details.artworkBytes))) }
            item { Text(stringResource(R.string.native_cache_metadata, details.metadataEntries)) }
            item { Text(stringResource(R.string.native_cache_lyrics, details.lyricEntries)) }
            item { Text(stringResource(R.string.native_cache_limits, readableBytes(details.mediaLimitBytes), readableBytes(details.artworkLimitBytes)), style = MaterialTheme.typography.bodySmall) }
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.cacheStats() }, enabled = !busy) { Text(stringResource(R.string.native_refresh)) }
            TextButton(onClick = { confirmClear = true }, enabled = !busy) { Text(stringResource(R.string.native_clear_cache)) }
        } }
    }
    if (confirmClear) Confirm(stringResource(R.string.native_confirm_clear_cache), { confirmClear = false }) { model.cacheStats(clear = true) }
}
