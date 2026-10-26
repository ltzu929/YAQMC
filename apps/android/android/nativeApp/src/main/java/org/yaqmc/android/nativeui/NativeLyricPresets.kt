package org.yaqmc.android.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.yaqmc.android.R

@Composable
internal fun nativePresetName(preset: NativeLyricPreset): String = when (preset.id) {
    "builtin.classic" -> stringResource(R.string.native_preset_classic)
    "builtin.immersive" -> stringResource(R.string.native_preset_immersive)
    "builtin.vinyl" -> stringResource(R.string.native_preset_vinyl)
    else -> preset.name.ifBlank { stringResource(R.string.native_preset_custom) }
}

/** Offline fixture: never requests provider data and never touches the player's queue. */
@Composable
internal fun NativeLyricPresetPreview(preset: NativeLyricPreset) {
    Surface(Modifier.fillMaxWidth().height(198.dp).testTag("lyric-preset-preview"),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        val title = stringResource(R.string.native_preset_sample_title)
        val artist = stringResource(R.string.native_preset_sample_artist)
        val sample = stringResource(R.string.native_preset_sample_lyric)
        val lyrics: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.Center) {
                Text(sample, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.native_preset_sample_second), style = MaterialTheme.typography.bodySmall)
            }
        }
        val cover: @Composable () -> Unit = {
            Box(Modifier.size((66f * preset.artworkScale).dp)
                .clip(if (preset.layout == "vinyl") CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("♫", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (preset.layout == "immersive") {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(preset.lyricsFraction), contentAlignment = Alignment.Center) { lyrics() }
                Row(Modifier.fillMaxWidth().weight(1f - preset.lyricsFraction),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    cover()
                    Column { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(artist, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f - preset.lyricsFraction), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cover()
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(artist, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.weight(preset.lyricsFraction), contentAlignment = Alignment.Center) { lyrics() }
            }
        }
    }
}

@Composable
internal fun NativeLyricPresets(settings: NativeLyricPresetPreferences, change: (NativeLyricPresetPreferences) -> Unit) {
    val active = settings.current()
    var draft by remember(active) { mutableStateOf(active) }
    var confirmDelete by remember(active.id) { mutableStateOf(false) }
    val builtin = active.id.startsWith("builtin.")
    val copyLabel = stringResource(R.string.native_preset_copy_suffix)
    val activeName = nativePresetName(active)
    LazyColumn(
        Modifier.fillMaxSize().testTag("lyric-presets"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.native_preset_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(stringResource(R.string.native_preset_select), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (nativeBuiltinLyricPresets + settings.normalized().custom).forEach { preset ->
                    FilterChip(settings.selectedId == preset.id, onClick = { change(settings.select(preset.id)) },
                        label = { Text(nativePresetName(preset)) }, modifier = Modifier.testTag("preset-${preset.id}"))
                }
            }
        }
        item { NativeLyricPresetPreview(draft) }
        item {
            if (!builtin) OutlinedTextField(draft.name, onValueChange = { draft = draft.copy(name = it.take(48)) },
                label = { Text(stringResource(R.string.native_preset_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PreferenceChoices(stringResource(R.string.native_preset_layout), draft.layout, listOf(
                "classic" to stringResource(R.string.native_preset_classic),
                "immersive" to stringResource(R.string.native_preset_immersive),
                "vinyl" to stringResource(R.string.native_preset_vinyl),
            )) { draft = draft.copy(layout = it) }
            PreferenceSlider(stringResource(R.string.native_preset_lyrics_share), draft.lyricsFraction, 0.35f..0.75f,
                "${(draft.lyricsFraction * 100).toInt()}%") { draft = draft.copy(lyricsFraction = it) }
            PreferenceSlider(stringResource(R.string.native_preset_artwork_size), draft.artworkScale, 0.7f..1.35f,
                "${(draft.artworkScale * 100).toInt()}%") { draft = draft.copy(artworkScale = it) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { change(settings.save(draft)) }, enabled = draft.normalized() != active,
                    modifier = Modifier.testTag("preset-save")) { Text(stringResource(R.string.native_preset_apply)) }
                OutlinedButton(onClick = { draft = active }, enabled = draft != active) { Text(stringResource(R.string.native_cancel)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    change(settings.duplicate("custom.${UUID.randomUUID()}", activeName + " " + copyLabel))
                }, enabled = settings.custom.size < 24, modifier = Modifier.testTag("preset-duplicate")) {
                    Text(stringResource(R.string.native_preset_duplicate))
                }
                if (builtin && active.id in settings.overrides) {
                    OutlinedButton(onClick = { change(settings.reset()) }, modifier = Modifier.testTag("preset-reset")) {
                        Text(stringResource(R.string.native_preset_reset))
                    }
                }
                if (!builtin) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("preset-delete")) {
                    Text(stringResource(R.string.native_preset_delete))
                }
            }
            Text(stringResource(R.string.native_preset_scope), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.native_preset_delete)) },
        text = { Text(nativePresetName(active)) },
        confirmButton = { TextButton(onClick = { change(settings.deleteCustom()); confirmDelete = false }) {
            Text(stringResource(R.string.native_preset_delete))
        } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.native_cancel)) } })
}
