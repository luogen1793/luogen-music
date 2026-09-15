package com.luogen.music.ui.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import com.luogen.music.R
import com.luogen.music.domain.model.Song
import com.luogen.music.service.PlaybackService
import com.luogen.music.ui.components.AmbientBackground
import com.luogen.music.ui.components.CoverArt
import com.luogen.music.ui.components.GlassGradientButton
import com.luogen.music.ui.components.NowPlayingBar
import com.luogen.music.ui.components.PressScale
import com.luogen.music.ui.components.songListKey
import com.luogen.music.ui.theme.LgPrimary
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.launch

/** 本地音乐 / 本地视频 */
@Composable
fun LocalScreen(
    viewModel: LocalViewModel,
    onPlayVideo: (Song) -> Unit,
    onUploadCover: (Song) -> Unit,
    onOpenPlayer: () -> Unit = { },
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // 权限
    var granted by remember {
        mutableStateOf(hasMediaPermission(context))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPermission(context)
        if (granted) viewModel.scan()
    }

    LaunchedEffect(granted) { if (granted) viewModel.scan() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(extraGlow = LgSecondary)
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("本地音乐", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (ui.tab == 0) "${ui.library.music.size} 首本地歌曲 · 自动识别" else "${ui.library.videos.size} 个本地视频",
                        color = TextDim, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                PressScale(onClick = {
                    if (granted) viewModel.scan() else launcher.launch(mediaPerms())
                }) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, "刷新", tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!granted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = TextDim, modifier = Modifier.size(50.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("授权访问媒体文件后即可自动识别本地音乐与视频", color = TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        GlassGradientButton("立即授权", onClick = { launcher.launch(mediaPerms()) })
                    }
                }
                return@Box
            }

            TabRow(
                selectedTabIndex = ui.tab,
                containerColor = Color.Transparent,
                contentColor = LgSecondary,
            ) {
                listOf("音乐" to Icons.Rounded.MusicNote, "视频" to Icons.Rounded.VideoLibrary).forEachIndexed { i, (label, _) ->
                    Tab(selected = ui.tab == i, onClick = { viewModel.setTab(i) }, text = { Text(label, fontSize = 13.sp) })
                }
            }

            if (ui.scanning && ui.library.music.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LgPrimary, strokeWidth = 3.dp)
                }
                return@Box
            }

            if (ui.tab == 0) {
                val songs = sortLocal(ui.library.music)
                if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("设备上暂未发现本地音乐", color = TextDim, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                        item {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                GlassGradientButton("播放全部", onClick = { PlaybackService.playQueue(context, songs, 0) })
                            }
                        }
                        items(songs, key = { songListKey(it) }) { song ->
                            LocalSongRow(song, onUploadCover = { onUploadCover(song) }, onPlay = {
                                PlaybackService.playQueue(context, songs, songs.indexOf(song).coerceAtLeast(0))
                            })
                        }
                    }
                }
            } else {
                val videos = ui.library.videos
                if (videos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("设备上暂未发现本地视频", color = TextDim, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(videos, key = { it.id }) { v ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPlayVideo(v) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                                    CoverArt(v, 88.dp, style = "square")
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, color = TextMain, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(fmt(v.durationMs), color = TextDim, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        // 播放中的迷你条
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp)) {
            androidx.compose.material3.Surface(
                modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)),
                color = Color(0xE61A1A2E),
                shadowElevation = 12.dp,
            ) {
                NowPlayingBar(onOpenPlayer = onOpenPlayer)
            }
        }
    }
}

@Composable
private fun LocalSongRow(song: Song, onUploadCover: () -> Unit, onPlay: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }

    // 设为铃声：未授权“修改系统设置”时先跳系统授权页，返回后自动重试设置
    val pendingRingtone = remember { mutableStateOf<Song?>(null) }
    val ringtoneLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        pendingRingtone.value?.let { s ->
            scope.launch {
                com.luogen.music.util.MediaActions.setAsRingtone(context, s)
                    .onSuccess { toast = "铃声已设置：${s.name}" }
                    .onFailure { toast = "设置铃声失败：${it.message ?: "未知错误"}" }
            }
            pendingRingtone.value = null
        }
    }

    fun requestRingtone(s: Song) {
        if (android.provider.Settings.System.canWrite(context)) {
            scope.launch {
                com.luogen.music.util.MediaActions.setAsRingtone(context, s)
                    .onSuccess { toast = "铃声已设置：${s.name}" }
                    .onFailure { toast = if (it is SecurityException) "需要授权“修改系统设置”后才能设置铃声" else "设置铃声失败：${it.message}" }
            }
        } else {
            pendingRingtone.value = s
            runCatching {
                ringtoneLauncher.launch(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            }.onFailure {
                pendingRingtone.value = null
                toast = "无法打开系统授权页，请手动前往 设置→应用→发现音乐 授予“修改系统设置”"
            }
        }
    }

    toast?.let { t ->
        androidx.compose.runtime.LaunchedEffect(t) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                t, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(song, 46.dp, style = "round")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistNames.joinToString(" / ").ifBlank { "未知歌手" }, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(fmt(song.durationMs), color = TextDim, fontSize = 11.sp)
        Box {
            Icon(Icons.Rounded.MoreVert, null, tint = TextDim, modifier = Modifier.padding(4.dp).size(20.dp).clickable { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("上传自定义封面") }, onClick = { menu = false; onUploadCover() })
                DropdownMenuItem(text = { Text("设为铃声") }, onClick = {
                    menu = false
                    requestRingtone(song)
                })
            }
        }
    }
}

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val perms = mediaPerms()
    return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

private fun mediaPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}