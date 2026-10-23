package org.yaqmc.android.nativeui

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.text(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

internal data class Track(val json: JSONObject) {
    val id: String = json.text("id")
    val providerId: String = json.optJSONObject("provider")?.text("providerId", "qqmusic") ?: "qqmusic"
    val title: String = json.text("title", "未知歌曲")
    val artists: String = json.optJSONArray("artists").objects().joinToString(" / ") { it.text("name") }
    val album: String = json.optJSONObject("album")?.text("title").orEmpty()
    val artwork: String = json.optJSONObject("artwork")?.text("src").orEmpty()
    val duration: Long = json.optLong("durationMs")
    val favorite: Boolean = json.optBoolean("isFavorite")
}

internal data class CatalogItem(
    val kind: String,
    val json: JSONObject,
) {
    val id: String = json.text("id", json.text("encArea"))
    val title: String = json.text("title", json.text("name"))
    val artwork: String = json.optJSONObject("artwork")?.text("src") ?: json.text("cover")
    val subtitle: String = json.optJSONObject("artist")?.text("name")
        ?: json.optJSONObject("owner")?.text("displayName") ?: json.text("creator")
}

internal data class FeedSection(
    val title: String,
    val cards: List<CatalogItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

internal data class CatalogPage(
    val title: String,
    val subtitle: String = "",
    val artwork: String = "",
    val sections: List<FeedSection> = emptyList(),
    val hasMore: Boolean = false,
    val cursor: String? = null,
) {
    val tracks: List<Track> get() = sections.flatMap { it.tracks }
}

internal data class PlayerState(
    val queue: List<Track> = emptyList(),
    val entryIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val phase: String = "idle",
    val mode: String = "sequential",
    val quality: String = "",
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)
}

internal data class LyricLine(
    val start: Long?, val end: Long?, val text: String,
    val translation: String, val romanization: String, val words: List<LyricWord>,
)
internal data class LyricWord(val start: Long, val end: Long, val text: String)
internal data class Lyrics(val songId: String = "", val offset: Long = 0, val lines: List<LyricLine> = emptyList()) {
    fun activeIndex(position: Long): Int = lines.indexOfLast { it.start != null && it.start <= position + offset }
}

/** JSON is decoded only at the existing Core boundary. Track JSON is preserved for playback requests. */
internal object NativeModels {
    fun tracks(array: JSONArray?): List<Track> = array.objects().map(::Track)
    fun cards(array: JSONArray?, kind: String): List<CatalogItem> = array.objects().map { CatalogItem(kind, it) }

    fun home(value: JSONObject): CatalogPage {
        val sections = mutableListOf<FeedSection>()
        val personal = listOf("guessSonglist", "dailySonglist", "newSongSonglist")
            .mapNotNull { value.optJSONObject(it) }.map { CatalogItem("playlist", it) }
        sections += FeedSection("为你推荐", personal)
        sections += FeedSection("音乐雷达", tracks = tracks(value.optJSONArray("radarSongs")))
        sections += FeedSection("推荐歌单", cards(value.optJSONArray("recommendedSonglists"), "playlist"))
        sections += FeedSection("为你精选", cards(value.optJSONArray("madeForYou"), "playlist"))
        sections += FeedSection("新碟上架", cards(value.optJSONArray("newReleases"), "album"))
        sections += FeedSection("最近播放", value.optJSONArray("recentlyPlayed").objects().mapNotNull {
            collection -> collection.optJSONObject("item")?.let { item -> CatalogItem(collection.text("type"), item) }
        })
        return CatalogPage("首页", "发现属于你的音乐", sections = sections.filter { it.cards.isNotEmpty() || it.tracks.isNotEmpty() })
    }

    fun discover(value: JSONObject) = CatalogPage("发现", "新的声音，新的心情", sections = listOf(
        FeedSection("排行榜", cards(value.optJSONArray("charts"), "playlist")),
        FeedSection("新歌速递", cards = listOfNotNull(value.optJSONObject("newSongs")?.let { CatalogItem("playlist", it) })),
        FeedSection("新碟上架", cards(value.optJSONArray("newAlbums"), "album")),
        FeedSection("热门歌单", cards(value.optJSONArray("popularSonglists"), "playlist")),
        FeedSection("分类", cards(value.optJSONArray("categories"), "area")),
    ).filter { it.cards.isNotEmpty() })

    fun detail(kind: String, value: JSONObject): CatalogPage {
        val summary = value.optJSONObject("summary") ?: value
        val trackPage = value.optJSONObject("tracks")
        val tracks = tracks(trackPage?.optJSONArray("items") ?: value.optJSONArray("tracks") ?: value.optJSONArray("topSongs"))
        val sections = mutableListOf(FeedSection("歌曲", tracks = tracks))
        if (kind == "artist") sections += FeedSection("专辑", cards(value.optJSONArray("albums"), "album"))
        if (kind == "area") {
            sections += FeedSection("歌单", cards(value.optJSONArray("songlists"), "playlist") + cards(value.optJSONArray("playlists"), "playlist"))
            sections += FeedSection("歌手", cards(value.optJSONArray("artists"), "artist"))
        }
        return CatalogPage(summary.text("title", summary.text("name")), summary.text("description"),
            summary.optJSONObject("artwork")?.text("src").orEmpty(), sections,
            hasMore = trackPage?.text("nextCursor")?.isNotEmpty() == true,
            cursor = trackPage?.text("nextCursor")?.takeIf(String::isNotEmpty))
    }

    fun lyrics(value: JSONObject) = Lyrics(value.text("songId"), value.optJSONObject("metadata")?.optLong("offsetMs") ?: 0,
        value.optJSONArray("lines").objects().map { line ->
            LyricLine(if (line.isNull("startMs")) null else line.optLong("startMs"),
                if (line.isNull("endMs")) null else line.optLong("endMs"), line.text("text"),
                line.text("translation"), line.text("romanization"), line.optJSONArray("words").objects().map {
                    LyricWord(it.optLong("startMs"), it.optLong("endMs"), it.text("text"))
                })
        })
}

internal fun formatTime(ms: Long): String = "%d:%02d".format((ms.coerceAtLeast(0) / 60000), (ms.coerceAtLeast(0) / 1000) % 60)
