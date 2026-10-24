package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.yaqmc.android.R

/** Uses the same provider methods as the old Android client, with no duplicate business cache. */
internal class NativeCatalogRepository(private val request: suspend (String, JSONObject) -> JSONObject) {
    suspend fun load(target: NativeRoute, refresh: Boolean, number: Int, cursor: String?): CatalogPage {
        fun provider() = JSONObject().put("providerId", "qqmusic")
        return when (target.kind) {
            "home" -> NativeModels.home(request("provider_home", provider().put("refresh", refresh)))
            "discover" -> NativeModels.discover(request("provider_discover", provider().put("refresh", refresh)))
            "search" -> if (target.query.isBlank()) {
                val home = request("provider_home", provider().put("refresh", refresh))
                CatalogPage(titleFor("search"), NativeText(R.string.native_search_hint), sections = listOf(
                    FeedSection(NativeText(R.string.native_recommended_playlists), NativeModels.cards(home.optJSONArray("madeForYou"), "playlist"))))
            } else {
                val value = request("provider_search", provider().put("query", target.query).put("kind", target.searchKind).put("page", number).put("limit", 30))
                val section = if (target.searchKind == "song") FeedSection(titleFor("song"), tracks = NativeModels.tracks(value.optJSONArray("items")))
                    else FeedSection(titleFor(target.searchKind), NativeModels.cards(value.optJSONArray("items"), target.searchKind))
                CatalogPage(titleFor("search"), NativeText(target.query), sections = listOf(section), hasMore = value.optBoolean("hasMore"))
            }
            "favorites", "recent", "account-playlists" -> {
                val method = when (target.kind) { "favorites" -> "provider_favorite_songs"; "recent" -> "provider_account_recently_played"; else -> "provider_account_playlists" }
                val value = request(method, provider().put("cursor", cursor ?: JSONObject.NULL).put("limit", 50))
                val section = when (target.kind) {
                    "account-playlists" -> FeedSection(titleFor("playlist"), NativeModels.cards(value.optJSONArray("items"), "account-playlist"))
                    "recent" -> FeedSection(titleFor("song"), tracks = value.optJSONArray("items").objects().mapNotNull { it.optJSONObject("song")?.let(::Track) })
                    else -> FeedSection(titleFor("song"), tracks = NativeModels.tracks(value.optJSONArray("items")))
                }
                val next = value.text("nextCursor").takeIf(String::isNotBlank)
                CatalogPage(titleFor(target.kind), sections = listOf(section), cursor = next, hasMore = next != null,
                    total = if (value.isNull("total")) null else value.optInt("total"), stale = value.optBoolean("stale"), raw = value)
            }
            "account-playlist" -> NativeModels.detail(target.kind, request("provider_account_playlist_tracks",
                provider().put("playlist", JSONObject(target.playlist)).put("cursor", cursor ?: JSONObject.NULL).put("limit", 50)))
            "playlist" -> NativeModels.detail(target.kind, if (target.playlist.isNotEmpty()) JSONObject(target.playlist)
                else request("provider_playlist", provider().put("id", target.id)))
            "song" -> {
                val track = Track(request("provider_song", provider().put("id", target.id)))
                val formats = track.json.optJSONArray("audioFormats").objects().map { it.text("quality") }.ifEmpty { listOf(track.json.text("quality")) }.filter(String::isNotBlank)
                CatalogPage(if (track.title.isBlank()) NativeText(R.string.native_unknown_song) else NativeText(track.title), artwork = track.artwork,
                    sections = listOf(FeedSection(titleFor("song"), tracks = listOf(track))),
                    metadata = listOf(NativeText(R.string.native_song_metadata, track.json.optInt("trackNumber"), formatTime(track.duration), formats.joinToString(" / "))),
                    links = track.artistLinks + listOfNotNull(track.albumLink), raw = track.json)
            }
            "artist" -> {
                val artist = NativeModels.detail("artist", request("provider_artist", provider().put("id", target.id)))
                if (target.tab == "top") artist else {
                    val value = request("provider_artist_catalog", provider().put("id", target.id).put("kind", target.tab).put("page", number).put("limit", 30))
                    artist.copy(sections = listOf(if (target.tab == "song") FeedSection(NativeText(R.string.native_all_songs), tracks = NativeModels.tracks(value.optJSONArray("items")), tab = "song")
                        else FeedSection(NativeText(R.string.native_all_albums), NativeModels.cards(value.optJSONArray("items"), "album"), tab = "album")), hasMore = value.optBoolean("hasMore"))
                }
            }
            "statistics" -> {
                val value = request("statistics_snapshot", JSONObject().put("range", "30-days"))
                CatalogPage(titleFor("statistics"), NativeText(R.string.native_statistics_summary, value.optLong("qualifiedListeningMs") / 60000, value.optInt("qualifiedPlayCount")),
                    sections = listOf(FeedSection(NativeText(R.string.native_top_songs), value.optJSONArray("topSongs").objects().map { row ->
                        CatalogItem("statistic", JSONObject().put("id", row.text("id")).put("title", row.text("title")),
                            NativeText(R.string.native_statistic_minutes, row.text("subtitle"), row.optLong("listenedMs") / 60000))
                    })))
            }
            else -> NativeModels.detail(target.kind, request("provider_${target.kind}", provider().put(if (target.kind == "area") "encArea" else "id", target.id)))
        }
    }
}
