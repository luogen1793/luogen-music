package com.luogen.music.data.local

import com.luogen.music.LuogenApp
import com.luogen.music.data.db.MyPlaylistEntity
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.songListKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 我的歌单数据操作（Room 持久化）。
 * songsJson 存 List<Song> 的 JSON 序列化快照，独立于第三方接口可用性。
 */
object PlaylistStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(Song.serializer())

    private suspend fun dao() = LuogenApp.instance.database.dao()

    /** 全部歌单（按创建时间倒序） */
    suspend fun all(): List<MyPlaylistEntity> = dao().playlists()

    /** 创建歌单（coverPath 为空串 = 默认渐变头像），返回新歌单 id */
    suspend fun create(name: String, coverPath: String = ""): Long {
        val e = MyPlaylistEntity(
            name = name.trim(),
            coverPath = coverPath,
            songsJson = json.encodeToString(listSer, emptyList<Song>()),
            createdAt = System.currentTimeMillis(),
        )
        return runCatching { dao().savePlaylist(e) }.getOrDefault(0L)
    }

    /** 歌单内歌曲列表（JSON 解析失败时降级为空） */
    suspend fun songs(p: MyPlaylistEntity): List<Song> =
        runCatching { json.decodeFromString(listSer, p.songsJson) }.getOrDefault(emptyList())

    /** 歌单内歌曲列表（按 id 查） */
    suspend fun songsOf(playlistId: Long): List<Song> {
        val p = dao().playlists().firstOrNull { it.id == playlistId } ?: return emptyList()
        return songs(p)
    }

    /** 追加歌曲（去重：同一歌曲已在歌单中则忽略） */
    suspend fun addSong(p: MyPlaylistEntity, song: Song): Boolean {
        val list = songs(p)
        if (list.any { songListKey(it) == songListKey(song) }) return false
        update(p, list + song)
        return true
    }

    /** 从歌单移除歌曲 */
    suspend fun removeSong(p: MyPlaylistEntity, song: Song) {
        update(p, songs(p).filterNot { songListKey(it) == songListKey(song) })
    }

    /** 更新歌单头像 */
    suspend fun updateCover(p: MyPlaylistEntity, coverPath: String) {
        dao().savePlaylist(p.copy(coverPath = coverPath))
    }

    /** 删除歌单（连同内部歌曲关联一并清除） */
    suspend fun delete(p: MyPlaylistEntity) {
        dao().deletePlaylist(p.id)
    }

    private suspend fun update(p: MyPlaylistEntity, list: List<Song>) {
        dao().savePlaylist(p.copy(songsJson = json.encodeToString(listSer, list)))
    }
}