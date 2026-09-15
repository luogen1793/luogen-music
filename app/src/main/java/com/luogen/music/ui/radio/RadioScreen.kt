package com.luogen.music.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.RaType
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassCard
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 红星 AI 电台：三个真实推荐引擎（热门/知心/宝藏），均基于真实数据，换一换每次有变化 */
class RadioViewModel(
    initialPersona: RaType = RaType.HOT,
) : ViewModel() {
    data class UiState(
        val persona: RaType = RaType.HOT,
        val songs: List<Song> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    )
    private val _ui = MutableStateFlow(UiState(persona = initialPersona))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /** 本轮会话已展示过的歌曲 id：换一换时优先避开，保证列表真的有变化 */
    private val seen = mutableSetOf<Long>()

    init {
        next()
    }

    /** 换一换：多数据源融合候选池 → 优先未展示过的 → 随机打乱取 30 首 */
    fun next() {
        if (_ui.value.loading) return
        val p = _ui.value.persona
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val pool = withContext(Dispatchers.IO) { loadPool(p) }.distinctBy { it.id }
                val fresh = pool.filter { it.id !in seen }
                val picked = (if (fresh.size >= 15) fresh else pool)
                    .shuffled()
                    .take(30)
                picked.forEach { seen.add(it.id) }
                if (picked.isEmpty()) {
                    _ui.value = _ui.value.copy(loading = false, error = "该电台暂时没有可推荐的歌曲，请稍候再试")
                } else {
                    _ui.value = UiState(p, picked, false)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "电台加载失败")
            }
        }
    }

    /** 每个电台的候选曲池：真实数据源，多榜单融合保证每次换一换都有新内容 */
    private suspend fun loadPool(p: RaType): List<Song> {
        val repo = LuogenApp.instance.repository
        val rec = mutableListOf<Song>()
        when (p) {
            // 热辣冲榜官：飙升榜 + 热歌榜 + 新歌榜 融合
            RaType.HOT -> {
                runCatching { repo.topListTracks(19723756L, 80).forEach { rec.add(it) } }
                runCatching { repo.topListTracks(3778678L, 80).forEach { rec.add(it) } }
                runCatching { repo.newsongs(40).forEach { rec.add(it) } }
            }
            // 知心点歌官：播放历史相似歌曲；历史不足时用热门榜兜底
            RaType.SOUL -> {
                val history = LuogenApp.instance.database.dao().topHistory(20)
                history.forEach { e ->
                    val song = runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                    if (song != null && song.id != 0L) {
                        runCatching { repo.simiSongs(song.id, 30).forEach { rec.add(it) } }
                    }
                }
                if (rec.size < 15) {
                    runCatching { repo.topListTracks(3778678L, 60).forEach { rec.add(it) } }
                    runCatching { repo.newsongs(40).forEach { rec.add(it) } }
                }
            }
            // 宝藏挖歌官：原创榜 + 新歌；不足时用新歌榜/飙升榜兜底
            RaType.TREASURE -> {
                runCatching { repo.topListTracks(2884035L, 80).forEach { rec.add(it) } }
                runCatching { repo.newsongs(60).forEach { rec.add(it) } }
                if (rec.size < 15) {
                    runCatching { repo.topListTracks(3779629L, 60).forEach { rec.add(it) } }
                    runCatching { repo.topListTracks(19723756L, 60).forEach { rec.add(it) } }
                }
            }
        }
        return rec
    }

    fun switch(p: RaType) {
        if (_ui.value.persona == p && _ui.value.songs.isNotEmpty()) return
        _ui.value = _ui.value.copy(persona = p)
        next()
    }
}

@Composable
fun RadioScreen(
    initialPersona: RaType = RaType.HOT,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    toArtist: (Long) -> Unit,
    vm: RadioViewModel = viewModel(factory = viewModelFactory { initializer { RadioViewModel(initialPersona) } }),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgPink)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.padding(4.dp))
                    Text("红星 AI 电台", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        RaType.HOT to LgPink,
                        RaType.SOUL to LgSecondary,
                        RaType.TREASURE to LgPrimary,
                    ).forEach { (p, c) ->
                        PressScale(modifier = Modifier.weight(1f), onClick = { vm.switch(p) }) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (ui.persona == p) c.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f))
                                    .padding(12.dp),
                            ) {
                                Text(p.label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(p.slogan, color = TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Radio, null, tint = ui.persona.let { LgSecondary }, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(4.dp))
                    Text("AI 正在生成你的专属歌单…", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    PressScale(onClick = { vm.next() }) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("换一批", color = LgSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (ui.error != null) {
                item {
                    Text(ui.error!!, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
            if (ui.loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = LgPrimary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    }
                }
            }
            items(ui.songs, key = { songListKey(it) }) { song ->
                SongRow(
                    song = song, index = 0, showCover = true,
                    onPlay = { onPlay(ui.songs, ui.songs.indexOf(song).coerceAtLeast(0)) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}