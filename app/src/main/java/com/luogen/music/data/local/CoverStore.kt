package com.luogen.music.data.local

import com.luogen.music.LuogenApp
import com.luogen.music.data.db.CustomCoverEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 自定义封面覆盖存储：用户上传的封面优先于系统封面/默认封面。
 * songId -> 封面图片绝对路径（内存缓存 + Room 持久化）
 */
object CoverStore {

    private val map = ConcurrentHashMap<Long, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _custom = MutableStateFlow<Map<Long, String>>(emptyMap())
    val custom: StateFlow<Map<Long, String>> = _custom.asStateFlow()

    fun load() {
        scope.launch {
            val list = LuogenApp.instance.database.dao().customCovers()
            list.forEach { map[it.songId] = it.imagePath }
            _custom.value = map.toMap()
        }
    }

    fun effectiveCover(songId: Long, fallback: String): String {
        val p = map[songId]
        return if (p != null && File(p).exists()) p else fallback
    }

    fun get(songId: Long): String? {
        val p = map[songId]
        return p?.takeIf { File(it).exists() }
    }

    fun set(songId: Long, path: String) {
        map[songId] = path
        _custom.value = map.toMap()
        scope.launch {
            LuogenApp.instance.database.dao().upsertCover(CustomCoverEntity(songId, path))
        }
    }

    fun remove(songId: Long) {
        map.remove(songId)
        _custom.value = map.toMap()
        scope.launch {
            LuogenApp.instance.database.dao().deleteCover(songId)
        }
    }
}