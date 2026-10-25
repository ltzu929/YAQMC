package org.yaqmc.android.nativeui

import org.json.JSONArray
import org.json.JSONObject
import org.yaqmc.android.R

internal fun JSONObject.text(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

internal data class Track(val json: JSONObject) {
    val id: String = json.text("id")
    val providerId: String = json.optJSONObject("provider")?.text("providerId", "qqmusic") ?: "qqmusic"
    val title: String = json.text("title")
    val artists: String = json.optJSONArray("artists").objects().joinToString(" / ") { it.text("name") }
    val album: String = json.optJSONObject("album")?.text("title").orEmpty()
    val artwork: String = json.optJSONObject("artwork")?.text("src").orEmpty()
    val duration: Long = json.optLong("durationMs")
    val favorite: Boolean = json.optBoolean("isFavorite")
    val key: String = "$providerId:$id"
    val artistLinks: List<EntityLink> = json.optJSONArray("artists").objects().map { EntityLink("artist", it.text("id"), it.text("name")) }
    val albumLink: EntityLink? = json.optJSONObject("album")?.let { EntityLink("album", it.text("id"), it.text("title")) }
    val writable: Boolean = json.optJSONObject("provider")?.let { it.text("providerId") == "qqmusic" && it.text("trackId").isNotBlank() } == true
}

internal data class EntityLink(val kind: String, val id: String, val title: String)

internal data class CatalogItem(
    val kind: String,
    val json: JSONObject,
    val subtitleText: NativeText? = null,
    val titleText: NativeText? = null,
    val continuation: String? = null,
) {
    val id: String = json.text("id", json.text("encArea"))
    val title: String = json.text("title", json.text("name"))
    val artwork: String = json.optJSONObject("artwork")?.text("src") ?: json.text("cover")
    val subtitle: String = json.optJSONObject("artist")?.text("name")
        ?: json.optJSONObject("owner")?.text("displayName") ?: json.text("creator", json.text("subtitle", json.text("artist")))
    val playable: Boolean get() = kind in setOf("playlist", "album", "account-playlist")
    val navigable: Boolean get() = kind in setOf("playlist", "album", "artist", "area", "account-playlist")
}

internal data class FeedSection(
    val title: NativeText,
    val cards: List<CatalogItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val tab: String = "",
    val continuation: String? = null,
)

internal data class CatalogTab(val id: String, val title: NativeText)

internal data class CatalogPage(
    val title: NativeText,
    val subtitle: NativeText = NativeText(),
    val artwork: String = "",
    val sections: List<FeedSection> = emptyList(),
    val hasMore: Boolean = false,
    val cursor: String? = null,
    val tabs: List<CatalogTab> = emptyList(),
    val metadata: List<NativeText> = emptyList(),
    val links: List<EntityLink> = emptyList(),
    val raw: JSONObject = JSONObject(),
    val total: Int? = null,
    val stale: Boolean = false,
) {
    val tracks: List<Track> get() = sections.flatMap { it.tracks }
}

/** JSON is decoded only at the existing Core boundary. Track JSON is preserved for playback requests. */
internal object NativeModels {
    fun tracks(array: JSONArray?): List<Track> = array.objects().map(::Track)
    fun cards(array: JSONArray?, kind: String): List<CatalogItem> = array.objects().map { CatalogItem(kind, it) }

    fun home(value: JSONObject): CatalogPage {
        val sections = mutableListOf<FeedSection>()
        val personal = listOf("guessSonglist", "dailySonglist", "newSongSonglist").mapNotNull { key ->
            value.optJSONObject(key)?.let {
                CatalogItem("playlist", it,
                    subtitleText = if (key == "guessSonglist") NativeText(R.string.native_play_immediately) else NativeText(R.string.native_song_count, it.optJSONArray("tracks")?.length() ?: 0),
                    titleText = when (key) { "guessSonglist" -> NativeText(R.string.native_guess); "newSongSonglist" -> NativeText(R.string.native_new_song_recommend); else -> null },
                    continuation = if (key == "guessSonglist") "guess" else null)
            }
        }
        sections += FeedSection(NativeText(R.string.native_for_you), personal)
        sections += FeedSection(value.text("radarBasedOnSong").takeIf(String::isNotBlank)?.let { NativeText(R.string.native_radar_based_on, it) } ?: NativeText(R.string.native_radar), tracks = tracks(value.optJSONArray("radarSongs")), continuation = "radar")
        sections += FeedSection(NativeText(R.string.native_recommended_playlists), cards(value.optJSONArray("recommendedSonglists"), "playlist"))
        sections += FeedSection(NativeText(R.string.native_made_for_you), cards(value.optJSONArray("madeForYou"), "playlist"))
        sections += FeedSection(NativeText(R.string.native_new_albums), cards(value.optJSONArray("newReleases"), "album"))
        sections += FeedSection(NativeText(R.string.native_recent), value.optJSONArray("recentlyPlayed").objects().mapNotNull {
            collection -> collection.optJSONObject("item")?.let { item -> CatalogItem(collection.text("type"), item) }
        })
        return CatalogPage(titleFor("home"), NativeText(R.string.native_home_subtitle), sections = sections.filter { it.cards.isNotEmpty() || it.tracks.isNotEmpty() })
    }

    fun discover(value: JSONObject): CatalogPage {
        val sections = listOf(
            FeedSection(NativeText(R.string.native_featured), cards(value.optJSONArray("featured"), "display"), tab = "featured"),
            FeedSection(NativeText(R.string.native_popular_playlists), cards(value.optJSONArray("popularSonglists"), "playlist"), tab = "featured"),
            FeedSection(NativeText(R.string.native_charts), cards(value.optJSONArray("charts"), "playlist"), tab = "charts"),
            FeedSection(NativeText(R.string.native_new_songs), tracks = tracks(value.optJSONObject("newSongs")?.optJSONArray("tracks")), tab = "newSongs"),
            FeedSection(NativeText(R.string.native_new_albums), cards(value.optJSONArray("newAlbums"), "album"), tab = "newAlbums"),
            FeedSection(NativeText(R.string.native_categories), cards(value.optJSONArray("categories"), "area"), tab = "categories"),
            FeedSection(NativeText(R.string.native_new_mvs), cards(value.optJSONArray("newMvs"), "display"), tab = "newMvs"),
            FeedSection(NativeText(R.string.native_podcasts), cards(value.optJSONArray("podcasts"), "display"), tab = "podcasts"),
        ).filter { it.cards.isNotEmpty() || it.tracks.isNotEmpty() }
        val tabs = sections.distinctBy { it.tab }.map { CatalogTab(it.tab, if (it.tab == "featured") NativeText(R.string.native_featured) else it.title) }
        return CatalogPage(titleFor("discover"), NativeText(R.string.native_discover_subtitle), sections = sections, tabs = tabs)
    }

    fun detail(kind: String, value: JSONObject): CatalogPage {
        val summary = value.optJSONObject("summary") ?: value
        val trackPage = value.optJSONObject("tracks")
        val tracks = tracks(trackPage?.optJSONArray("items") ?: value.optJSONArray("tracks") ?: value.optJSONArray("topSongs"))
        val sections = mutableListOf(FeedSection(if (kind == "artist") NativeText(R.string.native_top_songs) else titleFor("song"), tracks = tracks, tab = if (kind == "artist") "top" else ""))
        if (kind == "artist") sections += FeedSection(titleFor("album"), cards(value.optJSONArray("albums"), "album"), tab = "top")
        if (kind == "area") {
            sections += FeedSection(titleFor("playlist"), cards(value.optJSONArray("songlists"), "playlist") + cards(value.optJSONArray("playlists"), "playlist"))
            sections += FeedSection(titleFor("artist"), cards(value.optJSONArray("artists"), "artist"))
        }
        val links = listOfNotNull(value.optJSONObject("artist")?.let { EntityLink("artist", it.text("id"), it.text("name")) })
        val metadata = buildList {
            if (kind in setOf("album", "playlist", "account-playlist")) {
                add(NativeText(R.string.native_track_summary, if (summary.has("trackCount")) summary.optInt("trackCount") else tracks.size, formatTime(tracks.sumOf { it.duration })))
            }
            if (kind == "album") {
                if (value.optInt("releaseYear") > 0) add(NativeText(value.optInt("releaseYear").toString()))
                value.text("genre").takeIf(String::isNotBlank)?.let { add(NativeText(it)) }
            }
            summary.optJSONObject("owner")?.text("displayName")?.takeIf(String::isNotBlank)?.let { add(NativeText(R.string.native_owner, it)) }
            val updated = summary.text("updatedLabel").ifBlank {
                if (summary.isNull("updatedAtMs")) "" else java.text.DateFormat.getDateInstance().format(java.util.Date(summary.optLong("updatedAtMs")))
            }
            if (updated.isNotBlank()) add(NativeText(R.string.native_updated, updated))
            if (kind == "account-playlist") {
                add(NativeText(ownershipLabel(summary.text("ownership"))))
                val capabilities = summary.optJSONObject("capabilities")
                add(NativeText(if (listOf("canAddTracks", "canRemoveTracks", "canRename", "canDelete").any { capabilities?.optBoolean(it) == true }) R.string.native_editable else R.string.native_read_only))
            }
        }
        return CatalogPage(NativeText(summary.text("title", summary.text("name"))), NativeText(summary.text("description")),
            summary.optJSONObject("artwork")?.text("src").orEmpty(), sections,
            hasMore = trackPage?.text("nextCursor")?.isNotEmpty() == true,
            cursor = trackPage?.text("nextCursor")?.takeIf(String::isNotEmpty),
            tabs = if (kind == "artist") listOf(CatalogTab("top", NativeText(R.string.native_top_songs)), CatalogTab("song", NativeText(R.string.native_all_songs)), CatalogTab("album", NativeText(R.string.native_all_albums))) else emptyList(),
            metadata = metadata, links = links, raw = value,
            total = if (trackPage == null || trackPage.isNull("total")) null else trackPage.optInt("total"), stale = trackPage?.optBoolean("stale") == true)
    }

    fun ownershipLabel(value: String): Int = when (value) {
        "owned" -> R.string.native_owned; "favorite" -> R.string.native_favorite_collection
        "system" -> R.string.native_system_collection; else -> R.string.native_collected
    }

    fun merge(old: CatalogPage, next: CatalogPage): CatalogPage = next.copy(
        sections = (old.sections + next.sections).groupBy { it.tab to it.title }.values.map { group ->
            group.last().copy(cards = group.flatMap { it.cards }.distinctBy { it.kind to it.id }, tracks = group.flatMap { it.tracks }.distinctBy { it.key })
        }, stale = old.stale || next.stale,
    )

    fun lyrics(value: JSONObject) = Lyrics(value.text("songId"), value.optJSONObject("metadata")?.optLong("offsetMs") ?: 0,
        value.optJSONArray("lines").objects().map { line ->
            LyricLine(if (line.isNull("startMs")) null else line.optLong("startMs"),
                if (line.isNull("endMs")) null else line.optLong("endMs"), line.text("text"),
                line.text("translation"), line.text("romanization"), line.optJSONArray("words").objects().map {
                    LyricWord(it.optLong("startMs"), it.optLong("endMs"), it.text("text"))
                })
        }, synchronized = value.text("syncMode") != "unsynchronized", wordSynchronized = value.text("syncMode") == "word")
}

internal fun formatTime(ms: Long): String = "%d:%02d".format((ms.coerceAtLeast(0) / 60000), (ms.coerceAtLeast(0) / 1000) % 60)
