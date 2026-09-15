package com.luogen.music.ui.search

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.domain.model.Artist
import com.luogen.music.domain.model.HotWord
import com.luogen.music.domain.model.Song
import com.luogen.music.domain.model.Video
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassChip
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SectionTitle
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import androidx.compose.material.icons.rounded.TrendingUp

/** 搜索页：玻璃搜索框 + 多维度结果 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialKeyword: String = "",
    initialVideoOnly: Boolean = false,
    toArtist: (Long) -> Unit,
    toAlbum: (Long) -> Unit,
    toPlayVideo: (Video) -> Unit,
    toOpenPlayer: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // 每次从首页热搜/平台榜跳转都应重新自动填入并搜索（不再受 searched 状态抑制）
    LaunchedEffect(initialKeyword, initialVideoOnly) {
        if (initialKeyword.isNotBlank()) {
            viewModel.search(initialKeyword)
            if (initialVideoOnly) viewModel.setTab(SearchViewModel.Tab.VIDEO)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        Column(Modifier.fillMaxSize()) {
            // 玻璃搜索框
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(Modifier),
                ) {
                    TextField(
                        value = ui.keyword,
                        onValueChange = { viewModel.setKeyword(it) },
                        placeholder = { Text("搜索歌曲、歌手、专辑、MV", color = TextDim, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextDim) },
                        trailingIcon = {
                            if (ui.keyword.isNotBlank()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Rounded.Clear, null, tint = TextDim, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            viewModel.search(ui.keyword)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = LgSecondary,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                PressScale(onClick = {
                    keyboardController?.hide()
                    viewModel.search(ui.keyword)
                }) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (ui.keyword.isNotBlank()) LgPrimary else Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("搜索", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (!ui.searched) {
                // 热搜与平台榜
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 30.dp)) {
                    // 搜索历史（未搜索态置顶展示）
                    if (ui.history.isNotEmpty()) {
                        item {
                            Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, tint = TextDim, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("搜索历史", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "清空",
                                    color = TextDim,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.clearHistory() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            LazyRow(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ui.history) { w ->
                                    GlassChip(text = w, onClick = { viewModel.search(w) })
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = LgPink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("热搜趋势", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        LazyRow(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ui.hotWords.take(24)) { h ->
                                GlassChip(text = h.word, onClick = { viewModel.search(h.word) })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                    item { SectionTitle("全网热点榜", subtitle = "头条 · B站 · 实时", modifier = Modifier.padding(horizontal = 16.dp)) }
                    items(ui.platformHot.take(20)) { h ->
                        PlatformRow(h, onClick = { viewModel.search(h.word) })
                    }
                }
            } else {
                // 分类 Tab
                TabRow(
                    selectedTabIndex = ui.tab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = LgSecondary,
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.06f)) },
                ) {
                    SearchViewModel.Tab.entries.forEach { t ->
                        Tab(
                            selected = ui.tab == t,
                            onClick = { viewModel.setTab(t) },
                            text = { Text(t.label, fontSize = 13.sp, fontWeight = if (ui.tab == t) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }

                when (ui.tab) {
                    SearchViewModel.Tab.MIX -> MixTab(ui, viewModel, context, toArtist, toAlbum, toPlayVideo, toOpenPlayer)
                    SearchViewModel.Tab.SONG -> SongTab(ui, viewModel, context, toOpenPlayer)
                    SearchViewModel.Tab.ARTIST -> ArtistTab(ui, viewModel, toArtist)
                    SearchViewModel.Tab.ALBUM -> AlbumTab(ui, viewModel, toAlbum)
                    SearchViewModel.Tab.VIDEO -> VideoTab(ui.videos, toPlayVideo)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MixTab(
    ui: com.luogen.music.ui.search.SearchViewModel.UiState,
    viewModel: SearchViewModel,
    context: android.content.Context,
    toArtist: (Long) -> Unit,
    toAlbum: (Long) -> Unit,
    toPlayVideo: (Video) -> Unit,
    toOpenPlayer: () -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
        if (ui.artists.isNotEmpty()) {
            item { SectionTitle("歌手", modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) }
            item {
                LazyRow(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
                    items(ui.artists.take(10).distinctBy { it.id }) { a ->
                        PressScale(onClick = { toArtist(a.id) }) {
                            Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CoverArt(Song(name = a.name, coverUrl = a.picUrl), 72.dp, style = "round")
                                Spacer(Modifier.height(6.dp))
                                Text(a.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(76.dp))
                            }
                        }
                    }
                }
            }
        }
        if (ui.albums.isNotEmpty()) {
            item { SectionTitle("专辑", modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) }
            item {
                LazyRow(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
                    items(ui.albums.take(10).distinctBy { it.id }) { al ->
                        PressScale(onClick = { toAlbum(al.id) }) {
                            Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CoverArt(Song(name = al.name, coverUrl = al.picUrl, albumName = al.name), 96.dp, style = "square")
                                Spacer(Modifier.height(6.dp))
                                Text(al.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(90.dp))
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("歌曲", modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) }
        items(ui.songs, key = { it.keyOf() }) { song ->
            SongRow(song = song, index = 0, onPlay = {
                PlaybackService.playQueue(context, ui.songs, ui.songs.indexOf(song).coerceAtLeast(0))
                toOpenPlayer()
            })
        }
        // 综合页底部自动加载更多歌曲
        item {
            LoadMoreFooter(
                listState = listState,
                hasMore = ui.hasMoreSongs,
                loading = ui.loading,
                onLoad = { viewModel.loadMoreSongs() },
            )
        }
    }
}

@Composable
private fun ColumnScope.SongTab(ui: com.luogen.music.ui.search.SearchViewModel.UiState, viewModel: SearchViewModel, context: android.content.Context, toOpenPlayer: () -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
        items(ui.songs, key = { it.keyOf() }) { song ->
            SongRow(song = song, index = 0, onPlay = {
                PlaybackService.playQueue(context, ui.songs, ui.songs.indexOf(song).coerceAtLeast(0))
                toOpenPlayer()
            })
        }
        item {
            LoadMoreFooter(
                listState = listState,
                hasMore = ui.hasMoreSongs,
                loading = ui.loading,
                onLoad = { viewModel.loadMoreSongs() },
            )
        }
    }
}

@Composable
private fun ColumnScope.ArtistTab(ui: com.luogen.music.ui.search.SearchViewModel.UiState, viewModel: SearchViewModel, toArtist: (Long) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp)) {
        items(ui.artists.distinctBy { it.id }, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { toArtist(a.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(Song(name = a.name, coverUrl = a.picUrl), 56.dp, style = "round")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            a.alias.firstOrNull()?.let { "别名 ${it}" },
                            "歌曲 ${a.musicSize}",
                            "专辑 ${a.albumSize}",
                        ).joinToString(" · "),
                        color = TextDim, fontSize = 11.sp,
                    )
                }
                Icon(Icons.Rounded.PlayArrow, null, tint = TextDim)
            }
        }
        item {
            LoadMoreFooter(
                listState = listState,
                hasMore = ui.hasMoreArtists,
                loading = ui.loading,
                onLoad = { viewModel.loadMoreArtists() },
            )
        }
    }
}

@Composable
private fun ColumnScope.AlbumTab(ui: com.luogen.music.ui.search.SearchViewModel.UiState, viewModel: SearchViewModel, toAlbum: (Long) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp)) {
        items(ui.albums.distinctBy { it.id }, key = { it.id }) { al ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { toAlbum(al.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(Song(name = al.name, coverUrl = al.picUrl), 56.dp, style = "square")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(al.name, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(al.artistNames.firstOrNull(), if (al.size > 0) "${al.size}首" else null).joinToString(" · "),
                        color = TextDim, fontSize = 11.sp,
                    )
                }
            }
        }
        item {
            LoadMoreFooter(
                listState = listState,
                hasMore = ui.hasMoreAlbums,
                loading = ui.loading,
                onLoad = { viewModel.loadMoreAlbums() },
            )
        }
    }
}

/**
 * 列表滚动到底部时自动加载下一页：距底部 3 项内且还有更多时触发一次。
 */
