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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.LuogenApp
import com.luogen.music.data.local.FavoriteStore
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.GlassIconButton
import com.luogen.music.ui.components.RoundInputField
import com.luogen.music.ui.components.SongRow
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPink
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 「我的」页模块详情页：kind=0 我的喜欢 / kind=1 最近听过。
 * 模块入口卡片点击进入，支持搜索过滤、点击播放、取消喜欢 / 清空记录。
 */
@Composable
fun MyListScreen(
    kind: Int,
    title: String,
    onBack: () -> Unit,
    toPlay: (List<Song>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LuogenApp.instance
    var query by remember { mutableStateOf("") }

    // kind=0：我的喜欢（全局收藏状态实时同步）；kind=1：最近听过（Room 持久化）
    val favSongs by FavoriteStore.songs.collectAsState()
    var historySongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (kind == 1) {
            runCatching {
                val dao = app.database.dao()
                val json = Json { ignoreUnknownKeys = true }
                historySongs = dao.history().mapNotNull { e ->
                    runCatching { json.decodeFromString(Song.serializer(), e.songJson) }.getOrNull()
                }
            }
        }
    }

    val all = if (kind == 0) favSongs else historySongs
    val filtered = remember(all, query) {
        val q = query.trim()
        (if (q.isBlank()) all else all.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.artistNames.any { a -> a.contains(q, ignoreCase = true) } ||
                it.albumName.contains(q, ignoreCase = true)
        }).distinctBy { songListKey(it) }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = if (kind == 0) LgPink else LgSecondary)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (kind == 1 && historySongs.isNotEmpty()) {
                        Text(
                            "清空记录", color = TextDim, fontSize = 12.sp,
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .clickable {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { app.database.dao().clearHistory() }
                                        historySongs = emptyList()
                                        Toast.makeText(context, "已清空最近听过", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item {
                RoundInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (kind == 0) "搜索喜欢的歌曲" else "搜索听过的歌曲",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        when {
                            kind == 0 && favSongs.isEmpty() -> "还没有喜欢的歌曲，播放页点击「♥」即可收藏"
                            kind == 1 && historySongs.isEmpty() -> "还没有听过的歌曲，快去听一首吧"
                            else -> "没有匹配的歌曲"
                        },
                        color = TextDim, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            } else {
                items(filtered, key = { (if (kind == 0) "mvfav:" else "mvhis:") + songListKey(it) }) { s ->
                    SongRow(
                        song = s, index = 0,
                        onPlay = {
                            runCatching {
                                val idx = filtered.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                                toPlay(filtered, idx)
                            }
                        },
                        menuExtra = if (kind == 0) {
                            {
                                DropdownMenuItem(
                                    text = { Text("取消喜欢") },
                                    leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) },
                                    onClick = { FavoriteStore.toggle(s) },
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }
}