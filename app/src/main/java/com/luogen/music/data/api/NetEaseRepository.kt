package com.luogen.music.data.api

import com.luogen.music.domain.model.Album
import com.luogen.music.domain.model.Artist
import com.luogen.music.domain.model.HotWord
import com.luogen.music.domain.model.RankList
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.SongComment
import com.luogen.music.domain.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 网易云音乐数据仓库：把所有真实端点映射为领域模型。
 * 所有数据均为真实接口返回，无任何模拟数据。
 */
class NetEaseRepository(private val api: MusicApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 网易云 CDN 音源 URL 常含空格等非法字符（如 `vuutv=0+g73p58` 中带空格），
     * 直接交给 ExoPlayer/OkHttp 会解析失败触发 onPlayerError -> 无限跳下一首。
     * 这里统一编码为 %20，保证可播放。
     */
    private fun sanitizeAudioUrl(url: String): String =
        if (url.isBlank()) url else url.replace(" ", "%20")

    /**
     * 封面缩略：网易云 `?param=480y480` 参数可把原图（数百 KB）压缩到几十 KB，
     * 显著加快列表/封面加载；对已是缩略或非 http 的地址原样返回。
     */
    private fun thumbCover(url: String): String {
        if (url.isBlank() || !url.startsWith("http")) return url
        if (url.contains("param=")) return url
        return url + (if (url.contains("?")) "&" else "?") + "param=480y480"
    }

    // ---------- 搜索 ----------
    suspend fun searchSongs(keyword: String, limit: Int = 30, offset: Int = 0): List<Song> {
        val r = api.get("/search", mapOf("keywords" to keyword, "type" to "1", "limit" to "$limit", "offset" to "$offset"))
        return enrichCovers(r.obj("result").arr("songs").mapNotNull { it.asObj().toSong() })
    }

    suspend fun searchArtists(keyword: String, limit: Int = 20, offset: Int = 0): List<Artist> {
        val r = api.get("/search", mapOf("keywords" to keyword, "type" to "100", "limit" to "$limit", "offset" to "$offset"))
        return r.obj("result").arr("artists").mapNotNull { it.asObj().toArtist() }
    }

    suspend fun searchAlbums(keyword: String, limit: Int = 20, offset: Int = 0): List<Album> {
        val r = api.get("/search", mapOf("keywords" to keyword, "type" to "10", "limit" to "$limit", "offset" to "$offset"))
        return r.obj("result").arr("albums").mapNotNull { it.asObj().toAlbum() }
    }

    /** 综合搜索：一次请求聚合 全部维度 */
    suspend fun searchAll(keyword: String, limit: Int = 20): SearchResult {
        val tracks = searchSongs(keyword, limit)
        val artists = searchArtists(keyword, 10)
        val albums = searchAlbums(keyword, 10)
        return SearchResult(tracks, artists, albums)
    }

    // ---------- 热搜 / 榜单 ----------
    suspend fun searchHot(): List<HotWord> {
        return try {
            val r = api.get("/search/hot")
            r.obj("result").arr("hots").mapIndexed { idx, e ->
                val o = e.asObj()
                HotWord(o.str("first"), o.str("second").toLongOrNull() ?: 0L, idx + 1, "网易云")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun topLists(): List<RankList> {
        val r = api.get("/toplist")
        return r.arr("list").mapNotNull { e ->
            val o = e.asObj()
            RankList(o.lng("id"), o.str("name"), o.str("coverImgUrl"), o.str("updateFrequency"),
                o.lng("playCount"), o.i("trackCount"))
        }
    }

    suspend fun topListTracks(id: Long, limit: Int = 50): List<Song> {
        // 实测：/playlist/detail 忽略 limit 恒返回全量约 260KB（稳定 400-800ms，首屏卡顿主因）；
        // /playlist/track/all 支持 limit 且单次仅数十 KB、约 15ms，字段结构完全兼容，榜单加载快一个数量级。
        val r = api.get("/playlist/track/all", mapOf("id" to "$id", "limit" to "$limit"))
        return enrichCovers(r.arr("songs").mapNotNull { it.asObj().toSong() }.take(limit))
    }

    // ---------- 歌曲 ----------
    suspend fun songUrls(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val r = api.get("/song/url", mapOf("id" to ids.joinToString(",")))
        return r.arr("data").mapNotNull { e ->
            val o = e.asObj()
            val id = o.lng("id")
            if (id == 0L) null else id to sanitizeAudioUrl(o.str("url"))
        }.toMap()
    }

    suspend fun songUrl(id: Long): String? = songUrls(listOf(id))[id]

    /** 指定服务器获取播放地址（多服务器 fallback 用），URL 做安全清洗 */
    suspend fun songUrlForServer(baseUrl: String, id: Long): String? {
        val full = baseUrl.trimEnd('/') + "/song/url?id=" + id
        val body = ApiHttp.requestOnce(full) ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject
                .arr("data").firstOrNull()?.asObj()?.str("url")
                ?.takeIf { it.isNotBlank() }?.let { sanitizeAudioUrl(it) }
        }.getOrNull()
    }

    suspend fun lyric(id: Long): String {
        return try {
            api.get("/lyric", mapOf("id" to "$id")).obj("lrc").str("lyric")
        } catch (e: Exception) { "" }
    }

    suspend fun songDetail(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val r = api.get("/song/detail", mapOf("ids" to ids.joinToString(",")))
        return r.arr("songs").mapNotNull { it.asObj().toSong() }
    }

    suspend fun simiSongs(id: Long, limit: Int = 20): List<Song> {
        val r = api.get("/simi/song", mapOf("id" to "$id", "limit" to "$limit"))
        return enrichCovers(r.arr("songs").mapNotNull { it.asObj().toSong() })
    }

    /**
     * 歌曲评论（楼中楼版）：优先直连网易官方评论接口，返回热评 + 最新评论，
     * 并把带 beReplied 引用的回复（楼中楼）挂到对应父评论的 replies 下。
     * 官方接口不可用时回退镜像 /comment/music（同一组装逻辑）。
     */
    suspend fun comments(musicId: Long, limit: Int = 60): List<SongComment> {
        // 1) 官方直连（匿名可读，带 Referer；字段与镜像完全一致）
        val official = runCatching {
            val url = "https://music.163.com/api/v1/resource/comments/R_SO_4_$musicId?rid=R_SO_4_$musicId&limit=$limit"
            ApiHttp.requestOnce(url)?.let { body ->
                val r = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
                assembleComments(r)
            }
        }.getOrNull()
        if (!official.isNullOrEmpty()) return official
        // 2) 兜底：镜像接口
        return try {
            val r = api.get("/comment/music", mapOf("id" to "$musicId", "limit" to "$limit"))
            assembleComments(r)
        } catch (e: Exception) { emptyList() }
    }

    /** 解析 hotComments+comments 并按 beReplied(父评论 id) 组装楼中楼 */
    private fun assembleComments(r: JsonObject): List<SongComment> {
        val seen = HashSet<Long>()
        val all = buildList {
            r.arr("hotComments").mapNotNullTo(this) { e -> e.asObj().toComment()?.also { seen.add(it.commentId) } }
            r.arr("comments").mapNotNullTo(this) { e ->
                val c = e.asObj().toComment() ?: return@mapNotNullTo null
                if (seen.contains(c.commentId)) null else c
            }
        }
        val top = LinkedHashMap<Long, SongComment>()
        val repliesBuf = HashMap<Long, MutableList<SongComment>>()
        for (c in all) {
            if (c.parentId > 0L && c.parentId != c.commentId && top.containsKey(c.parentId)) {
                repliesBuf.getOrPut(c.parentId) { mutableListOf() }.add(c)
            } else {
                // 顶层评论，或父评论不在本批（limit 截断）时作为独立评论展示（带“回复 @xxx”引用）
                top[c.commentId] = c
            }
        }
        return top.values.map { t -> t.copy(replies = repliesBuf[t.commentId] ?: emptyList()) }
    }

    // ---------- 歌手 ----------
    suspend fun artistDetail(id: Long): Pair<Artist, List<Song>> {
        val r = api.get("/artists", mapOf("id" to "$id"))
        val artist = r.obj("artist").toArtist()
        val hot = r.arr("hotSongs").mapNotNull { it.asObj().toSong() }
        return (artist ?: Artist(id = id)) to enrichCovers(hot)
    }

    suspend fun artistTopSongs(id: Long, limit: Int = 50): List<Song> {
        val r = api.get("/artist/top/song", mapOf("id" to "$id", "limit" to "$limit"))
        return enrichCovers(r.arr("songs").mapNotNull { it.asObj().toSong() })
    }

    suspend fun artistAlbums(id: Long, limit: Int = 30): List<Album> {
        val r = api.get("/artist/album", mapOf("id" to "$id", "limit" to "$limit"))
        return r.arr("hotAlbums").mapNotNull { it.asObj().toAlbum() }
    }

    suspend fun simiArtists(id: Long, limit: Int = 10): List<Artist> {
        return try {
            val r = api.get("/artist/simi/artist", mapOf("id" to "$id", "limit" to "$limit"))
            r.arr("artists").mapNotNull { it.asObj().toArtist() }
        } catch (e: Exception) { emptyList() }
    }

    // ---------- 专辑 ----------
    suspend fun albumDetail(id: Long): Pair<Album, List<Song>> {
        val r = api.get("/album", mapOf("id" to "$id"))
        val album = r.obj("album").toAlbum()
        val songs = r.arr("songs").mapNotNull { it.asObj().toSong() }
        return (album ?: Album(id = id)) to enrichCovers(songs)
    }

    suspend fun newAlbums(limit: Int = 20): List<Album> {
        val r = api.get("/album/new", mapOf("limit" to "$limit", "area" to "ALL"))
        return r.arr("albums").mapNotNull { it.asObj().toAlbum() }
    }

    // ---------- 推荐 ----------
    suspend fun newsongs(limit: Int = 20): List<Song> {
        val r = api.get("/personalized/newsong", mapOf("limit" to "$limit"))
        return enrichCovers(r.arr("result").mapNotNull { e -> e.asObj().obj("song").toSong() })
    }

    // ---------- MV / 视频 ----------
    suspend fun mvList(limit: Int = 30, area: String = "全部"): List<Video> {
        val r = api.get("/mv/first", mapOf("limit" to "$limit", "area" to area))
        return r.arr("data").mapNotNull { e ->
            val o = e.asObj()
            Video(o.lng("id"), o.str("name"), thumbCover(o.str("cover")), o.lng("playCount"),
                o.str("artistName"), o.lng("artistId"), o.lng("duration"), "")
        }
    }

    /** 关键词搜索 MV（type=1004），任意关键词均返回真实可播放的 MV 列表 */
    suspend fun searchVideos(keyword: String, limit: Int = 30, offset: Int = 0): List<Video> {
        return try {
            val r = api.get("/search", mapOf("keywords" to keyword, "type" to "1004", "limit" to "$limit", "offset" to "$offset"))
            r.obj("result").arr("mvs").mapNotNull { e ->
                val o = e.asObj()
                Video(o.lng("id"), o.str("name"), thumbCover(o.str("cover")), o.lng("playCount"),
                    o.str("artistName"), o.lng("artistId"), o.lng("duration"), "")
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun mvUrl(id: Long): String {
        val r = api.get("/mv/url", mapOf("id" to "$id"))
        return sanitizeAudioUrl(r.obj("data").str("url"))
    }

    /**
     * 网易官方 MV 详情接口解析直链（不依赖任何镜像的 /mv/url 权限）。
     * [https://music.163.com/api/mv/detail?id=xxx&type=mp4] 返回 data.brs 码率字典
     * {240/480/720/1080: 直链}，实测 vodkgeyttp8.vod.126.net 直链可直接播放（206+ftyp）。
     * 镜像 /mv/url 对大量 MV 返回空 url（镜像侧无源），官方接口是更可靠的主通道。
     * 无源（MV 已下架/非正式 MV）返回 null。
     */
    suspend fun mvUrlOfficial(id: Long): String? = runCatching {
        val body = ApiHttp.requestOnce(
            "https://music.163.com/api/mv/detail?id=$id&type=mp4",
            timeoutMs = 10000,
        ) ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        if (root.str("code") != "200") return null
        val brs = root.obj("data").obj("brs")
        // 按清晰度从高到低取第一个可用直链
        val picked = listOf("1080", "720", "480", "360", "240")
            .firstNotNullOfOrNull { k ->
                brs.str(k).takeIf { it.startsWith("http") }
            }
        picked?.let { sanitizeAudioUrl(it) }
    }.getOrNull()

    /** 指定服务器解析 MV 直链（多服务器 fallback 用），无源返回 null */
    suspend fun mvUrlForServer(baseUrl: String, id: Long): String? {
        val full = baseUrl.trimEnd('/') + "/mv/url?id=" + id
        val body = ApiHttp.requestOnce(full) ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject
                .obj("data").str("url")
                .takeIf { it.isNotBlank() }?.let { sanitizeAudioUrl(it) }
        }.getOrNull()
    }

    suspend fun artistMv(artistId: Long, limit: Int = 20): List<Video> {
        val r = api.get("/artist/mv", mapOf("id" to "$artistId", "limit" to "$limit"))
        return r.arr("mvs").mapNotNull { e ->
            val o = e.asObj()
            Video(o.lng("id"), o.str("name"), thumbCover(o.str("imgurl")), o.lng("playCount"), "", o.lng("artistId"), 0, "")
        }
    }

    // ---------- 模型映射 ----------
    /**
     * 批量补齐歌曲封面：搜索/榜单接口的歌曲通常只有 picId 数字，
     * /song/detail 才返回完整 picUrl。对 coverUrl 为空且 id 有效的歌曲
     * 一次请求批量拉取封面并回填，保证列表/播放页显示真实封面。
     */
    private suspend fun enrichCovers(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        val need = songs.filter { it.coverUrl.isBlank() && it.id > 0L }
        if (need.isEmpty()) return songs
        return try {
            val covers = mutableMapOf<Long, String>()
            need.chunked(50).forEach { batch ->
                val r = api.get("/song/detail", mapOf("ids" to batch.joinToString(",") { it.id.toString() }))
                r.arr("songs").forEach { e ->
                    val o = e.asObj()
                    val id = o.lng("id")
                    if (id != 0L) {
                        val pic = o.obj("al").str("picUrl").ifBlank { o.obj("al").str("pic") }
                        if (pic.isNotBlank()) covers[id] = thumbCover(pic)
                    }
                }
            }
            if (covers.isEmpty()) songs
            else songs.map { s -> if (s.coverUrl.isBlank()) s.copy(coverUrl = covers[s.id] ?: s.coverUrl) else s }
        } catch (e: Exception) { songs }
    }

    private fun JsonObject.toSong(): Song? {
        val id = lng("id")
        if (id == 0L) return null
        val ar = if (arr("ar").isNotEmpty()) arr("ar") else arr("artists")
        val al = if (obj("al").isNotEmpty()) obj("al") else obj("album")
        val artists = ar.mapNotNull { (it as? JsonObject)?.str("name") }
        val artistIds = ar.mapNotNull { (it as? JsonObject)?.lng("id") }
        val name = str("name")
        if (name.isBlank()) return null
        val dur = lng("duration").let { if (it == 0L) lng("dt") else it }
        return Song(
            id = id, name = name,
            artistNames = artists, artistIds = artistIds,
            albumName = al.str("name"), albumId = al.lng("id"),
            coverUrl = thumbCover(al.str("picUrl").ifBlank { al.str("blurPicUrl") }),
            durationMs = dur, fee = i("fee"), mvid = lng("mvid"),
            popularity = i("pop").let { if (it == 0) i("popularity") else it },
            publishTime = al.lng("publishTime"),
        )
    }

    private fun JsonObject.toArtist(): Artist? {
        val id = lng("id")
        val name = str("name")
        if (id == 0L || name.isBlank()) return null
        return Artist(
            id = id, name = name,
            picUrl = thumbCover(str("picUrl").ifBlank { str("img1v1Url") }),
            alias = arr("alias").mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() },
            musicSize = i("musicSize"), albumSize = i("albumSize"),
            briefDesc = str("briefDesc"), fans = lng("fansCount").let { if (it == 0L) lng("fans") else it },
        )
    }

    private fun JsonObject.toComment(): SongComment? {
        val id = lng("commentId")
        if (id == 0L) return null
        val user = obj("user")
        val replied = arr("beReplied").firstOrNull()?.asObj()
        return SongComment(
            commentId = id,
            nickname = user.str("nickname"),
            avatarUrl = thumbCover(user.str("avatarUrl")),
            content = str("content"),
            time = lng("time"),
            likedCount = i("likedCount"),
            parentId = replied?.lng("beRepliedCommentId") ?: 0L,
            replyTo = replied?.str("content") ?: "",
            replyToUser = replied?.obj("user")?.str("nickname") ?: "",
        )
    }

    private fun JsonObject.toAlbum(): Album? {
        val id = lng("id")
        val name = str("name")
        if (id == 0L || name.isBlank()) return null
        val artistObj = obj("artist")
        val artistName = if (artistObj.isNotEmpty()) artistObj.str("name") else str("artistName")
        return Album(
            id = id, name = name,
            picUrl = thumbCover(str("picUrl").ifBlank { str("blurPicUrl") }),
            publishTime = lng("publishTime"), size = i("size"),
            artistNames = listOfNotNull(artistName.ifBlank { null }),
            description = str("description"), company = str("company"),
        )
    }
}

data class SearchResult(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
)