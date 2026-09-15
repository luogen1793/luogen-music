package com.luogen.music.ui.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luogen.music.LuogenApp
import com.luogen.music.data.local.LocalLibrary
import com.luogen.music.data.local.MediaScanner
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地页 ViewModel：自动扫描设备音乐与视频（MediaStore 真实读取）
 */
class LocalViewModel : ViewModel() {

    data class UiState(
        val library: LocalLibrary = LocalLibrary(),
        val scanning: Boolean = false,
        val error: String? = null,
        val tab: Int = 0,   // 0 音乐 1 视频
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun setTab(t: Int) { _ui.value = _ui.value.copy(tab = t) }

    fun scan() {
        _ui.value = _ui.value.copy(scanning = true, error = null)
        viewModelScope.launch {
            try {
                val lib = withContext(Dispatchers.IO) { MediaScanner.scan(LuogenApp.instance) }
                _ui.value = _ui.value.copy(library = lib, scanning = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(scanning = false, error = e.message ?: "扫描失败")
            }
        }
    }
}

/** 本地排序帮助 */
fun sortLocal(songs: List<Song>): List<Song> = songs.sortedBy { it.name.lowercase() }