package org.yaqmc.android.nativeui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yaqmc.android.R

internal fun nativeColor(hex: String) = Color((0xff000000L or nativeHex(hex).drop(1).toLong(16)).toInt())

internal fun nativeColorScheme(settings: NativeAppearancePreferences, dark: Boolean): ColorScheme {
    val base = if (settings.background == "color") settings.backgroundColor else if (dark) "#11120F" else "#F5F5F1"
    val surface = if (dark) "#181A16" else "#FFFFFF"
    val primary = nativeReadableAccent(settings.primary, base)
    val secondary = nativeReadableAccent(settings.secondary, base)
    val defaults = if (dark) darkColorScheme() else lightColorScheme()
    return defaults.copy(primary = nativeColor(primary), onPrimary = nativeColor(nativeForeground(primary)),
        primaryContainer = nativeColor(settings.primary), onPrimaryContainer = nativeColor(nativeForeground(settings.primary)),
        secondary = nativeColor(secondary), onSecondary = nativeColor(nativeForeground(secondary)),
        secondaryContainer = nativeColor(mixNativeHex(surface, settings.secondary, if (dark) 0.3f else 0.18f)),
        onSecondaryContainer = nativeColor(nativeForeground(mixNativeHex(surface, settings.secondary, if (dark) 0.3f else 0.18f))),
        background = nativeColor(base), onBackground = nativeColor(nativeForeground(base)),
        surface = nativeColor(surface).copy(alpha = settings.surfaceAlpha), onSurface = nativeColor(if (dark) "#F0F2E9" else "#1D201A"),
        surfaceVariant = nativeColor(mixNativeHex(surface, settings.secondary, 0.08f)).copy(alpha = settings.surfaceAlpha),
        onSurfaceVariant = nativeColor(if (dark) "#C5C8BC" else "#54584C"))
}

internal fun nativeTypography(family: String): Typography {
    val font = when (family) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val t = Typography()
    return Typography(displayLarge = t.displayLarge.copy(fontFamily = font), displayMedium = t.displayMedium.copy(fontFamily = font), displaySmall = t.displaySmall.copy(fontFamily = font),
        headlineLarge = t.headlineLarge.copy(fontFamily = font), headlineMedium = t.headlineMedium.copy(fontFamily = font), headlineSmall = t.headlineSmall.copy(fontFamily = font),
        titleLarge = t.titleLarge.copy(fontFamily = font), titleMedium = t.titleMedium.copy(fontFamily = font), titleSmall = t.titleSmall.copy(fontFamily = font),
        bodyLarge = t.bodyLarge.copy(fontFamily = font), bodyMedium = t.bodyMedium.copy(fontFamily = font), bodySmall = t.bodySmall.copy(fontFamily = font),
        labelLarge = t.labelLarge.copy(fontFamily = font), labelMedium = t.labelMedium.copy(fontFamily = font), labelSmall = t.labelSmall.copy(fontFamily = font))
}

@Composable
internal fun NativeBackdrop(settings: NativeAppearancePreferences, artwork: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val assets = remember(context.filesDir) { NativeBackgroundAssets(File(context.filesDir, "native-backgrounds")) }
    val image = when (settings.background) { "image" -> assets.resolve(settings.image); "artwork" -> artwork; else -> null }
    val base = if (settings.background in setOf("color", "image")) nativeColor(settings.backgroundColor) else MaterialTheme.colorScheme.background
    Box(modifier.background(base)) {
        if (image != null) AsyncImage(image, null, Modifier.fillMaxSize().blur(if (settings.background == "artwork") 30.dp else 0.dp),
            alpha = settings.influence / 100f, contentScale = if (settings.fit == "contain" && settings.background == "image") ContentScale.Fit else ContentScale.Crop)
        if (image != null) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.74f)))
    }
}

