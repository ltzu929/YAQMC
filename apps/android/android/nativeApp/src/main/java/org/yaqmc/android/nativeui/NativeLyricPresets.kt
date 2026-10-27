package org.yaqmc.android.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Offline fixture using the same layout partitions as the real player.
 * Dragging edits only a local preview; the parent records one undo step on release.
 */
@Composable
internal fun NativeLyricPresetPreview(
    preset: NativeLyricPreset, wide: Boolean,
    onFractionChange: (Float) -> Unit, onArtworkScaleChange: (Float) -> Unit,
) {
    var dragging by remember(preset.id, wide, preset.layout) { mutableStateOf<Float?>(null) }
    var resizing by remember(preset.id, wide, preset.layout) { mutableStateOf<Float?>(null) }
    val displayedFraction = dragging ?: preset.lyricsFraction
    val displayed = preset.copy(lyricsFraction = displayedFraction, artworkScale = resizing ?: preset.artworkScale)
    val horizontal = wide && preset.layout != "immersive"
    val canDrag = horizontal || preset.layout == "immersive"
    val currentFraction by rememberUpdatedState(preset.lyricsFraction)
    val finishDrag by rememberUpdatedState(onFractionChange)
    val currentArtworkScale by rememberUpdatedState(preset.artworkScale)
    val finishResize by rememberUpdatedState(onArtworkScaleChange)
    val density = LocalDensity.current
    val artworkAxisPixels = with(density) { 66.dp.toPx() }.coerceAtLeast(1f)
    val dragHint = stringResource(R.string.native_preset_drag_hint)
    val resizeLabel = stringResource(R.string.native_preset_resize_handle)

    BoxWithConstraints(Modifier.fillMaxWidth()
        .then(if (wide) Modifier.aspectRatio(16f / 9f) else Modifier.height(280.dp))) {
        val axisPixels = with(density) { (if (horizontal) maxWidth else maxHeight).toPx() }.coerceAtLeast(1f)
        Surface(Modifier.fillMaxSize().testTag("lyric-preset-preview"),
            shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            val title = stringResource(R.string.native_preset_sample_title)
            val artist = stringResource(R.string.native_preset_sample_artist)
            val sample = stringResource(R.string.native_preset_sample_lyric)
            val lyrics: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.fillMaxSize().testTag("lyrics-viewport").padding(10.dp),
                    verticalArrangement = Arrangement.Center) {
                    Text(sample, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.native_preset_sample_second),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            val cover: @Composable () -> Unit = {
                Box(Modifier.testTag("preset-artwork-preview")) {
                    Box(Modifier.size((66f * displayed.artworkScale).dp)
                        .clip(if (displayed.layout == "vinyl") CircleShape else RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center) {
                        Text("♫", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    // 预览手柄复用实际播放器的 artworkScale，不引入第二套布局状态。
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp).size(28.dp)
                        .testTag("preset-artwork-resize")
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.90f))
                        .semantics { contentDescription = resizeLabel }
                        .pointerInput(artworkAxisPixels) {
                            detectDragGestures(
                                onDragStart = { resizing = currentArtworkScale },
                                onDragEnd = {
                                    val value = resizing
                                    resizing = null
                                    if (value != null) finishResize(nativeSnapArtworkScale(value))
                                },
                                onDragCancel = { resizing = null },
                            ) { pointer, delta ->
                                pointer.consume()
                                resizing = ((resizing ?: currentArtworkScale) + delta.x / artworkAxisPixels)
                                    .coerceIn(0.7f, 1.35f)
                            }
                        }, contentAlignment = Alignment.Center) {
                        Text("↔", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            NativeLyricLayout(displayed, wide, controls = {
                Column(Modifier.fillMaxWidth().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (displayed.showArtwork) cover()
                    if (displayed.showMetadata) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        Text(artist, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }, lyrics = lyrics)
        }

        if (canDrag) {
            // Row layouts allocate controls on the left. Immersive layouts allocate lyrics above.
            val handleWidth = if (horizontal) 24.dp else 80.dp
            val handleHeight = if (horizontal) 80.dp else 24.dp
            val x = if (horizontal) maxWidth * (1f - displayedFraction) - handleWidth / 2f
                else maxWidth / 2f - handleWidth / 2f
            val y = if (horizontal) maxHeight / 2f - handleHeight / 2f
                else maxHeight * displayedFraction - handleHeight / 2f
            Box(Modifier.offset(x = x, y = y).size(handleWidth, handleHeight)
                .testTag("preset-divider-handle")
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.90f))
                .semantics { contentDescription = dragHint }
                .pointerInput(horizontal, axisPixels) {
                    detectDragGestures(
                        onDragStart = { dragging = currentFraction },
                        onDragEnd = {
                            val value = dragging
                            dragging = null
                            if (value != null) finishDrag(nativeSnapLyricsFraction(value))
                        },
                        onDragCancel = { dragging = null },
                    ) { pointer, delta ->
                        pointer.consume()
                        val movement = (if (horizontal) -delta.x else delta.y) / axisPixels
                        dragging = ((dragging ?: currentFraction) + movement).coerceIn(0.35f, 0.75f)
                    }
                }, contentAlignment = Alignment.Center) {
                Text(if (horizontal) "⋮" else "━", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
internal fun NativeLyricPresets(settings: NativeLyricPresetPreferences, change: (NativeLyricPresetPreferences) -> Unit) {
    val active = settings.current()
    var edits by remember(active) { mutableStateOf(NativeLyricPresetEditHistory(active)) }
    val draft = edits.current
    val edit: (NativeLyricPreset) -> Unit = { edits = edits.edit(it) }
    var confirmDelete by remember(active.id) { mutableStateOf(false) }
    val deviceWide = LocalConfiguration.current.screenWidthDp >= 600
    var previewWide by remember(deviceWide) { mutableStateOf(deviceWide) }
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
        item {
            Text(stringResource(R.string.native_preset_preview_mode), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!previewWide, onClick = { previewWide = false },
                    label = { Text(stringResource(R.string.native_preset_preview_device)) },
                    modifier = Modifier.testTag("preset-preview-device"))
                FilterChip(previewWide, onClick = { previewWide = true },
                    label = { Text(stringResource(R.string.native_preset_preview_wide)) },
                    modifier = Modifier.testTag("preset-preview-wide"))
            }
        }
        item {
            NativeLyricPresetPreview(draft, previewWide,
                onFractionChange = { edit(draft.copy(lyricsFraction = it)) },
                onArtworkScaleChange = { edit(draft.copy(artworkScale = it)) })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edits = edits.undo() }, enabled = edits.undoSteps.isNotEmpty(),
                    modifier = Modifier.testTag("preset-undo")) {
                    Text(stringResource(R.string.native_preset_undo))
                }
                OutlinedButton(onClick = { edits = edits.redo() }, enabled = edits.redoSteps.isNotEmpty(),
                    modifier = Modifier.testTag("preset-redo")) {
                    Text(stringResource(R.string.native_preset_redo))
                }
            }
            Text(stringResource(R.string.native_preset_drag_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.native_preset_resize_hint), style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (!builtin) OutlinedTextField(draft.name,
                onValueChange = { edit(draft.copy(name = it.take(48))) },
                label = { Text(stringResource(R.string.native_preset_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            PreferenceChoices(stringResource(R.string.native_preset_layout), draft.layout, listOf(
                "classic" to stringResource(R.string.native_preset_classic),
                "immersive" to stringResource(R.string.native_preset_immersive),
                "vinyl" to stringResource(R.string.native_preset_vinyl),
            )) { edit(draft.copy(layout = it)) }
            PreferenceSlider(stringResource(R.string.native_preset_lyrics_share), draft.lyricsFraction, 0.35f..0.75f,
                "${(draft.lyricsFraction * 100).toInt()}%") { edit(draft.copy(lyricsFraction = it)) }
            PreferenceSlider(stringResource(R.string.native_preset_artwork_size), draft.artworkScale, 0.7f..1.35f,
                "${(draft.artworkScale * 100).toInt()}%") { edit(draft.copy(artworkScale = it)) }
            PreferenceSwitch(stringResource(R.string.native_preset_show_artwork), draft.showArtwork,
                modifier = Modifier.testTag("preset-artwork-visible")) { edit(draft.copy(showArtwork = it)) }
            PreferenceSwitch(stringResource(R.string.native_preset_show_metadata), draft.showMetadata,
                modifier = Modifier.testTag("preset-metadata-visible")) { edit(draft.copy(showMetadata = it)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { change(settings.save(draft)) }, enabled = draft.normalized() != active,
                    modifier = Modifier.testTag("preset-save")) {
                    Text(stringResource(R.string.native_preset_apply))
                }
                OutlinedButton(onClick = { edits = NativeLyricPresetEditHistory(active) },
                    enabled = draft != active, modifier = Modifier.testTag("preset-cancel")) {
                    Text(stringResource(R.string.native_cancel))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    change(settings.duplicate("custom.${UUID.randomUUID()}", activeName + " " + copyLabel))
                }, enabled = settings.custom.size < 24, modifier = Modifier.testTag("preset-duplicate")) {
                    Text(stringResource(R.string.native_preset_duplicate))
                }
                if (builtin && active.id in settings.overrides) {
                    OutlinedButton(onClick = { change(settings.reset()) },
                        modifier = Modifier.testTag("preset-reset")) {
                        Text(stringResource(R.string.native_preset_reset))
                    }
                }
                if (!builtin) OutlinedButton(onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("preset-delete")) {
                    Text(stringResource(R.string.native_preset_delete))
                }
            }
            Text(stringResource(R.string.native_preset_scope), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.native_preset_delete)) },
        text = { Text(nativePresetName(active)) },
        confirmButton = {
            TextButton(onClick = { change(settings.deleteCustom()); confirmDelete = false },
                modifier = Modifier.testTag("preset-confirm-delete")) {
                Text(stringResource(R.string.native_preset_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("preset-cancel-delete")) {
                Text(stringResource(R.string.native_cancel))
            }
        })
}
