package org.yaqmc.android.nativeui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.yaqmc.android.R

@Composable
internal fun NativeLyricSettings(settings: NativeLyricPreferences, change: (NativeLyricPreferences) -> Unit, openPresets: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("lyric-settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(stringResource(R.string.native_lyric_settings), style = MaterialTheme.typography.headlineMedium) }
        item { OutlinedButton(onClick = openPresets, modifier = Modifier.testTag("open-lyric-presets")) { Text(stringResource(R.string.native_preset_title)) } }
        item { PreferenceChoices(stringResource(R.string.native_lyric_translation), settings.translation, secondaryChoices()) { change(settings.copy(translation = it)) } }
        item { PreferenceChoices(stringResource(R.string.native_lyric_romanization), settings.romanization, secondaryChoices()) { change(settings.copy(romanization = it)) } }
        item {
            PreferenceSlider(stringResource(R.string.native_lyric_offset), settings.timingOffsetMs.toFloat(), -2000f..2000f, "${settings.timingOffsetMs} ms", steps = 79) { change(settings.copy(timingOffsetMs = it.toLong())) }
            Text(stringResource(R.string.native_lyric_offset_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { change(settings.copy(timingOffsetMs = settings.timingOffsetMs - 50)) }) { Text("−50 ms") }
                OutlinedButton(onClick = { change(settings.copy(timingOffsetMs = 0)) }) { Text(stringResource(R.string.native_reset)) }
                OutlinedButton(onClick = { change(settings.copy(timingOffsetMs = settings.timingOffsetMs + 50)) }) { Text("+50 ms") }
            }
        }
        item { PreferenceChoices(stringResource(R.string.native_lyric_word_effect), settings.wordEffect, listOf("fill" to stringResource(R.string.native_lyric_fill), "jump" to stringResource(R.string.native_lyric_jump))) { change(settings.copy(wordEffect = it)) } }
        item { PreferenceSlider(stringResource(R.string.native_lyric_font_scale), settings.fontScale, 0.7f..1.45f, "${(settings.fontScale * 100).toInt()}%") { change(settings.copy(fontScale = it)) } }
        item { PreferenceChoices(stringResource(R.string.native_lyric_font_weight), settings.fontWeight.toString(), (400..900 step 100).map { it.toString() to it.toString() }) { change(settings.copy(fontWeight = it.toInt())) } }
        item { PreferenceSlider(stringResource(R.string.native_lyric_line_height), settings.lineHeight, 1.05f..1.6f, String.format(Locale.ROOT, "%.2f", settings.lineHeight)) { change(settings.copy(lineHeight = it)) } }
        item { PreferenceSwitch(stringResource(R.string.native_lyric_spring), settings.spring) { change(settings.copy(spring = it)) } }
        item { PreferenceSwitch(stringResource(R.string.native_lyric_scale), settings.scale) { change(settings.copy(scale = it)) } }
        item {
            PreferenceSwitch(stringResource(R.string.native_lyric_blur), settings.blur, enabled = Build.VERSION.SDK_INT >= 31) { change(settings.copy(blur = it)) }
            if (Build.VERSION.SDK_INT < 31) Text(stringResource(R.string.native_lyric_blur_version), style = MaterialTheme.typography.bodySmall)
        }
        item { PreferenceSwitch(stringResource(R.string.native_lyric_hide_passed), settings.hidePassed) { change(settings.copy(hidePassed = it)) } }
        item { PreferenceSlider(stringResource(R.string.native_lyric_fade), settings.fadeWidth, 0.05f..1f, String.format(Locale.ROOT, "%.2f", settings.fadeWidth)) { change(settings.copy(fadeWidth = it)) } }
        item { OutlinedButton(onClick = { change(NativeLyricPreferences()) }) { Text(stringResource(R.string.native_lyric_reset)) } }
    }
}

@Composable
internal fun secondaryChoices() = listOf("auto" to stringResource(R.string.native_automatic), "show" to stringResource(R.string.native_show), "hide" to stringResource(R.string.native_hide))

@Composable
internal fun PreferenceChoices(title: String, selected: String, options: List<Pair<String, String>>, change: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) -> FilterChip(selected == value, { change(value) }, label = { Text(label) }) }
        }
    }
}

@Composable
internal fun PreferenceSwitch(title: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f)); Switch(checked, change, enabled = enabled)
    }
}

@Composable
internal fun PreferenceSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, steps: Int = 0, change: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(label, style = MaterialTheme.typography.labelLarge) }
        Slider(draft, { draft = it }, valueRange = range, steps = steps, onValueChangeFinished = { change(draft) })
    }
}
