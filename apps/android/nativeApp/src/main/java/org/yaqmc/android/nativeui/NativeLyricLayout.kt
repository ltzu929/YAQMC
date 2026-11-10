package org.yaqmc.android.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Relative x displacement tracks the control-pane width, while y uses a stable 160dp axis.
 * Preview and player use the same displacement relative to each layer's content pane.
 */
internal fun Modifier.nativeHeaderOffset(
    preset: NativeLyricPreset, layer: NativeLyricHeaderLayer, paneWidth: Dp,
): Modifier {
    val (x, y) = preset.headerOffset(layer)
    return offset(x = paneWidth * x, y = 160.dp * y)
}

/** Reserve the scaled size in the scroll container so edge controls remain reachable. */
internal fun Modifier.nativeTransportTransform(preset: NativeLyricPreset, paneWidth: Dp): Modifier =
    nativeHeaderOffset(preset, NativeLyricHeaderLayer.Transport, paneWidth)
        .widthIn(max = paneWidth * (1f - 2f * kotlin.math.abs(preset.transportOffsetX)))
        .layout { measurable, constraints ->
            val scale = preset.transportScale
            fun unscale(value: Int) = if (value == Constraints.Infinity) value else (value / scale).roundToInt()
            val child = measurable.measure(Constraints(
                unscale(constraints.minWidth), unscale(constraints.maxWidth),
                unscale(constraints.minHeight), unscale(constraints.maxHeight)))
            layout((child.width * scale).roundToInt(), (child.height * scale).roundToInt()) {
                child.placeWithLayer(0, 0) {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }

/** Shared partitions for the live player and its offline sample preview. */
@Composable
internal fun NativeLyricLayout(
    preset: NativeLyricPreset,
    wide: Boolean,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
    lyrics: @Composable (Modifier) -> Unit,
) {
    // The same transform is used by the offline editor and the real player.
    // Keep weight/scroll constraints intact so navigation and follow-scrolling still work.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val paneWidth = if (wide && preset.layout != "immersive") maxWidth * preset.lyricsFraction else maxWidth
        if (preset.layout == "immersive") {
            Column(Modifier.fillMaxSize()) {
                lyrics(Modifier.weight(preset.lyricsFraction)
                    .nativeHeaderOffset(preset, NativeLyricHeaderLayer.Lyrics, paneWidth)
                    .graphicsLayer { scaleX = preset.lyricsScale; scaleY = preset.lyricsScale })
                Column(Modifier.weight(1f - preset.lyricsFraction).verticalScroll(rememberScrollState())
                    .testTag("player-controls")) { controls() }
            }
        } else if (wide) Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f - preset.lyricsFraction).fillMaxHeight()
                .verticalScroll(rememberScrollState()).testTag("player-controls")) { controls() }
            lyrics(Modifier.weight(preset.lyricsFraction)
                .nativeHeaderOffset(preset, NativeLyricHeaderLayer.Lyrics, paneWidth)
                .graphicsLayer { scaleX = preset.lyricsScale; scaleY = preset.lyricsScale })
        } else {
            val maxControlsHeight = maxHeight * (0.72f - (preset.lyricsFraction - 0.55f) * 0.5f)
            Column {
                Column(Modifier.heightIn(max = maxControlsHeight).verticalScroll(rememberScrollState())
                    .testTag("player-controls")) { controls() }
                lyrics(Modifier.weight(1f)
                    .nativeHeaderOffset(preset, NativeLyricHeaderLayer.Lyrics, paneWidth)
                    .graphicsLayer { scaleX = preset.lyricsScale; scaleY = preset.lyricsScale })
            }
        }
    }
}
