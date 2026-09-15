package com.luogen.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luogen.music.LuogenApp
import com.luogen.music.data.api.ApiException
import com.luogen.music.domain.model.HotWord
import com.luogen.music.domain.model.RankList
import com.luogen.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 发现页 ViewModel：真实榜单/新歌/热搜/个人化推荐（基于播放历史）
 */
class HomeViewModel : ViewModel() {

    data class UiState(
        val toplists: List<RankList> = emptyList(),
        val topTracks: Map<Long, List<Song>> = emptyMap(),
        val newsongs: List<Song> = emptyList(),
        val hotWords: List<HotWord> = emptyList(),
        val platformHot: List<HotWord> = emptyList(),
        val recommend: List<Song> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val app = LuogenApp.instance
    private val json = Json { ignoreUnknownKeys = true }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val repo = app.repository
                // 首屏分两层发布：
                // 第一层：榜单壳/新歌/热搜（全部是小接口，毫秒级）完成即结束转圈。
                // 第二层：榜单歌曲预览/平台热点/个人化推荐 后台异步填充，不阻塞首屏。
                val core = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val topD = async {
                            runCatching {
                                kotlinx.coroutines.withTimeout(4000) {
                                    repo.topLists()
                                        .filter { it.id in listOf(19723756L, 3778678L, 3779629L, 2884035L) }
                                }
                            }.getOrDefault(emptyList())
                        }
                        val newsD = async {
                            runCatching { kotlinx.coroutines.withTimeout(4000) { repo.newsongs(20) } }.getOrDefault(emptyList())
                        }
                        val hotD = async {
                            runCatching { kotlinx.coroutines.withTimeout(4000) { repo.searchHot() } }.getOrDefault(emptyList())
                        }
                        CoreState(
                            toplists = topD.await(),
                            newsongs = newsD.await(),
                            hotWords = hotD.await(),
                        )
                    }
                }
                // 核心数据先发布、立即结束 loading（其余随后单独填充）
                _ui.value = UiState(
                    toplists = core.toplists,
                    newsongs = core.newsongs,
                    hotWords = core.hotWords,
                    loading = false,
                )
                // 第二层：榜单歌曲预览 + 平台热点 + 个人化推荐 后台异步填充，不阻塞首屏
                viewModelScope.launch(Dispatchers.IO) {
                    val tops = core.toplists
                    if (tops.isNotEmpty()) {
                        val trackMap = tops.associate { tl ->
                            tl.id to runCatching {
                                kotlinx.coroutines.withTimeout(6000) { repo.topListTracks(tl.id, 50) }
                            }.getOrDefault(emptyList())
                        }
                        _ui.update { it.copy(topTracks = trackMap) }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val hot = runCatching { kotlinx.coroutines.withTimeout(4000) { app.hotApi.fetchAll() } }
                        .getOrDefault(emptyList())
                    if (hot.isNotEmpty()) _ui.update { it.copy(platformHot = hot) }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val rec = runCatching { kotlinx.coroutines.withTimeout(8000) { buildRecommendation() } }
                        .getOrDefault(emptyList())
                    if (rec.isNotEmpty()) _ui.update { it.copy(recommend = rec) }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    private data class CoreState(
        val toplists: List<RankList> = emptyList(),
        val topTracks: Map<Long, List<Song>> = emptyMap(),
        val newsongs: List<Song> = emptyList(),
        val hotWords: List<HotWord> = emptyList(),
    )

    /** 个人化推荐：基于真实播放历史（高频歌手热歌 + 相似歌曲 + 新歌补足），并行拉取 */
    private suspend fun buildRecommendation(): List<Song> = withContext(Dispatchers.IO) {
        coroutineScope {
            val repo = app.repository
            val merged = linkedSetOf<Song>()
            try {
                val history = app.database.dao().topHistory(30)
                // 统计高频歌手
                val singerCount = mutableMapOf<Long, Int>()
                history.forEach { e ->
                    val song = runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                    song?.artistIds?.forEach { singerCount[it] = (singerCount[it] ?: 0) + e.playCount }
                }
                val topSingers = singerCount.entries.sortedByDescending { it.value }.take(3).map { it.key }
                // 并发：高频歌手热歌 + 历史相似歌曲（每个请求都加超时，防止外网慢拖垮推荐）
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<Song>>>()
                topSingers.forEach { sid ->
                    jobs += async {
                        runCatching { kotlinx.coroutines.withTimeout(3000) { repo.artistTopSongs(sid, 5) } }
                            .getOrDefault(emptyList())
                    }
                }
                history.take(3).forEach { e ->
                    val song = runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                    if (song != null && song.id != 0L) {
                        jobs += async {
                            runCatching { kotlinx.coroutines.withTimeout(3000) { repo.simiSongs(song.id, 10) } }
                                .getOrDefault(emptyList())
                        }
                    }
                }
                val parts = jobs.awaitAll()
                for (part in parts) for (song in part) merged.add(song)
                // 热门兜底：新歌
                if (merged.size < 12) {
                    runCatching { repo.newsongs(20).forEach { merged.add(it) } }
                }
            } catch (e: Exception) {
                runCatching { repo.newsongs(12).forEach { merged.add(it) } }
            }
            merged.toList().distinctBy { it.id }.take(20)
        }
    }
}