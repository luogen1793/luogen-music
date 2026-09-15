package com.luogen.music.ui.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.luogen.music.LuogenApp
import com.luogen.music.domain.model.Song
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassGradientButton
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SectionTitle
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistViewModel(private val artistId: Long) : ViewModel() {
    data class UiState(
        val id: Long = 0L,
        val name: String = "",
        val pic: String = "",
        val brief: String = "",
        val musicSize: Int = 0,
        val albumSize: Int = 0,
        val fans: Long = 0,
        val hotSongs: List<Song> = emptyList(),
        val allSongs: List<Song> = emptyList(),
        val albums: List<com.luogen.music.domain.model.Album> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val briefExpanded: Boolean = false,
    )
    private val _ui = MutableStateFlow(UiState(id = artistId))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { refresh() }

    fun toggleBrief() { _ui.value = _ui.value.copy(briefExpanded = !_ui.value.briefExpanded) }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val repo = LuogenApp.instance.repository
                val detail = withContext(Dispatchers.IO) { repo.artistDetail(artistId) }
                val albums = withContext(Dispatchers.IO) { repo.artistAlbums(artistId, 30) }
                val artist = detail.first
                _ui.value = _ui.value.copy(
                    name = artist.name, pic = artist.picUrl, brief = artist.briefDesc,
                    musicSize = artist.musicSize, albumSize = artist.albumSize, fans = artist.fans,
                    hotSongs = detail.second, albums = albums, loading = false,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }
}

/** 歌手详情页：头像/简介/全部歌曲/专辑 */
@Composable
fun ArtistScreen(viewModel: ArtistViewModel, onBack: () -> Unit, toAlbum: (Long) -> Unit) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgPrimary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Text(ui.name, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(Song(name = ui.name, coverUrl = ui.pic), 92.dp, style = "round")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ui.name, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOf(
                                "歌曲 ${ui.musicSize}",
                                "专辑 ${ui.albumSize}",
                                if (ui.fans > 0) "%.1f万粉丝".format(ui.fans / 10000f) else null,
                            ).filterNotNull().joinToString(" · "),
                            color = TextDim, fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                if (ui.brief.isNotBlank()) {
                    val text = if (ui.briefExpanded) ui.brief else ui.brief.take(90) + if (ui.brief.length > 90) "…" else ""
                    Text(
                        text, color = TextDim, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 20.dp).clickable { viewModel.toggleBrief() },
                    )
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp)) {
                    GlassGradientButton("播放全部歌曲", icon = Icons.Rounded.PlayArrow, onClick = {
                        PlaybackService.playQueue(context, ui.hotSongs, 0)
                    })
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
            item { SectionTitle("热门歌曲", modifier = Modifier.padding(horizontal = 16.dp)) }
            items(ui.hotSongs, key = { songListKey(it) }) { song ->
                SongRow(song = song, index = 0, onPlay = {
                    PlaybackService.playQueue(context, ui.hotSongs, ui.hotSongs.indexOf(song).coerceAtLeast(0))
                })
            }
            if (ui.albums.isNotEmpty()) {
                item { Spacer(Modifier.height(18.dp)) }
                item { SectionTitle("专辑", modifier = Modifier.padding(horizontal = 16.dp)) }
                item {
                    LazyRow(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
                        items(ui.albums) { al ->
                            PressScale(onClick = { toAlbum(al.id) }) {
                                Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CoverArt(Song(name = al.name, coverUrl = al.picUrl), 110.dp, style = "square")
                                    Spacer(Modifier.height(6.dp))
                                    Text(al.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(104.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}