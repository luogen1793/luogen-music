package com.luogen.music.data.local

import com.luogen.music.LuogenApp
import com.luogen.music.data.db.FavoriteEntity
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 我喜欢的歌曲：内存 StateFlow 实时同步（播放页爱心 ⇄ 我的页列表），Room 持久化。
 * 歌曲以 JSON 快照存储，接口失效/下架也不影响本地收藏展示与播放（本地/下载源）或解析重试（在线源）。
 */
object FavoriteStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** 收藏的 songId 集合（含本地/在线） */
    val favoriteIds: Set<Long>
        get() = _songs.value.map { it.id }.toSet()

    fun load() {
        scope.launch {
            runCatching {
                val list = LuogenApp.instance.database.dao().favorites()
                _songs.value = list.mapNotNull { e ->
                    runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                }
            }
        }
    }

    fun isFavorite(songId: Long): Boolean = favoriteIds.contains(songId)

    fun toggle(song: Song) {
        val now = _songs.value
        val exists = now.any { it.id == song.id }
        _songs.value = if (exists) now.filterNot { it.id == song.id } else listOf(song) + now
        scope.launch {
            val dao = LuogenApp.instance.database.dao()
            if (exists) {
                dao.deleteFavorite(song.id)
            } else {
                dao.upsertFavorite(
                    FavoriteEntity(
                        songId = song.id,
                        songJson = runCatching { json.encodeToString(Song.serializer(), song) }.getOrDefault("{}"),
                        favoritedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun remove(songId: Long) {
        _songs.value = _songs.value.filterNot { it.id == songId }
        scope.launch { LuogenApp.instance.database.dao().deleteFavorite(songId) }
    }

    fun clear() {
        _songs.value = emptyList()
        scope.launch { LuogenApp.instance.database.dao().clearFavorites() }
    }
}