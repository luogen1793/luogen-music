package com.luogen.music.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.domain.model.HotWord
import com.luogen.music.domain.model.RaType
import com.luogen.music.domain.model.RankList
import com.luogen.music.domain.model.Song
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassChip
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.NowPlayingBar
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.SectionTitle
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import androidx.compose.foundation.lazy.rememberLazyListState

/** 发现页 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    toSearch: (keyword: String, videoOnly: Boolean) -> Unit,
    toScan: () -> Unit,
    toOpenPlayer: () -> Unit,
    toArtist: (Long) -> Unit,
    toAlbum: (Long) -> Unit,
    toRankList: (RankList) -> Unit,
    toRadio: (RaType) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgPrimary)
        Column(Modifier.fillMaxSize()) {
            HomeTopBar({ toSearch("", false) }, toScan)

            if (ui.loading && ui.toplists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LgPrimary, strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 76.dp),
                ) {
                    // 个人化推荐横幅 + AI 电台
                    item {
                        RecommendBanner(ui.recommend, ui.newsongs, { toRadio(RaType.HOT) }, onPlay = { songs, i ->
                            PlaybackService.playQueue(context, songs, i)
                            toOpenPlayer()
                        })
                    }

                    // 热门搜索词
                    if (ui.hotWords.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.padding(horizontal = 16.dp).padding(top = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("热搜", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("实时更新", color = TextDim, fontSize = 11.sp)
                            }
                            LazyRow(
                                Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(ui.hotWords.take(20), key = { it.word }) { h ->
                                    GlassChip(text = h.word, onClick = { toSearch(h.word, false) })
                                }
                            }
                        }
                    }

                    // 官方榜单
                    item {
                        Column(Modifier.padding(top = 22.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle("热歌榜单", modifier = Modifier.weight(1f))
                                Text("实时更新 ·", color = TextDim, fontSize = 11.sp)
                            }
                            LazyRow(
                                Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(ui.toplists, key = { it.id }) { tl ->
                                    RankCard(tl, ui.topTracks[tl.id].orEmpty(), onClick = { toRankList(tl) })
                                }
                            }
                        }
                    }

                    // AI 电台
                    item {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionTitle("红星 AI 电台", subtitle = "三个 AI 为你推荐", modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AiRadioCard("热辣冲榜官", "热门金曲", LgPink, Modifier.weight(1f), onClick = { toRadio(RaType.HOT) })
                                AiRadioCard("知心点歌官", "懂你喜好", LgSecondary, Modifier.weight(1f), onClick = { toRadio(RaType.SOUL) })
                                AiRadioCard("宝藏挖歌官", "小众宝藏", LgPrimary, Modifier.weight(1f), onClick = { toRadio(RaType.TREASURE) })
                            }
                        }
                    }

                    // 新歌首发
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        SectionTitle("新歌首发", subtitle = "全站实时上新", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(ui.newsongs.take(6), key = { songListKey(it) }) { song ->
                        SongRow(
                            song = song, index = 0, showCover = true,
                            onPlay = {
                                PlaybackService.playQueue(context, ui.newsongs, ui.newsongs.indexOf(song).coerceAtLeast(0))
                                toOpenPlayer()
                            },
                        )
                    }

                    // 平台热搜榜（头条/B站 真实实时）
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        SectionTitle("全网热点榜", subtitle = "头条 · B站 实时", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(ui.platformHot.take(15), key = { it.word }) { h ->
                        PlatformHotRow(h, onClick = { toSearch(h.word, true) })
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
        // 迷你播放条悬浮底部
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp)) {
            androidx.compose.material3.Surface(
                modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)),
                color = Color(0xE61A1A2E),
                shadowElevation = 12.dp,
            ) {
                NowPlayingBar(onOpenPlayer = toOpenPlayer)
            }
        }
    }
}

@Composable
private fun HomeTopBar(toSearch: () -> Unit, toScan: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("发现音乐", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("作者：罗根 · 极致质感", color = TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        PressScale(onClick = toScan) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .then(Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, "听歌识曲", tint = TextMain, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        PressScale(onClick = toSearch) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = TextDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("搜索歌曲、歌手、专辑", color = TextDim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RecommendBanner(
    recommend: List<Song>,
    newsongs: List<Song>,
    toRadio: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    val songs = (recommend.ifEmpty { newsongs })
    val playable = songs.toList()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF40215E), Color(0xFF1E2E4A))))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Radio, null, tint = LgSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("为你推荐", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                PressScale(onClick = toRadio) {
                    Text("红星AI电台 ›", color = LgSecondary, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("根据你的听歌历史 · 真实算法推荐", color = TextDim, fontSize = 11.sp)
            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(songs.take(8)) { idx, song ->
                        Column(Modifier.width(92.dp)) {
                            Box {
                                CoverArt(song, 92.dp, style = "glass", modifier = Modifier.clickable { onPlay(playable, idx) })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(song.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artistNames.firstOrNull() ?: "未知歌手", color = TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankCard(tl: RankList, tracks: List<Song>, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(Song(name = tl.name, coverUrl = tl.coverUrl), 40.dp, style = "square")
            Spacer(Modifier.width(8.dp))
            Column {
                Text(tl.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tl.updateFrequency.ifBlank { "实时更新" }, color = TextDim, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        tracks.take(3).forEachIndexed { i, s ->
            Text(
                "${i + 1}  ${s.name} · ${s.artistNames.firstOrNull() ?: ""}",
                color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun AiRadioCard(name: String, desc: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressScale(modifier = modifier, onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(12.dp),
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlatformHotRow(h: HotWord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${h.rank}",
            color = if (h.rank <= 3) LgPink else TextDim,
            fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(h.word, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.source, color = TextFaint, fontSize = 10.sp)
        }
        if (h.hotValue > 0) {
            Text(formatHot(h.hotValue), color = TextDim, fontSize = 11.sp)
        }
    }
}

private val TextFaint = TextDim.copy(alpha = 0.6f)

private fun formatHot(v: Long): String = when {
    v >= 10000 -> "${"%.1f".format(v / 10000f)}万"
    else -> "$v"
}