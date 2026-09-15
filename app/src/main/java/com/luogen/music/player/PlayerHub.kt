package com.luogen.music.player

import com.luogen.music.domain.model.PlayMode
import com.luogen.music.domain.model.QueueItem
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放队列与播放状态管理中心（进程内单例，由 PlaybackService 驱动写入，
 * UI/小组件/蓝牙/一起听 统一读取与下发命令）。
 */
object PlayerHub {

    /** 低频播放状态 */
    data class UiState(
        val songs: List<Song> = emptyList(),
        val index: Int = -1,
        val isPlaying: Boolean = false,
        val mode: PlayMode = PlayMode.REPEAT_ALL,
        val isPreparing: Boolean = false,
        val error: String? = null,
    ) {
        val current: Song? get() = if (index in songs.indices) songs[index] else null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 高频播放位置（毫秒） */
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)

    /** 当前歌曲的可用播放地址（失败重试后更新） */
    val currentUrl = MutableStateFlow<String?>(null)

    /** 一起听同步的本地应用状态 */
    val togetherMode = MutableStateFlow(false)

    fun update(transform: (UiState) -> UiState) {
        _state.value = transform(_state.value)
    }

    fun replaceQueue(songs: List<Song>, index: Int) {
        // 队列项去重后入队（保持用户点歌即播）
        val clean = songs.filter { it.id != 0L || it.localUri != null }
        val idx = if (index in clean.indices) index else 0
        _state.value = _state.value.copy(songs = clean, index = idx, error = null)
    }

    fun removeAt(pos: Int) {
        val s = _state.value
        if (pos !in s.songs.indices) return
        val songs = s.songs.toMutableList().apply { removeAt(pos) }
        // 若删除的是当前曲，交给 service 处理
        _state.value = s.copy(songs = songs)
    }

    fun insertNext(song: Song) {
        val s = _state.value
        val insertPos = if (s.index in s.songs.indices) s.index + 1 else s.songs.size
        val songs = s.songs.toMutableList().apply { add(insertPos, song) }
        _state.value = s.copy(songs = songs)
    }
}