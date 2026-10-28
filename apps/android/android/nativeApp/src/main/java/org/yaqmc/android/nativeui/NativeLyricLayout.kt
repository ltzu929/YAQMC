package org.yaqmc.android.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Relative x displacement tracks the control-pane width, while y uses a stable 160dp axis.
 * A shared transform guarantees preview/live agreement without moving transport controls.
 */
internal fun Modifier.nativeHeaderOffset(
    preset: NativeLyricPreset, layer: NativeLyricHeaderLayer, paneWidth: Dp,
): Modifier {
    val (x, y) = preset.headerOffset(layer)
    return offset(x = paneWidth * x, y = 160.dp * y)
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
        val paneWidth = maxWidth
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