@Composable
internal fun NativeAppearanceSettings(
    settings: NativeAppearancePreferences, dark: Boolean, change: (NativeAppearancePreferences) -> Unit, reset: () -> Unit, notify: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(settings)
    val assets = remember(context.filesDir) { NativeBackgroundAssets(File(context.filesDir, "native-backgrounds")) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val reference = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use(assets::import) }
                change(current.copy(image = reference, background = "image"))
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { notify(R.string.native_background_import_failed) }
            finally { importing = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("appearance-settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(stringResource(R.string.native_appearance), style = MaterialTheme.typography.headlineMedium) }
        item { PreferenceSlider(stringResource(R.string.native_interface_scale), settings.fontScale.toFloat(), 80f..130f, "${settings.fontScale}%", steps = 49) { change(settings.copy(fontScale = it.toInt())) } }
        item { PreferenceChoices(stringResource(R.string.native_interface_font), settings.fontFamily, listOf(
            "application" to stringResource(R.string.native_font_application), "system" to stringResource(R.string.native_system),
            "serif" to stringResource(R.string.native_font_serif), "monospace" to stringResource(R.string.native_font_monospace))) { change(settings.copy(fontFamily = it)) } }
        item { PreferenceChoices(stringResource(R.string.native_palette), settings.palette, nativePalettes.map { it.id to stringResource(paletteLabel(it.id)) } + ("custom" to stringResource(R.string.native_custom))) { change(settings.withPalette(it)) } }
        item { NativeColorControl(stringResource(R.string.native_primary_color), settings.primary, dark, { settings.copy(primary = it, palette = "custom") }) { change(settings.copy(primary = it, palette = "custom")) } }
        item { NativeColorControl(stringResource(R.string.native_secondary_color), settings.secondary, dark, { settings.copy(secondary = it, palette = "custom") }) { change(settings.copy(secondary = it, palette = "custom")) } }
        item { AppearancePreview(settings, dark) }
        item { PreferenceChoices(stringResource(R.string.native_background), settings.background, listOf(
            "default" to stringResource(R.string.native_background_default), "artwork" to stringResource(R.string.native_background_artwork),
            "color" to stringResource(R.string.native_background_color), "image" to stringResource(R.string.native_background_image))) { change(settings.copy(background = it)) } }
        if (settings.background == "color" || settings.background == "image") item {
            NativeColorControl(stringResource(R.string.native_background_color), settings.backgroundColor, dark, { settings.copy(backgroundColor = it) }) { change(settings.copy(backgroundColor = it)) }
        }
        if (settings.background == "image") {
            item {
                OutlinedButton(enabled = !importing, onClick = { picker.launch("image/*") }) { Text(stringResource(if (settings.image.isBlank()) R.string.native_choose_image else R.string.native_replace_image)) }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(stringResource(if (assets.resolve(settings.image) == null) R.string.native_background_missing else R.string.native_background_ready), style = MaterialTheme.typography.bodySmall)
            }
            item { PreferenceChoices(stringResource(R.string.native_background_fit), settings.fit, listOf("cover" to stringResource(R.string.native_fit_cover), "contain" to stringResource(R.string.native_fit_contain))) { change(settings.copy(fit = it)) } }
        }
        if (settings.background in setOf("image", "artwork")) item { PreferenceSlider(stringResource(R.string.native_background_influence), settings.influence.toFloat(), 0f..100f, "${settings.influence}%") { change(settings.copy(influence = it.toInt())) } }
        item { PreferenceSlider(stringResource(R.string.native_surface_opacity), settings.opacity.toFloat(), 85f..100f, "${settings.opacity}%") { change(settings.copy(opacity = it.toInt())) } }
        item { PreferenceChoices(stringResource(R.string.native_material), settings.material, listOf("opaque" to stringResource(R.string.native_material_opaque), "translucent" to stringResource(R.string.native_material_translucent))) { change(settings.copy(material = it)) } }
        item { OutlinedButton(enabled = !importing, onClick = { resetConfirm = true }) { Text(stringResource(R.string.native_appearance_reset)) } }
    }
    if (resetConfirm) Confirm(stringResource(R.string.native_appearance_reset), { resetConfirm = false }, reset)
}

@Composable
private fun AppearancePreview(settings: NativeAppearancePreferences, dark: Boolean) {
    MaterialTheme(colorScheme = nativeColorScheme(settings, dark), typography = nativeTypography(settings.fontFamily)) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.native_preview), style = MaterialTheme.typography.titleLarge)
                Text("YAQMC · 音乐 / Music", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text(stringResource(R.string.native_play)) }
                    FilterChip(true, {}, label = { Text(stringResource(R.string.native_favorite)) })
                }
            }
        }
    }
}

@Composable
private fun NativeColorControl(title: String, color: String, dark: Boolean, preview: (String) -> NativeAppearancePreferences, change: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { editing = true }) {
        Box(Modifier.size(20.dp).background(nativeColor(color))); Spacer(Modifier.width(8.dp)); Text("$title · $color")
    }
    if (editing) {
        var draft by remember(color) { mutableStateOf(color) }
        val valid = validNativeHex(draft)
        NativeAlertDialog(onDismissRequest = { editing = false }, title = { Text(title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(draft, { draft = it.take(16) }, singleLine = true, label = { Text("#RRGGBB") }, isError = !valid)
                if (valid) AppearancePreview(preview(nativeHex(draft)), dark)
            }
        }, confirmButton = { TextButton(enabled = valid, onClick = { change(nativeHex(draft)); editing = false }) { Text(stringResource(R.string.native_confirm)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.native_cancel)) } })
    }
}

private fun paletteLabel(id: String): Int = when (id) {
    "ember" -> R.string.native_palette_ember; "ocean" -> R.string.native_palette_ocean; "violet" -> R.string.native_palette_violet
    "sakura" -> R.string.native_palette_sakura; "mint" -> R.string.native_palette_mint; "mono" -> R.string.native_palette_mono
    else -> R.string.native_background_default
}
