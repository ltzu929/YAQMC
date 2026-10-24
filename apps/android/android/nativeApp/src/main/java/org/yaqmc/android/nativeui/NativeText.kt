package org.yaqmc.android.nativeui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.yaqmc.android.R

/** Resource references stay unresolved in cached pages so a language change needs no network reload. */
internal data class NativeText(val value: String = "", @param:StringRes val resource: Int = 0, val arguments: List<Any> = emptyList()) {
    constructor(@StringRes resource: Int, vararg arguments: Any) : this(resource = resource, arguments = arguments.toList())
    fun resolve(context: Context): String = if (resource == 0) value else context.getString(resource, *arguments.toTypedArray())
    @Composable fun resolve(): String = resolve(LocalContext.current)
}

internal fun titleFor(kind: String): NativeText = NativeText(when (kind) {
    "home" -> R.string.native_home; "discover" -> R.string.native_discover
    "search" -> R.string.native_search; "library" -> R.string.native_library
    "favorites" -> R.string.native_favorites; "account-playlists" -> R.string.native_my_playlists
    "recent" -> R.string.native_recent; "statistics" -> R.string.native_statistics
    "settings" -> R.string.native_settings; "album" -> R.string.native_albums
    "artist" -> R.string.native_artists; "playlist", "account-playlist" -> R.string.native_playlists
    "song" -> R.string.native_songs; "player" -> R.string.native_now_playing
    "queue" -> R.string.native_queue; else -> R.string.native_music
})

internal val nativeRootDestinations = listOf("home", "discover", "library", "search")
internal fun selectedRoot(kind: String): String = when (kind) {
    "favorites", "account-playlists", "account-playlist", "recent", "statistics" -> "library"
    else -> kind
}
