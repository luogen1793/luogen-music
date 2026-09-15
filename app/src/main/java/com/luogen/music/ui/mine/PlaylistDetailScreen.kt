package com.luogen.music.ui.mine

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.data.db.MyPlaylistEntity
import com.luogen.music.data.local.PlaylistStore
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.RoundInputField
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.launch

/**
 * 歌单详情页：歌单头像/名称/歌曲数 + 搜索过滤 + 播放 + 移出歌曲 + 删除歌单。
 * 从「我的」页歌单模块卡片点击进入。
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    toPlay: (List<Song>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<MyPlaylistEntity?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val p = runCatching { PlaylistStore.all().firstOrNull { it.id == playlistId } }.getOrNull()
            playlist = p
            songs = if (p != null) runCatching { PlaylistStore.songs(p) }.getOrDefault(emptyList()) else emptyList()
        }
    }
    LaunchedEffect(playlistId) { reload() }

    val filtered = remember(songs, query) {
        val q = query.trim()
        (if (q.isBlank()) songs else songs.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.artistNames.any { a -> a.contains(q, ignoreCase = true) } ||
                it.albumName.contains(q, ignoreCase = true)
        }).distinctBy { songListKey(it) }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text(playlist?.name ?: "歌单", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (playlist != null) {
                        // 删除歌单（确认后删除并返回「我的」页）
                        Text(
                            "删除歌单", color = TextDim, fontSize = 12.sp,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .clickable { deleteConfirm = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)).background(MainGradient), contentAlignment = Alignment.Center) {
                        if (!playlist.isNullOrBlankCover() && java.io.File(playlist!!.coverPath).exists()) {
                            androidx.compose.foundation.Image(
                                bitmap = (com.luogen.music.util.BitmapLoader.decodeSampled(playlist!!.coverPath, 256) ?: return@Box).asImageBitmap(),
                                contentDescription = playlist?.name,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(playlist?.name ?: "歌单", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("${songs.size} 首歌曲", color = TextDim, fontSize = 12.sp)
                }
            }
            item {
                RoundInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索歌单内歌曲",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (songs.isEmpty()) "歌单还是空的：在歌曲列表点「三点 → 添加到歌单」即可加入" else "没有匹配的歌曲",
                        color = TextDim, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            } else {
                items(filtered, key = { "pld:${songListKey(it)}" }) { s ->
                    SongRow(
                        song = s, index = 0,
                        onPlay = {
                            runCatching {
                                val idx = filtered.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                                toPlay(filtered, idx)
                            }
                        },
                        menuExtra = {
                            DropdownMenuItem(
                                text = { Text("移出歌单") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                onClick = {
                                    scope.launch { playlist?.let { PlaylistStore.removeSong(it, s) } }
                                    reload()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
    // 删除歌单确认
    if (deleteConfirm && playlist != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xF2242036),
            title = { Text("删除歌单", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("确定删除歌单「${playlist?.name}」吗？歌单内 ${songs.size} 首歌曲将被移出（不影响原歌曲）。", color = TextDim, fontSize = 13.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val p = playlist
                    scope.launch {
                        if (p != null) runCatching { PlaylistStore.delete(p) }
                        Toast.makeText(context, "歌单已删除", Toast.LENGTH_SHORT).show()
                    }
                    deleteConfirm = false
                    onBack()
                }) { Text("删除", color = com.luogen.music.ui.theme.LgPink, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteConfirm = false }) { Text("取消", color = TextDim) }
            },
        )
    }
}

/** 歌单封面为空的判断（避免空串走文件加载） */
private fun MyPlaylistEntity?.isNullOrBlankCover(): Boolean = this == null || coverPath.isBlank()