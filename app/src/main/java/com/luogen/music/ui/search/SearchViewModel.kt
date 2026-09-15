package com.luogen.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.Album
import com.luogen.music.domain.model.Artist
import com.luogen.music.domain.model.HotWord
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索页 ViewModel：真实调用网易云多维度搜索 + 平台热搜
 * 歌曲 / 歌手 / 专辑三路均支持持续分页，滚动到底自动加载下一页。
 */
class SearchViewModel : ViewModel() {

    enum class Tab(val label: String) { MIX("综合"), SONG("歌曲"), ARTIST("歌手"), ALBUM("专辑"), VIDEO("视频") }

    private companion object {
        const val PAGE = 30          // 歌曲每页条数
        const val PAGE_META = 20     // 歌手/专辑每页条数
    }

    data class UiState(
        val keyword: String = "",
        val tab: Tab = Tab.MIX,
        val searched: Boolean = false,
        val songs: List<Song> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val videos: List<Video> = emptyList(),
        val hotWords: List<HotWord> = emptyList(),
        val platformHot: List<HotWord> = emptyList(),
        val history: List<String> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val songOffset: Int = 0,
        val hasMoreSongs: Boolean = false,
        val artistOffset: Int = 0,
        val hasMoreArtists: Boolean = false,
        val albumOffset: Int = 0,
        val hasMoreAlbums: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val app = LuogenApp.instance

    init {
        loadHot()
        // 恢复搜索历史（最近 12 条）
        viewModelScope.launch {
            val h = withContext(Dispatchers.IO) { app.searchHistory.load() }
            _ui.value = _ui.value.copy(history = h)
        }
    }

    /** 清空搜索历史 */
    fun clearHistory() {
        viewModelScope.launch {
            val h = withContext(Dispatchers.IO) { app.searchHistory.clear() }
            _ui.value = _ui.value.copy(history = h)
        }
    }

    private fun loadHot() {
        viewModelScope.launch {
            val hot = withContext(Dispatchers.IO) { runCatching { app.repository.searchHot() }.getOrDefault(emptyList()) }
            val plat = withContext(Dispatchers.IO) { runCatching { kotlinx.coroutines.withTimeout(4000) { app.hotApi.fetchAll() } }.getOrDefault(emptyList()) }
            _ui.value = _ui.value.copy(hotWords = hot, platformHot = plat)
        }
    }

    fun setKeyword(k: String) { _ui.value = _ui.value.copy(keyword = k); if (k.isBlank() && _ui.value.searched) clearSearch() }

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        _ui.value = _ui.value.copy(keyword = keyword, searched = true, loading = true, error = null,
            songs = emptyList(), artists = emptyList(), albums = emptyList(), videos = emptyList())
        // 记录搜索历史（去重置顶，异步写盘，不阻塞搜索）
        viewModelScope.launch {
            val h = withContext(Dispatchers.IO) { app.searchHistory.append(keyword) }
            _ui.value = _ui.value.copy(history = h)
        }
        viewModelScope.launch {
            try {
                val repo = app.repository
                // 四路搜索完全并行（各自超时兜底），总耗时从「四路相加」降为「最慢一路」
                val res = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val songsD = async {
                            runCatching { kotlinx.coroutines.withTimeout(6000) { repo.searchSongs(keyword, PAGE, 0) } }
                                .getOrDefault(emptyList())
                        }
                        val artistsD = async {
                            runCatching { kotlinx.coroutines.withTimeout(6000) { repo.searchArtists(keyword, PAGE_META, 0) } }
                                .getOrDefault(emptyList())
                        }
                        val albumsD = async {
                            runCatching { kotlinx.coroutines.withTimeout(6000) { repo.searchAlbums(keyword, PAGE_META, 0) } }
                                .getOrDefault(emptyList())
                        }
                        val videosD = async { loadVideos(keyword) }
                        SearchCore(
                            songs = songsD.await(),
                            artists = artistsD.await(),
                            albums = albumsD.await(),
                            videos = videosD.await(),
                        )
                    }
                }
                _ui.value = _ui.value.copy(
                    songs = res.songs, artists = res.artists, albums = res.albums, videos = res.videos,
                    loading = false,
                    songOffset = res.songs.size, hasMoreSongs = res.songs.size >= PAGE,
                    artistOffset = res.artists.size, hasMoreArtists = res.artists.size >= PAGE_META,
                    albumOffset = res.albums.size, hasMoreAlbums = res.albums.size >= PAGE_META,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "搜索失败，请检查网络或服务器设置")
            }
        }
    }

    private data class SearchCore(
        val songs: List<Song> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val videos: List<Video> = emptyList(),
    )

    /** 歌曲滚动加载更多 */
    fun loadMoreSongs() {
        val st = _ui.value
        if (st.loading || !st.hasMoreSongs) return
        viewModelScope.launch {
            _ui.value = st.copy(loading = true)
            try {
                val more = withContext(Dispatchers.IO) { app.repository.searchSongs(st.keyword, PAGE, st.songOffset) }
                _ui.value = _ui.value.copy(
                    songs = st.songs + more, songOffset = st.songOffset + more.size,
                    hasMoreSongs = more.size >= PAGE, loading = false,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    /** 歌手滚动加载更多 */
    fun loadMoreArtists() {
        val st = _ui.value
        if (st.loading || !st.hasMoreArtists) return
        viewModelScope.launch {
            _ui.value = st.copy(loading = true)
            try {
                val more = withContext(Dispatchers.IO) { app.repository.searchArtists(st.keyword, PAGE_META, st.artistOffset) }
                _ui.value = _ui.value.copy(
                    artists = st.artists + more, artistOffset = st.artistOffset + more.size,
                    hasMoreArtists = more.size >= PAGE_META, loading = false,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    /** 专辑滚动加载更多 */
    fun loadMoreAlbums() {
        val st = _ui.value
        if (st.loading || !st.hasMoreAlbums) return
        viewModelScope.launch {
            _ui.value = st.copy(loading = true)
            try {
                val more = withContext(Dispatchers.IO) { app.repository.searchAlbums(st.keyword, PAGE_META, st.albumOffset) }
                _ui.value = _ui.value.copy(
                    albums = st.albums + more, albumOffset = st.albumOffset + more.size,
                    hasMoreAlbums = more.size >= PAGE_META, loading = false,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    fun setTab(t: Tab) { _ui.value = _ui.value.copy(tab = t) }

    fun clearSearch() {
        _ui.value = UiState(
            keyword = "", tab = Tab.MIX, searched = false,
            songs = emptyList(), artists = emptyList(), albums = emptyList(), videos = emptyList(),
            loading = false, error = null,
            hotWords = _ui.value.hotWords, platformHot = _ui.value.platformHot,
            history = _ui.value.history,
            songOffset = 0, hasMoreSongs = false,
            artistOffset = 0, hasMoreArtists = false,
            albumOffset = 0, hasMoreAlbums = false,
        )
    }

    /** MV 搜索独立并行：真实关键词搜索优先，空结果兜底热门 MV（不再依赖歌手结果，避免串行等待） */
    private suspend fun loadVideos(keyword: String): List<Video> {
        val repo = app.repository
        val searched = runCatching {
            kotlinx.coroutines.withTimeout(6000) { repo.searchVideos(keyword.trim(), 30, 0) }
        }.getOrDefault(emptyList())
        if (searched.isNotEmpty()) return searched
        return runCatching {
            kotlinx.coroutines.withTimeout(5000) { repo.mvList(30) }
        }.getOrDefault(emptyList())
    }
}