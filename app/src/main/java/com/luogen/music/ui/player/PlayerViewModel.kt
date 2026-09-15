package com.luogen.music.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.PlayMode
import com.luogen.music.domain.model.Song
import com.luogen.music.player.PlayerHub
import com.luogen.music.service.PlaybackService
import com.luogen.music.util.LrcLine
import com.luogen.music.util.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放页 ViewModel：播放状态 + 真实歌词 + 封面样式 + 下载/铃声动作
 */
class PlayerViewModel : ViewModel() {

    val state = PlayerHub.state
    val positionMs: StateFlow<Long> = PlayerHub.positionMs
    val durationMs: StateFlow<Long> = PlayerHub.durationMs
    val currentUrl: StateFlow<String?> = PlayerHub.currentUrl

    data class LyrState(
        val lyrics: List<LrcLine> = emptyList(),
        val loading: Boolean = false,
        val visible: Boolean = false,
        /** true=全屏歌词（隐藏封面，点击歌词可在全屏/封面共存间切换） */
        val fullscreen: Boolean = false,
    )

    private val _lyr = MutableStateFlow(LyrState(visible = true))
    val lyr: StateFlow<LyrState> = _lyr.asStateFlow()

    private val _coverStyle = MutableStateFlow("round")
    val coverStyle: StateFlow<String> = _coverStyle.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            LuogenApp.instance.settings.coverStyle.collect { _coverStyle.value = it }
        }
    }

    fun setCoverStyle(s: String) {
        _coverStyle.value = s
        viewModelScope.launch { LuogenApp.instance.settings.setCoverStyle(s) }
    }

    /** 底部“歌词”开关：开=显示歌词（默认封面共存），关=完全隐藏歌词 */
    fun toggleLyric() { _lyr.value = _lyr.value.copy(visible = !_lyr.value.visible, fullscreen = false) }

    /** 点击歌词区域：在“全屏歌词（隐藏封面）”与“封面共存”之间切换 */
    fun toggleLyricFullscreen() {
        val l = _lyr.value
        if (!l.visible || l.lyrics.isEmpty()) return
        _lyr.value = l.copy(fullscreen = !l.fullscreen)
    }

    /** 加载当前歌曲真实歌词 */
    fun loadLyric() {
        val song = state.value.current ?: return
        if (song.lyric.isNotBlank()) {
            _lyr.value = _lyr.value.copy(lyrics = LrcParser.parse(song.lyric), loading = false)
            return
        }
        viewModelScope.launch {
            _lyr.value = _lyr.value.copy(loading = true)
            val lrc = withContext(Dispatchers.IO) { LuogenApp.instance.repository.lyric(song.id) }
            _lyr.value = _lyr.value.copy(lyrics = LrcParser.parse(lrc), loading = false)
        }
    }

    fun currentLyricLine(): LrcLine? = LrcParser.current(_lyr.value.lyrics, positionMs.value)

    /** 歌词跳转：从指定行的时间点开始播放，不退出全屏 */
    fun seekToLine(line: LrcLine) {
        PlaybackService.seekTo(LuogenApp.instance, line.timeMs)
    }

    // ---------- 命令 ----------
    fun playPause(context: Context) = PlaybackService.playPause(context)
    fun next(context: Context) = PlaybackService.next(context)
    fun prev(context: Context) = PlaybackService.prev(context)
    fun seekTo(context: Context, ms: Long) = PlaybackService.seekTo(context, ms)
    fun removeAt(context: Context, index: Int) = PlaybackService.removeAt(context, index)

    fun setMode(context: Context, mode: PlayMode) = PlaybackService.setMode(context, mode)

    fun rotateMode(context: Context) {
        val order = listOf(PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE, PlayMode.SEQUENCE, PlayMode.SHUFFLE)
        val cur = state.value.mode
        val next = order[(order.indexOf(cur) + 1).let { if (it >= order.size) 0 else it }]
        PlaybackService.setMode(context, next)
    }

    fun downloadCurrent(context: Context, song: Song, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _toast.value = "开始下载…"
            val r = com.luogen.music.util.MediaActions.downloadSong(context, song)
            _toast.value = r.fold(
                onSuccess = { "已下载：${song.name}" },
                onFailure = { "下载失败：${it.message}" },
            )
            onDone(_toast.value ?: "")
        }
    }

    fun setRingtone(context: Context, song: Song) {
        // 未授权“修改系统设置”时给出明确指引（UI 层负责跳转授权页，这里兜底提示）
        if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.System.canWrite(context)) {
            showToast("请先在弹出的系统页面中开启“修改系统设置”权限")
            return
        }
        viewModelScope.launch {
            _toast.value = "正在设为铃声…"
            val r = com.luogen.music.util.MediaActions.setAsRingtone(context, song)
            _toast.value = r.fold(
                onSuccess = { "铃声已设置：${song.name}" },
                onFailure = {
                    when (it) {
                        is SecurityException -> "需要授权“修改系统设置”后才能设置铃声"
                        else -> "设置铃声失败：${it.message}"
                    }
                },
            )
        }
    }

    fun showToast(msg: String) { _toast.value = msg }

    fun consumeToast() { _toast.value = null }
}