@Composable
private fun LoadMoreFooter(
    listState: LazyListState,
    hasMore: Boolean,
    loading: Boolean,
    onLoad: () -> Unit,
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, loading) {
        if (nearEnd && hasMore && !loading) onLoad()
    }
    if (!hasMore) {
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text("已展示全部结果", color = TextDim.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LgSecondary, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
        }
    }
}

/** 歌单行的稳定 key（本地/在线区分） */
private fun Song.keyOf(): String = if (localUri != null) "l:$localUri" else "o:$source:$id"

@Composable
private fun ColumnScope.VideoTab(videos: List<Video>, toPlayVideo: (Video) -> Unit) {
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无 MV 资源", color = TextDim, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
        items(videos, key = { it.keyOf() }) { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { toPlayVideo(v) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    CoverArt(Song(name = v.name, coverUrl = v.coverUrl), 84.dp, style = "square")
                    Icon(
                        Icons.Rounded.PlayArrow, null, tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(28.dp).background(Color.Black.copy(alpha = 0.35f)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (v.artistName.isNotBlank()) v.artistName else "MV", color = TextDim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(formatPlayCount(v.playCount), color = TextDim, fontSize = 11.sp)
            }
        }
        // 到底提示：列表可完整滑到底，底部留白足够不会出现“半截内容”卡住
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Text("已显示全部 ${videos.size} 条 MV 结果", color = TextDim.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

/** 视频行的稳定 key */
private fun Video.keyOf(): String = "v:${id}"

@Composable
private fun PlatformRow(h: HotWord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${h.rank}", color = if (h.rank <= 3) LgPink else TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(h.word, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.source, color = TextDim.copy(alpha = 0.6f), fontSize = 10.sp)
        }
        if (h.hotValue > 0) {
            Text(if (h.hotValue >= 10000) "${"%.1f".format(h.hotValue / 10000f)}万" else "$h.hotValue", color = TextDim, fontSize = 11.sp)
        }
    }
}

private fun formatPlayCount(v: Long): String = when {
    v >= 10000 -> "${"%.1f".format(v / 10000f)}万播放"
    else -> "${v}播放"
}