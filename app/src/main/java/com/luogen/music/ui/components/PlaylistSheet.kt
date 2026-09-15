package com.luogen.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.data.db.MyPlaylistEntity
import com.luogen.music.data.local.PlaylistStore
import com.luogen.music.domain.model.Song
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.MainGradient
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import kotlinx.coroutines.launch

/**
 * 「添加到歌单」选择弹窗：列出全部歌单（含歌曲数），点击即加入；
 * 没有歌单或想新建时，可直接输入名称创建并自动加入当前歌曲。
 */
@Composable
fun PlaylistSheet(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<MyPlaylistEntity>>(emptyList()) }
    var countOf by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val ps = runCatching { PlaylistStore.all() }.getOrDefault(emptyList())
        playlists = ps
        countOf = runCatching { ps.associate { it.id to PlaylistStore.songs(it).size } }.getOrDefault(emptyMap())
        loaded = true
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xF2242036),
        title = {
            Text(if (creating) "新建歌单" else "添加到歌单", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            when {
                creating -> {
                    Column {
                        RoundInputField(value = newName, onValueChange = { newName = it }, placeholder = "歌单名称", maxLength = 30)
                        Spacer(Modifier.padding(6.dp))
                        Text("创建后「${song.name}」将自动加入新歌单", color = TextDim, fontSize = 11.sp)
                    }
                }
                !loaded -> Text("加载歌单中…", color = TextDim, fontSize = 13.sp)
                playlists.isEmpty() -> Column {
                    Text("还没有歌单，先创建一个吧", color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.padding(6.dp))
                    Text("点击下方「新建歌单」输入名称，创建后自动加入本歌曲", color = TextDim, fontSize = 11.sp, lineHeight = 16.sp)
                }
                else -> Column {
                    playlists.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        val ok = PlaylistStore.addSong(p, song)
                                        Toast.makeText(context, if (ok) "已添加到「${p.name}」" else "这首歌已在「${p.name}」中", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MainGradient), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${countOf[p.id] ?: 0} 首歌曲", color = TextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = runCatching { PlaylistStore.create(newName) }.getOrDefault(0L)
                            if (id > 0L) {
                                val created = runCatching { PlaylistStore.all() }.getOrDefault(emptyList()).firstOrNull { it.id == id }
                                if (created != null) {
                                    PlaylistStore.addSong(created, song)
                                    Toast.makeText(context, "已创建歌单并加入「${song.name}」", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "创建失败，请重试", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    },
                ) { Text("创建并添加", color = if (newName.isNotBlank()) LgSecondary else TextDim, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = { creating = true }) { Text("新建歌单", color = LgSecondary, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (creating) creating = false else onDismiss()
            }) { Text(if (creating) "返回" else "取消", color = TextDim) }
        },
    )
}